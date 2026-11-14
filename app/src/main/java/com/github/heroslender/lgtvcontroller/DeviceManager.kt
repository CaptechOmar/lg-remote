package com.github.heroslender.lgtvcontroller

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.github.heroslender.lgtvcontroller.utils.AppLog
import com.connectsdk.device.ConnectableDevice
import com.connectsdk.discovery.DiscoveryManager
import com.connectsdk.discovery.DiscoveryManagerListener
import com.connectsdk.discovery.provider.SSDPDiscoveryProvider
import com.connectsdk.service.DLNAService
import com.connectsdk.service.WebOSTVService
import com.connectsdk.service.command.ServiceCommandError
import com.connectsdk.service.config.ServiceDescription
import com.github.heroslender.lgtvcontroller.device.Device
import com.github.heroslender.lgtvcontroller.device.DeviceListener
import com.github.heroslender.lgtvcontroller.device.DeviceStatus
import com.github.heroslender.lgtvcontroller.device.NetworkDevice
import com.github.heroslender.lgtvcontroller.device.impl.LgDevice
import com.github.heroslender.lgtvcontroller.device.impl.LgNetworkDevice
import com.github.heroslender.lgtvcontroller.domain.model.Tv
import com.github.heroslender.lgtvcontroller.storage.TvRepository
import com.github.heroslender.lgtvcontroller.ui.snackbar.Snackbar
import com.github.heroslender.lgtvcontroller.utils.TvProbe
import com.github.heroslender.lgtvcontroller.utils.hasMouseSocket
import com.github.heroslender.lgtvcontroller.utils.WakeOnLan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

class DeviceManager(
    ctx: Context,
    private val scope: CoroutineScope,
    private val tvRepository: TvRepository,
) : DiscoveryManagerListener {
    private var _connectedDevice: MutableStateFlow<LgDevice?> = MutableStateFlow(null)
    val connectedDevice: StateFlow<Device?>
        get() = _connectedDevice

    private var _devices: MutableStateFlow<List<NetworkDevice>> = MutableStateFlow(emptyList())
    val devices: StateFlow<List<NetworkDevice>>
        get() = _devices

    private val _errors = MutableSharedFlow<Snackbar>()
    val errors: Flow<Snackbar> = _errors

    @Volatile
    var appStatus = AppStatus.AWAITING_FIRST_CONNECTION
        private set

    /**
     * The id of the tv that was connected when the app was paused.
     * This is because when the app is in the background, it looses connection to the internet,
     * and therefore, disconnects from the tv. This is used to reconnect back to the tv when the
     * app returns to the foreground.
     */
    private var pausedTvId: String? = null

    /**
     * The id of a tv that was just sent a Wake-on-LAN packet. Once it shows up on the network,
     * the app connects to it automatically.
     */
    private var pendingWakeTvId: String? = null

    /**
     * The id of the tv the user wants to stay connected to. While this is set, unexpected
     * disconnects are recovered from automatically. It's cleared when the user powers the tv off,
     * picks another tv, or when reconnecting gave up.
     */
    @Volatile
    private var desiredTvId: String? = null

    private var reconnectJob: Job? = null

    @Volatile
    private var pausedAtMs = 0L

    @Volatile
    private var lastForcedDiscoveryMs = 0L

    @Volatile
    private var lastGiveUpMs = 0L

    /** The tv the user switched off from the app, which must not be reconnected to on its own. */
    @Volatile
    private var userStoppedTvId: String? = null

    /**
     * Every tv seen this session, even after discovery stopped reporting it. The discovery list
     * drops a tv roughly a minute after it stops answering, but its last known address is still
     * the best place to try reconnecting to.
     */
    private val knownNetworkDevices = ConcurrentHashMap<String, LgNetworkDevice>()

    /**
     * Every tv the app has connected to before, including those that are currently off and
     * therefore not discovered on the network.
     */
    val savedTvs: Flow<List<Tv>> = tvRepository.getAllTvsStream()

    /** The tv the user connected to last, so the app can open straight to it. */
    private val prefs = ctx.applicationContext.getSharedPreferences("lg_remote_state", Context.MODE_PRIVATE)
    private val lastTvIdState = MutableStateFlow(prefs.getString(LAST_TV_KEY, null))

    @Volatile
    private var cachedSavedTvs: List<Tv> = emptyList()

    /** The tv to show and connect to when the app opens. LastTv.loaded is false until the database answered. */
    val lastTv: Flow<LastTv> = combine(savedTvs, lastTvIdState) { tvs, id ->
        LastTv(loaded = true, tv = tvs.firstOrNull { it.id == id } ?: tvs.firstOrNull())
    }

    private fun lastTvId(): String? {
        val saved = cachedSavedTvs
        val id = lastTvIdState.value
        return if (saved.isEmpty()) id else saved.firstOrNull { it.id == id }?.id ?: saved.first().id
    }

    /** Tvs that were added to the device list by us (from their saved address), not by discovery. */
    private val injectedTvIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Consecutive failed reachability probes per tv, see [presenceLoop]. */
    private val probeFailures = ConcurrentHashMap<String, Int>()

    /**
     * Tvs added from their saved address whose real discovery data hasn't arrived yet. The logs
     * show connecting before that fails every time, and works right after the sdk reports the
     * device as updated, so connecting waits for it (see [awaitRealDescription]).
     */
    private val awaitingRealDescription: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        try {
            AppLog.d("Device_Manager", "Starting Device Manager...")
            DiscoveryManager.init(ctx)
            DiscoveryManager.getInstance().pairingLevel = DiscoveryManager.PairingLevel.ON
            DiscoveryManager.getInstance().registerDeviceService(
                WebOSTVService::class.java,
                SSDPDiscoveryProvider::class.java
            )
            DiscoveryManager.getInstance().registerDeviceService(
                DLNAService::class.java,
                SSDPDiscoveryProvider::class.java
            )
            DiscoveryManager.getInstance().addListener(this)
            DiscoveryManager.getInstance().start()

            registerNetworkCallback(ctx)
            scope.launch { savedTvs.collect { cachedSavedTvs = it } }
            scope.launch { heartbeatLoop() }
            scope.launch { presenceLoop() }

            scope.launch {
                try {
                    while (appStatus == AppStatus.AWAITING_FIRST_CONNECTION) {
                        for (discoveryProvider in DiscoveryManager.getInstance().discoveryProviders) {
                            AppLog.d(
                                "Device_Manager",
                                "rescan :::: " + discoveryProvider::class.simpleName
                            )
                            discoveryProvider.rescan()
                        }

                        // discoveryProvider.rescan sends 3 scans with 1000ms interval, so
                        // we set the delay here for 4000ms
                        delay(4000)
                    }
                } catch (e: Exception) {
                    AppLog.e("Device_Manager", "rescan :::: " + e.message)
                    _errors.emit(Snackbar.error("Failed to scan for devices", e.message))
                }
            }
        } catch (e: Exception) {
            AppLog.e("Device_Manager", "Failed to start device manager: ${e.message}")
            e.printStackTrace()
            scope.launch {
                _errors.emit(Snackbar.error("Failed to start device manager", e.message))
            }
        }
    }

    private fun getLgDevice(id: String): LgNetworkDevice? {
        return (devices.value.firstOrNull { it.id == id } as? LgNetworkDevice)
            ?: knownNetworkDevices[id]
    }

    private fun getDevice(id: String): NetworkDevice? = getLgDevice(id)

    private fun isConnectedTo(id: String): Boolean {
        val device = _connectedDevice.value ?: return false
        return device.id == id && device.state.value.status == DeviceStatus.CONNECTED
    }

    fun onDeviceConnected(device: Device) {
        AppLog.d("Device_Manager", "connected to ${device.id}")
        getDevice(device.id)?.updateStatus(DeviceStatus.CONNECTED)

        // Separate from loading the apps, so a failure there can't leave the arrows and OK
        // without their socket (and the other way around)
        scope.launch {
            delay(MOUSE_SETTLE_MS)
            ensureMouse(device as LgDevice)
        }

        scope.launch {
            try {
                (device as LgDevice).loadAppsAndInputs()
            } catch (e: Exception) {
                AppLog.e(
                    "DeviceManager",
                    "Failed to load apps and inputs for device ${device.id}: ${e.message}"
                )
            }
        }
    }

    /**
     * (Re)opens the socket the arrows and OK are sent through. The sdk keeps a mouse socket that
     * died with the old connection and never replaces it ("already connected"), which leaves the
     * volume and home buttons working but the arrows and OK dead. Closing it first forces a new one.
     */
    private suspend fun ensureMouse(device: LgDevice) {
        try {
            val mouse = device.device.device.mouseControl
            withContext(Dispatchers.IO) { runCatching { mouse.disconnectMouse() } }

            // Asking too soon after connecting can fail without any error, so check that the
            // socket really exists and ask again if it doesn't.
            for (attempt in 1..MOUSE_ATTEMPTS) {
                if (_connectedDevice.value !== device
                    || device.state.value.status != DeviceStatus.CONNECTED
                ) {
                    return
                }

                runCatching { mouse.connectMouse() }
                delay(MOUSE_CHECK_DELAY_MS)

                if (device.service.hasMouseSocket()) {
                    AppLog.d("Device_Manager", "Pointer socket ready (attempt $attempt)")
                    return
                }
                AppLog.w("Device_Manager", "Pointer socket not ready (attempt $attempt)")
            }
        } catch (e: Exception) {
            AppLog.e("Device_Manager", "Failed to open the pointer socket: ${e.message}")
        }
    }

    fun onDeviceDisconnected(device: Device) {
        AppLog.d("Device_Manager", "disconnected from ${device.id} (app=$appStatus, desired=$desiredTvId)")
        if (appStatus == AppStatus.PAUSED) {
            this.pausedTvId = device.id
        }

        getDevice(device.id)?.updateStatus(DeviceStatus.DISCONNECTED)

        // Decisions are made on the main thread, since the sdk reports from its own threads
        scope.launch {
            val current = _connectedDevice.value
            if (current == null || current !== device) {
                // Late event from an older connection of this (or another) tv, ignore it
                return@launch
            }

            if (desiredTvId == device.id && appStatus != AppStatus.PAUSED) {
                // Not requested by the user, so try to get the connection back
                startReconnect(device.id)
                return@launch
            }

            _connectedDevice.value = null
        }
    }

    /**
     * Called when the user deliberately ends the connection (powering the tv off), so the app
     * doesn't fight it by reconnecting.
     */
    fun onUserDisconnect(tvId: String) {
        userStoppedTvId = tvId
        if (desiredTvId == tvId) {
            desiredTvId = null
            reconnectJob?.cancel()
        }
    }

    fun onDevicePairing(device: Device) {
        val networkDevice = getDevice(device.id) ?: return
        networkDevice.updateStatus(DeviceStatus.PAIRING)
    }

    fun ConnectableDevice.isCompatible(): Boolean {
        return getServiceByName(WebOSTVService.ID) != null
    }

    fun LgNetworkDevice.isCompatible() = device.isCompatible()

    override fun onDeviceAdded(manager: DiscoveryManager, device: ConnectableDevice) {
        AppLog.d(
            "Device_Manager",
            "onDeviceAdded :::: id: ${device.id} :: ${device.friendlyName}; ${device.modelNumber}"
        )

        if (device.isCompatible()) {
            deviceFound(device)
        }
    }

    override fun onDeviceUpdated(manager: DiscoveryManager, device: ConnectableDevice) {
        AppLog.d("Device_Manager", "onDeviceUpdated :::: ${device.friendlyName}")

        awaitingRealDescription.remove(device.id)
        if (device.isCompatible() && devices.value.indexOfFirst { it.id == device.id } == -1) {
            deviceFound(device)
        }
    }

    override fun onDeviceRemoved(manager: DiscoveryManager, device: ConnectableDevice) {
        AppLog.d("Device_Manager", "onDeviceRemoved :::: ${device.friendlyName}; ${device.id}")
        // The sdk dropped it (it hadn't answered discovery for a while), so this device object is
        // dead: connecting to it just hangs. A fresh one is created from the saved address.
        knownNetworkDevices.remove(device.id)
        _devices.tryEmit(devices.value.toMutableList().apply { removeIf { it.id == device.id } })
    }

    override fun onDiscoveryFailed(manager: DiscoveryManager, error: ServiceCommandError) {
        AppLog.d("Device_Manager", "onDiscoveryFailed :::: ${error.message}")
    }

    fun deviceFound(device: ConnectableDevice) {
        scope.launch {
            try {
                val saved = try {
                    tvRepository
                        .getTvStream(device.id)
                        .first()
                } catch (_: IllegalStateException) {
                    AppLog.d("DeviceManager", "Device not found in database")
                    null
                }

                val address = device.ipAddress?.takeIf { it.isNotBlank() }
                val tv = saved ?: Tv(
                    id = device.id,
                    name = device.friendlyName,
                    ipAddress = address,
                    serviceUuid = device.id,
                )

                if (saved != null && address != null
                    && (saved.ipAddress != address || saved.serviceUuid != device.id)
                ) {
                    // Remember where the tv lives so it can be reached without discovery later
                    saved.ipAddress = address
                    saved.serviceUuid = device.id
                    tvRepository.updateTv(saved)
                }

                val networkDevice = LgNetworkDevice(
                    device = device,
                    tv = tv,
                    manager = this@DeviceManager,
                )

                _devices.value = devices.value.filterNot { it.id == device.id } + networkDevice
                knownNetworkDevices[device.id] = networkDevice
                probeFailures.remove(device.id)

                AppLog.d("Device_Manager", "Device found: ${device.friendlyName}")

                if (pendingWakeTvId == device.id) {
                    // The tv we just woke up is now on the network, connect to it. The tv's
                    // services need a moment to be ready after it announces itself.
                    pendingWakeTvId = null
                    delay(WAKE_CONNECT_DELAY_MS)
                    connect(networkDevice)
                    return@launch
                }

                autoConnect(networkDevice)
            } catch (e: Exception) {
                AppLog.e("Device_Manager", "Failed to handle device found: ${e.message}")
                e.printStackTrace()
                scope.launch {
                    _errors.emit(Snackbar.error("Failed to handle device found", e.message))
                }
            }
        }
    }

    private suspend fun savedTv(id: String): Tv? = try {
        tvRepository.getTvStream(id).first()
    } catch (_: Exception) {
        null
    }

    /**
     * Adds a saved tv to the sdk's device list using its stored address and UUID, as if discovery
     * had just found it. This is what makes the tv usable immediately, without waiting for it to
     * answer an SSDP search (which can take long, or never happen on some networks).
     *
     * @return the device once it shows up (it's reported asynchronously), or null if the tv has
     * no stored address or the sdk didn't accept it.
     */
    private suspend fun injectSavedTv(tv: Tv): LgNetworkDevice? {
        getLgDevice(tv.id)?.let { known ->
            if (devices.value.none { it.id == tv.id }) {
                // Seen before, but dropped from the list while it was off
                deviceFound(known.device)
            }
            return known
        }

        val ip = tv.ipAddress?.takeIf { it.isNotBlank() } ?: return null
        val uuid = tv.serviceUuid?.takeIf { it.isNotBlank() } ?: return null

        try {
            val provider = DiscoveryManager.getInstance().discoveryProviders.firstOrNull() ?: return null
            val description = ServiceDescription(WEBOS_SERVICE_FILTER, uuid, ip).apply {
                serviceID = WebOSTVService.ID
                port = WEBOS_PORT
                friendlyName = tv.name
                lastDetection = System.currentTimeMillis()
            }

            injectedTvIds.add(tv.id)
            awaitingRealDescription.add(tv.id)
            AppLog.d("Device_Manager", "Injecting saved tv ${tv.name} at $ip")
            DiscoveryManager.getInstance().onServiceAdded(provider, description)
            // Ask the tv for its real description right away
            rescanDiscovery()
        } catch (e: Exception) {
            AppLog.e("Device_Manager", "Failed to inject saved tv: ${e.message}")
            return null
        }

        // The sdk reports the new device through onDeviceAdded, which is asynchronous
        repeat(25) {
            getLgDevice(tv.id)?.let { return it }
            delay(200)
        }
        return null
    }

    /**
     * Waits (briefly) until the sdk has the tv's real discovery data, if the tv was added from its
     * saved address. Connecting earlier just fails and has to be retried.
     */
    private suspend fun awaitRealDescription(id: String) {
        if (!awaitingRealDescription.contains(id)) return

        var waited = 0L
        var forced = false
        while (awaitingRealDescription.contains(id) && waited < REAL_DESCRIPTION_WAIT_MS) {
            delay(100)
            waited += 100

            if (!forced && waited >= FORCE_DISCOVERY_AFTER_MS) {
                // The sdk's SSDP provider only reports a tv the first time it sees it, so if it
                // already knew this one before we added it from its saved address, the real data
                // never comes. Make it forget what it has seen and search from scratch.
                forced = true
                forceFreshDiscovery()
            }
        }

        if (awaitingRealDescription.contains(id)) {
            AppLog.w("Device_Manager", "No discovery data for $id after ${waited}ms, connecting anyway")
        }
    }

    private suspend fun forceFreshDiscovery() {
        val now = System.currentTimeMillis()
        if (now - lastForcedDiscoveryMs < FORCE_DISCOVERY_MIN_GAP_MS) return
        lastForcedDiscoveryMs = now

        AppLog.d("Device_Manager", "Restarting discovery to get the tv's real data")
        withContext(Dispatchers.IO) {
            try {
                for (provider in DiscoveryManager.getInstance().discoveryProviders) {
                    if (provider is SSDPDiscoveryProvider) {
                        provider.reset()
                        provider.start()
                    } else {
                        provider.rescan()
                    }
                }
            } catch (e: Exception) {
                AppLog.e("Device_Manager", "Failed to restart discovery: ${e.message}")
            }
        }
    }

    /**
     * Regularly checks, with a plain TCP connection to the saved address, which saved tvs are
     * on. Unlike SSDP this is a direct question to a known address, so it answers within a
     * second whether the tv is on or not. A tv that turns on is added to the list right away
     * (and auto connected if configured), one that stays unreachable is removed from it.
     */
    private suspend fun presenceLoop() {
        while (true) {
            if (appStatus == AppStatus.PAUSED) {
                delay(PRESENCE_INTERVAL_MS)
                continue
            }

            try {
                for (tv in tvRepository.getAllTvsStream().first()) {
                    val ip = tv.ipAddress?.takeIf { it.isNotBlank() } ?: continue
                    if (isConnectedTo(tv.id)) {
                        probeFailures.remove(tv.id)
                        continue
                    }

                    if (TvProbe.isOnline(ip)) {
                        probeFailures.remove(tv.id)
                        if (devices.value.none { it.id == tv.id }) {
                            injectSavedTv(tv)
                        }

                        // The last used tv is reachable but there is no connection (it was off or
                        // restarting, or the reconnect gave up): connect again without being asked.
                        if (tv.id == lastTvId()
                            && desiredTvId == null
                            && userStoppedTvId != tv.id
                            && _connectedDevice.value == null
                            && reconnectJob?.isActive != true
                            && System.currentTimeMillis() - lastGiveUpMs > REGAIN_AFTER_GIVE_UP_MS
                        ) {
                            val device = getLgDevice(tv.id) ?: injectSavedTv(tv)
                            if (device != null) {
                                awaitRealDescription(tv.id)
                                AppLog.d("Device_Manager", "TV is reachable again, reconnecting")
                                connect(device)
                            }
                        }
                    } else if (injectedTvIds.contains(tv.id)) {
                        val failures = (probeFailures[tv.id] ?: 0) + 1
                        probeFailures[tv.id] = failures
                        val busy = _connectedDevice.value?.id == tv.id
                        if (failures >= PRESENCE_MAX_FAILURES && !busy) {
                            _devices.value = devices.value.filterNot { it.id == tv.id }
                        }
                    }
                }
            } catch (e: Exception) {
                AppLog.e("Device_Manager", "presence check failed: ${e.message}")
            }

            delay(PRESENCE_INTERVAL_MS)
        }
    }

    fun connect(cDevice: LgNetworkDevice) {
        if (cDevice.device.getServiceByName(WebOSTVService.ID) == null) {
            return
        }

        val currDevice = _connectedDevice.value
        if (currDevice != null && currDevice.id == cDevice.id) {
            when (currDevice.state.value.status) {
                // Already connected (or busy connecting) to this device
                DeviceStatus.CONNECTED,
                DeviceStatus.CONNECTING,
                DeviceStatus.PAIRING,
                    -> {
                    desiredTvId = cDevice.id
                    return
                }

                // A dead connection, fall through and start a new one
                DeviceStatus.DISCONNECTED -> {}
            }
        }

        AppLog.d("Device_Manager", "connect(${cDevice.friendlyName}) at ${cDevice.device.ipAddress}")
        userStoppedTvId = null
        reconnectJob?.cancel()
        desiredTvId = cDevice.id
        lastTvIdState.value = cDevice.id
        prefs.edit().putString(LAST_TV_KEY, cDevice.id).apply()
        knownNetworkDevices[cDevice.id] = cDevice
        startConnection(cDevice)

        scope.launch {
            tvRepository.insertTv(cDevice.tv)
        }
    }

    /**
     * Starts a new connection to [cDevice], replacing the current one. Whether it works is
     * reported through the [DeviceListener].
     */
    private fun startConnection(cDevice: LgNetworkDevice): LgDevice {
        val currDevice = _connectedDevice.value
        if (currDevice != null && currDevice.id != cDevice.id) {
            currDevice.disconnect()
        }

        val device = LgDevice(cDevice, this, DeviceStatus.CONNECTING)
        cDevice.device.addListener(DeviceListener(this, device))
        cDevice.device.connect()

        _connectedDevice.value = device
        if (appStatus != AppStatus.PAUSED) {
            appStatus = AppStatus.RUNNING
        }
        pausedTvId = null

        return device
    }

    /**
     * Keeps trying to connect to the tv with the given [id] until it works, the user picks
     * something else, the app goes to the background, or [RECONNECT_GIVE_UP_MS] has passed.
     * Does nothing if it's already trying.
     */
    private fun startReconnect(id: String) {
        if (reconnectJob?.isActive == true) {
            return
        }

        reconnectJob = scope.launch {
            val startedAt = System.currentTimeMillis()
            var attempt = 0

            while (desiredTvId == id && appStatus != AppStatus.PAUSED) {
                if (System.currentTimeMillis() - startedAt > RECONNECT_GIVE_UP_MS) {
                    giveUpReconnecting(id)
                    return@launch
                }

                // Show that we're working on it
                _connectedDevice.value?.takeIf { it.id == id }?.updateStatus(DeviceStatus.CONNECTING)

                val target = getLgDevice(id) ?: savedTv(id)?.let { injectSavedTv(it) }
                if (target == null) {
                    // Never seen it on this network yet, ask the network where it is
                    rescanDiscovery()
                } else if (!isReachable(target)) {
                    // Off or unreachable, don't sit through a connection timeout to find out
                    AppLog.d("Device_Manager", "Reconnect attempt ${attempt + 1}: tv doesn't answer")
                    if (attempt % 3 == 2) {
                        rescanDiscovery()
                    }
                } else {
                    awaitRealDescription(id)
                    AppLog.d("Device_Manager", "Reconnect attempt ${attempt + 1} to ${target.friendlyName}")
                    val device = startConnection(target)
                    if (awaitConnected(device)) {
                        AppLog.d("Device_Manager", "Reconnected to ${target.friendlyName}")
                        return@launch
                    }

                    // Tear the failed attempt down properly, the sdk ignores connect() while it
                    // still believes a service is connected.
                    withContext(Dispatchers.IO) {
                        runCatching { target.device.disconnect() }
                    }

                    // The tv might have a new address, so refresh discovery now and then
                    if (attempt % 3 == 2) {
                        rescanDiscovery()
                    }
                }

                delay(RECONNECT_DELAYS_MS[min(attempt, RECONNECT_DELAYS_MS.lastIndex)])
                attempt++
            }
        }
    }

    /** True if the tv accepts connections, or if its address isn't known so it can't be told. */
    private suspend fun isReachable(target: LgNetworkDevice): Boolean {
        val ip = target.tv.ipAddress?.takeIf { it.isNotBlank() }
            ?: target.device.ipAddress?.takeIf { it.isNotBlank() }
            ?: return true
        return TvProbe.isOnline(ip, timeoutMs = 800)
    }

    private suspend fun awaitConnected(device: LgDevice): Boolean {
        val startedAt = System.currentTimeMillis()
        while (true) {
            if (_connectedDevice.value !== device) {
                // Replaced by another connection in the meantime
                return false
            }

            val waited = System.currentTimeMillis() - startedAt
            when (device.state.value.status) {
                DeviceStatus.CONNECTED -> return true
                DeviceStatus.DISCONNECTED -> return false
                DeviceStatus.CONNECTING -> if (waited > CONNECT_TIMEOUT_MS) return false
                DeviceStatus.PAIRING -> if (waited > PAIRING_TIMEOUT_MS) return false
            }

            delay(250)
        }
    }

    private suspend fun giveUpReconnecting(id: String) {
        AppLog.w("Device_Manager", "Giving up reconnecting to $id")
        if (desiredTvId == id) {
            desiredTvId = null
        }

        getDevice(id)?.updateStatus(DeviceStatus.DISCONNECTED)
        _connectedDevice.value = null
        lastGiveUpMs = System.currentTimeMillis()
        // No popup: the remote shows the tv as disconnected, and the presence check below
        // reconnects by itself as soon as the tv is reachable again.
    }

    private fun rescanDiscovery() {
        try {
            for (discoveryProvider in DiscoveryManager.getInstance().discoveryProviders) {
                discoveryProvider.rescan()
            }
        } catch (e: Exception) {
            AppLog.e("Device_Manager", "rescan failed: ${e.message}")
        }
    }

    /**
     * The tv socket never notices a silently dead connection (phone woke from sleep, wifi
     * roaming, tv crashed...) until something is sent, so regularly ask the tv for something
     * and treat repeated silence as a dropped connection.
     */
    private suspend fun heartbeatLoop() {
        var misses = 0
        while (true) {
            delay(HEARTBEAT_INTERVAL_MS)

            val device = _connectedDevice.value
            if (device == null
                || appStatus == AppStatus.PAUSED
                || reconnectJob?.isActive == true
                || device.state.value.status != DeviceStatus.CONNECTED
            ) {
                misses = 0
                continue
            }

            if (device.isResponsive(HEARTBEAT_TIMEOUT_MS)) {
                misses = 0
                continue
            }

            misses++
            AppLog.w("Device_Manager", "TV didn't answer the heartbeat ($misses/$HEARTBEAT_MAX_MISSES)")
            if (misses >= HEARTBEAT_MAX_MISSES) {
                misses = 0
                dropDeadConnection(device)
            }
        }
    }

    /**
     * Closes a connection that stopped working. The resulting disconnect event is what starts
     * the reconnect loop.
     */
    private suspend fun dropDeadConnection(device: LgDevice) {
        AppLog.w("Device_Manager", "Dropping dead connection to ${device.friendlyName}")
        withContext(Dispatchers.IO) {
            runCatching { device.disconnect() }
        }
    }

    private fun registerNetworkCallback(ctx: Context) {
        try {
            val connectivityManager = ctx.getSystemService(ConnectivityManager::class.java) ?: return
            connectivityManager.registerDefaultNetworkCallback(object :
                ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch { onNetworkAvailable() }
                }
            })
        } catch (e: Exception) {
            AppLog.e("Device_Manager", "Failed to listen for network changes: ${e.message}")
        }
    }

    /**
     * The phone (re)joined a network, e.g. wifi came back or it switched access points.
     */
    private suspend fun onNetworkAvailable() {
        val id = desiredTvId ?: return
        if (appStatus == AppStatus.PAUSED) return

        val device = _connectedDevice.value
        if (device != null && device.id == id && device.state.value.status == DeviceStatus.CONNECTED) {
            // The old connection may not have survived the network change
            if (!device.isResponsive(RESUME_PROBE_TIMEOUT_MS)) {
                dropDeadConnection(device)
            }
        } else {
            startReconnect(id)
        }
    }

    private fun autoConnect(device: LgNetworkDevice) {
        if (!device.isCompatible() || !shouldAutoConnect(device)) {
            return
        }

        scope.launch {
            if (appStatus != AppStatus.RUNNING || device.tv.autoConnect) {
                awaitRealDescription(device.id)
                AppLog.d("Device_Manager", "Connecting to device favorite")
                connect(device)
            }
        }
    }

    fun shouldAutoConnect(device: LgNetworkDevice): Boolean {
        // The app only reconnects to the tv that was used last, never to whichever tv answers
        // first. Without a last tv (first use) the per-tv auto connect setting decides.
        val last = lastTvId()
        val isLast = if (last != null) device.id == last else device.tv.autoConnect
        return when (appStatus) {
            AppStatus.AWAITING_FIRST_CONNECTION -> isLast
            AppStatus.AWAITING_CONNECTION_AFTER_PAUSE ->
                if (pausedTvId == null) isLast else pausedTvId == device.id

            else -> false
        }
    }

    /**
     * Wakes up a tv that is off by sending it a Wake-on-LAN packet, then waits for it to show
     * up on the network and connects to it.
     */
    fun wakeTv(tv: Tv) {
        val mac = tv.macAddress
        if (mac.isNullOrBlank()) {
            scope.launch {
                _errors.emit(
                    Snackbar.error(
                        "Can't wake up the TV",
                        "Set the TV's MAC address in its settings first"
                    )
                )
            }
            return
        }

        userStoppedTvId = null
        scope.launch {
            try {
                WakeOnLan.send(mac)
            } catch (e: Exception) {
                AppLog.e("Device_Manager", "Failed to send Wake-on-LAN packet: ${e.message}")
                _errors.emit(Snackbar.error("Failed to wake up the TV", e.message))
                return@launch
            }

            _errors.emit(Snackbar.info("Waking up ${tv.displayName.takeUnless { it.isNullOrEmpty() } ?: tv.name}..."))

            if (!tv.ipAddress.isNullOrBlank()) {
                wakeAndConnectDirectly(tv)
                return@launch
            }

            // No stored address yet: the tv has to be found by discovery first
            pendingWakeTvId = tv.id

            if (getLgDevice(tv.id) != null) {
                // We know where the tv lives, no need to wait for discovery to find it again
                desiredTvId = tv.id
                startReconnect(tv.id)
            }

            repeat(WAKE_RESCAN_ATTEMPTS) {
                delay(WAKE_RESCAN_INTERVAL_MS)
                if (pendingWakeTvId != tv.id) {
                    // Found (and connected) in the meantime
                    return@launch
                }

                if (isConnectedTo(tv.id)) {
                    pendingWakeTvId = null
                    return@launch
                }

                rescanDiscovery()
            }

            if (pendingWakeTvId == tv.id) {
                pendingWakeTvId = null
                if (reconnectJob?.isActive == true || isConnectedTo(tv.id)) {
                    // Still trying (or made it just now), the reconnect loop reports its own result
                    return@launch
                }

                _errors.emit(
                    Snackbar.warning(
                        "The TV didn't show up on the network",
                        "Check that Wake-on-LAN is enabled on the TV and that the MAC address is correct"
                    )
                )
            }
        }
    }

    /**
     * After a Wake-on-LAN packet, polls the tv's saved address until it accepts connections and
     * connects right away, instead of waiting for discovery. Discovery is still nudged now and
     * then in case the tv came back with a different address.
     */
    private suspend fun wakeAndConnectDirectly(tv: Tv) {
        val ip = tv.ipAddress ?: return
        desiredTvId = tv.id

        for (tick in 1..WAKE_POLL_ATTEMPTS) {
            if (isConnectedTo(tv.id)) return

            if (TvProbe.isOnline(ip, timeoutMs = 800)) {
                // The tv's services need a moment after the port opens
                delay(WAKE_CONNECT_DELAY_MS)
                val device = injectSavedTv(tv)
                if (device != null) {
                    awaitRealDescription(tv.id)
                    connect(device)
                    return
                }
            }

            if (tick % 5 == 0) {
                rescanDiscovery()
            }
            delay(WAKE_POLL_INTERVAL_MS)
        }

        if (!isConnectedTo(tv.id) && reconnectJob?.isActive != true) {
            _errors.emit(
                Snackbar.warning(
                    "The TV didn't turn on",
                    "Check that Wake-on-LAN is enabled on the TV and that its MAC and IP addresses are correct"
                )
            )
        }
    }

    fun updateTv(tv: Tv) {
        getDevice(tv.id)?.let {
            it.displayName = tv.displayName
            // Keep the in-memory tv in sync, otherwise the stale value would be written back
            // to the database the next time this device updates itself.
            it.tv.macAddress = tv.macAddress
            it.tv.homeAppId = tv.homeAppId
            it.tv.okLongPressAction = tv.okLongPressAction
            it.tv.ipAddress = tv.ipAddress
            it.tv.brightnessControls = tv.brightnessControls
            it.tv.serviceUuid = tv.serviceUuid
        }

        val currDevice = connectedDevice.value
        if (currDevice != null && currDevice.id == tv.id) {
            (currDevice as LgDevice).updateDisplayName()
        }

        scope.launch {
            tvRepository.updateTv(tv)
        }
    }

    fun resume() {
        val pausedFor = System.currentTimeMillis() - pausedAtMs
        appStatus = AppStatus.AWAITING_CONNECTION_AFTER_PAUSE
        AppLog.d("Device_Manager", "resume() after ${pausedFor}ms, desired=$desiredTvId")

        // Not connected to anything before pausing, nothing to recover. Discovery plus the
        // auto connect setting take it from here.
        val id = desiredTvId ?: return

        scope.launch {
            val device = _connectedDevice.value
            val status = device?.state?.value?.status
            when {
                device != null && device.id == id
                        && (status == DeviceStatus.CONNECTING || status == DeviceStatus.PAIRING)
                        && reconnectJob?.isActive == true
                    -> AppLog.d("Device_Manager", "Resuming app. Already connecting to the device.")

                device != null && device.id == id && status == DeviceStatus.CONNECTED
                        && pausedFor < QUICK_RESUME_MS -> {
                    // Only left the app briefly, the connection is most likely still fine
                    if (!device.isResponsive(RESUME_PROBE_TIMEOUT_MS)) {
                        dropDeadConnection(device)
                    } else {
                        ensureMouse(device)
                    }
                }

                else -> {
                    // Connections don't reliably survive the app being in the background (the
                    // system cuts the network, and the tv socket never notices). Instead of waiting
                    // for a probe to time out, replace the old connection right away.
                    AppLog.d("Device_Manager", "Resuming app. Replacing the old connection.")
                    if (device != null && device.id == id) {
                        withContext(Dispatchers.IO) { runCatching { device.disconnect() } }
                    }
                    startReconnect(id)
                }
            }
        }
    }

    fun pause() {
        appStatus = AppStatus.PAUSED
        pausedAtMs = System.currentTimeMillis()
        AppLog.d("Device_Manager", "pause()")
        // No point retrying in the background, the system cuts our network access anyway.
        // resume() starts over.
        reconnectJob?.cancel()
    }

    private companion object {
        const val RECONNECT_GIVE_UP_MS = 3 * 60 * 1000L
        val RECONNECT_DELAYS_MS = longArrayOf(400L, 800L, 1500L, 3000L, 5000L)
        const val CONNECT_TIMEOUT_MS = 7_000L
        const val QUICK_RESUME_MS = 5_000L
        const val REAL_DESCRIPTION_WAIT_MS = 4_000L
        const val FORCE_DISCOVERY_AFTER_MS = 1_200L
        const val FORCE_DISCOVERY_MIN_GAP_MS = 8_000L
        const val LAST_TV_KEY = "last_tv_id"
        const val MOUSE_SETTLE_MS = 700L
        const val MOUSE_CHECK_DELAY_MS = 900L
        const val MOUSE_ATTEMPTS = 6
        const val REGAIN_AFTER_GIVE_UP_MS = 15_000L
        const val PAIRING_TIMEOUT_MS = 90_000L
        const val HEARTBEAT_INTERVAL_MS = 8_000L
        const val HEARTBEAT_TIMEOUT_MS = 4_000L
        const val HEARTBEAT_MAX_MISSES = 2
        const val RESUME_PROBE_TIMEOUT_MS = 2_500L
        const val WAKE_CONNECT_DELAY_MS = 3000L
        const val WAKE_RESCAN_INTERVAL_MS = 5000L
        const val WAKE_RESCAN_ATTEMPTS = 12
        const val WAKE_POLL_INTERVAL_MS = 1000L
        const val WAKE_POLL_ATTEMPTS = 90
        const val PRESENCE_INTERVAL_MS = 4000L
        const val PRESENCE_MAX_FAILURES = 2
        const val WEBOS_SERVICE_FILTER = "urn:lge-com:service:webos-second-screen:1"
        const val WEBOS_PORT = 3001
    }
}

/** The tv shown when the app opens. tv is null if none was ever saved, once loaded. */
data class LastTv(val loaded: Boolean = false, val tv: Tv? = null)
