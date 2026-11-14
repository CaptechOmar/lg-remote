package com.github.heroslender.lgtvcontroller.device.impl

import com.github.heroslender.lgtvcontroller.utils.AppLog
import com.connectsdk.core.AppInfo
import com.connectsdk.core.ExternalInputInfo
import com.connectsdk.core.TextInputStatusInfo
import com.connectsdk.service.WebOSTVService
import com.connectsdk.service.capability.ExternalInputControl
import com.connectsdk.service.capability.KeyControl
import com.connectsdk.service.capability.Launcher
import com.connectsdk.service.capability.TextInputControl
import com.connectsdk.service.capability.VolumeControl
import com.connectsdk.service.command.ServiceCommandError
import com.github.heroslender.lgtvcontroller.DeviceManager
import com.github.heroslender.lgtvcontroller.device.Device
import com.github.heroslender.lgtvcontroller.device.DeviceControllerButton
import com.github.heroslender.lgtvcontroller.device.OK_LONG_PRESS_HOLD
import com.github.heroslender.lgtvcontroller.device.DeviceState
import com.github.heroslender.lgtvcontroller.device.DeviceStatus
import com.github.heroslender.lgtvcontroller.domain.model.App
import com.github.heroslender.lgtvcontroller.domain.model.Input
import com.github.heroslender.lgtvcontroller.domain.model.Tv
import com.github.heroslender.lgtvcontroller.ui.snackbar.Snackbar
import com.github.heroslender.lgtvcontroller.utils.lunaRequest
import com.github.heroslender.lgtvcontroller.utils.sendRequest
import com.github.heroslender.lgtvcontroller.utils.sendRequestAny
import com.github.heroslender.lgtvcontroller.utils.sendSpecialKey
import com.github.heroslender.lgtvcontroller.utils.setScreenPower
import com.github.heroslender.lgtvcontroller.utils.setPointerButtonDown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class LgDevice(
    val device: LgNetworkDevice,
    private val manager: DeviceManager,
    status: DeviceStatus = DeviceStatus.DISCONNECTED,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : Device {
    override val tv: Tv
        get() = device.tv

    private val _state: MutableStateFlow<DeviceState> = MutableStateFlow(
        DeviceState(
            displayName = tv.displayName,
            status = status,
            runningApp = "",
            apps = tv.apps,
            inputs = tv.inputs,
            isKeyboardOpen = false,
            brightnessControls = tv.brightnessControls,
        )
    )
    override val state: StateFlow<DeviceState>
        get() = _state

    private val _errors = MutableSharedFlow<Snackbar>()
    override val errors: Flow<Snackbar> = _errors

    val service
        get() = device.device.getServiceByName(WebOSTVService.ID) as WebOSTVService

    override fun hasCapability(capability: String): Boolean {
        return device.device.hasCapability(capability)
    }

    override fun powerOff() {
        // Deliberate, so the app must not try to reconnect
        manager.onUserDisconnect(id)
        service.powerOff(null)
        disconnect()
    }

    /**
     * Asks the tv for its volume and waits for any answer. Silence for [timeoutMs] means the
     * connection is dead, even though the sdk may still report it as connected.
     */
    suspend fun isResponsive(timeoutMs: Long): Boolean {
        return try {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    service.getVolume(object : VolumeControl.VolumeListener {
                        override fun onSuccess(volume: Float) {
                            if (cont.isActive) cont.resume(true)
                        }

                        // An error is still an answer, the tv is reachable
                        override fun onError(error: ServiceCommandError) {
                            if (cont.isActive) cont.resume(true)
                        }
                    })
                }
            } ?: false
        } catch (e: Exception) {
            AppLog.w("LgDevice", "Heartbeat failed: ${e.message}")
            false
        }
    }

    override fun volumeUp() {
        service.volumeUp()
    }

    override fun volumeDown() {
        service.volumeDown()
    }

    override fun mute() {
        service.sendSpecialKey("MUTE")
    }

    override fun channelUp() {
        service.channelUp()
    }

    override fun channelDown() {
        service.channelDown()
    }

    override fun up() {
        service.keyControl.up(null)
    }

    override fun down() {
        service.keyControl.down(null)
    }

    override fun left() {
        service.keyControl.left(null)
    }

    override fun right() {
        service.keyControl.right(null)
    }

    override fun ok() {
        service.keyControl.sendKeyCode(KeyControl.KeyCode.ENTER, null)
//        service.keyControl.ok(null)
    }

    override fun okLongPress() {
        if (tv.okLongPressAction == OK_LONG_PRESS_HOLD) {
            if (!service.setPointerButtonDown(true)) {
                scope.launch {
                    _errors.emit(
                        Snackbar.error(
                            "Can't hold OK",
                            "This version of the TV library doesn't support holding the pointer button"
                        )
                    )
                }
            }
            return
        }

        val action = tv.okLongPressAction
            ?.let { name -> DeviceControllerButton.entries.firstOrNull { it.name == name } }

        if (action == null || action == DeviceControllerButton.OK_LONG_PRESS) {
            // Nothing mapped, behave like a normal press
            ok()
        } else {
            executeControllerButton(action)
        }
    }

    override fun okLongPressEnd() {
        if (tv.okLongPressAction == OK_LONG_PRESS_HOLD) {
            service.setPointerButtonDown(false)
        }
    }

    override fun back() {
        service.keyControl.back(null)
    }

    override fun exit() {
        service.sendSpecialKey("EXIT")
    }

    override fun home() {
        val appId = tv.homeAppId
        if (appId.isNullOrBlank()) {
            service.keyControl.home(null)
        } else {
            // The user remapped the Home button to open an app (e.g. a custom launcher)
            launchApp(appId)
        }
    }

    override fun info() {
        service.sendSpecialKey("INFO")
    }

    override fun source() {
        // The remote's Input key opens the TV's input picker (launching the picker app directly
        // is refused with "Application error")
        try {
            service.sendSpecialKey("INPUT_HUB")
        } catch (e: Exception) {
            AppLog.e("LgDevice", "Input key failed: ${e.message}")
            scope.launch {
                _errors.emit(Snackbar.error("Couldn't open the input picker", e.message))
            }
        }
    }

    @Volatile
    private var backlight: Int? = null
    private var backlightReadAt = 0L
    private var pendingBrightness = 0
    private var readingBacklight = false

    override fun adjustBrightness(delta: Int) {
        val now = System.currentTimeMillis()
        val current = backlight
        if (current != null && now - backlightReadAt < BACKLIGHT_CACHE_MS) {
            // Known, so holding the button can send changes as fast as it repeats
            setBacklight((current + delta).coerceIn(0, 100))
            return
        }

        // Unknown (or maybe changed with the TV's own remote): read it first. Presses made
        // meanwhile are added up and applied together.
        synchronized(this) {
            pendingBrightness += delta
            if (readingBacklight) return
            readingBacklight = true
        }

        val payload = JSONObject()
            .put("category", "picture")
            .put("keys", JSONArray().put("backlight"))
        service.sendRequestAny(SETTINGS_GET, payload) { error, response ->
            val value = (response as? JSONObject)
                ?.optJSONObject("settings")
                ?.opt("backlight")
                ?.toString()
                ?.toDoubleOrNull()
                ?.toInt()

            val pending = synchronized(this) {
                readingBacklight = false
                pendingBrightness.also { pendingBrightness = 0 }
            }

            if (error != null || value == null) {
                AppLog.e("LgDevice", "Couldn't read the brightness: ${error?.message ?: response}")
                scope.launch {
                    _errors.emit(Snackbar.error("Couldn't read the brightness", error?.message))
                }
            } else {
                setBacklight((value + pending).coerceIn(0, 100))
            }
        }
    }

    private fun setBacklight(value: Int) {
        backlight = value
        _state.update { it.copy(brightness = value) }
        backlightReadAt = System.currentTimeMillis()

        val payload = JSONObject()
            .put("category", "picture")
            .put("settings", JSONObject().put("energySaving", "off").put("backlight", value.toString()))
        // Newer webOS versions don't accept the settings call over the websocket ("no such
        // service or method"), but the internal settings service can be reached through an alert.
        service.lunaRequest("com.webos.settingsservice/setSystemSettings", payload) { lunaError ->
            if (lunaError == null) return@lunaRequest

            AppLog.w("LgDevice", "Settings via luna failed (${lunaError.message}), trying the direct call")
            service.sendRequestAny(SETTINGS_SET, payload) { error, _ ->
                if (error != null) {
                    backlight = null
                    AppLog.e("LgDevice", "Couldn't set the brightness: ${error.message}")
                    scope.launch {
                        _errors.emit(Snackbar.error("Couldn't change the brightness", error.message))
                    }
                }
            }
        }
    }

    @Volatile
    private var screenOff = false

    override fun readSetting(category: String, key: String, onResult: (String?) -> Unit) {
        val payload = JSONObject()
            .put("category", category)
            .put("keys", JSONArray().put(key))
        service.sendRequestAny(SETTINGS_GET, payload) { error, response ->
            val value = (response as? JSONObject)?.optJSONObject("settings")?.opt(key)?.toString()
            if (error != null || value == null) {
                AppLog.w("LgDevice", "Couldn't read $category/$key: ${error?.message ?: response}")
                onResult(null)
            } else {
                onResult(value)
            }
        }
    }

    override fun changeSetting(category: String, key: String, value: String) {
        val payload = JSONObject()
            .put("category", category)
            .put("settings", JSONObject().put(key, value))
        service.lunaRequest("com.webos.settingsservice/setSystemSettings", payload) { error ->
            if (error != null) {
                AppLog.e("LgDevice", "Couldn't set $category/$key: ${error.message}")
                scope.launch {
                    _errors.emit(Snackbar.error("Couldn't change the setting", error.message))
                }
            }
        }
    }

    override fun toggleScreen() {
        val turnOn = screenOff
        screenOff = !turnOn
        service.setScreenPower(turnOn) { error ->
            if (error != null) {
                // It didn't work, the screen is still as it was
                screenOff = turnOn
                AppLog.e("LgDevice", "Screen power failed: ${error.message}")
                scope.launch {
                    _errors.emit(Snackbar.error("Couldn't change the screen", error.message))
                }
            }
        }
    }

    override fun menu() {
        service.sendSpecialKey("MENU")
    }

    override fun qmenu() {
        service.sendSpecialKey("QMENU")
    }

    override fun red() {
        service.sendSpecialKey("RED")
    }

    override fun green() {
        service.sendSpecialKey("GREEN")
    }

    override fun yellow() {
        service.sendSpecialKey("YELLOW")
    }

    override fun blue() {
        service.sendSpecialKey("BLUE")
    }

    override fun launchNetflix() = launchApp("netflix")

    override fun launchApp(appId: String) {
        service.launchApp(appId, null)
    }

    override fun disconnect() {
        device.device.disconnect()
    }

    override fun updateStatus(status: DeviceStatus) {
        _state.update {
            it.copy(status = status)
        }
    }

    suspend fun getExternalInputList(): List<Input> = suspendCoroutine { continuation ->
        device.device.externalInputControl.getExternalInputList(object :
            ExternalInputControl.ExternalInputListListener {
            override fun onSuccess(discoveredInputs: List<ExternalInputInfo>) {
                val inputs = mutableListOf<Input>()
                for (info in discoveredInputs) {
                    inputs.add(
                        Input(
                            id = info.rawData.getString("appId"),
                            name = info.name,
                            icon = info.iconURL,
                            connected = info.isConnected,
                            favorite = info.rawData.optBoolean("favorite", false)
                        )
                    )
                }

                continuation.resume(inputs)
            }

            override fun onError(error: ServiceCommandError) {
                scope.launch {
                    _errors.emit(Snackbar.error("Failed to load inputs from TV", error.message))
                }
                continuation.resume(emptyList())
            }
        })
    }

    suspend fun getAppList(): List<App> {
        data class AppListApp(
            val id: String,
            val name: String,
            val systemApp: Boolean,
            val installedTime: Long,
        )

        val appList: MutableList<AppListApp> = suspendCoroutine { cont ->
            device.device.launcher.getAppList(object : Launcher.AppListListener {
                override fun onSuccess(apps: List<AppInfo>) {
                    val appList = mutableListOf<AppListApp>()
                    apps.forEach {
                        appList.add(
                            AppListApp(
                                id = it.id,
                                name = it.name,
                                systemApp = it.rawData.optBoolean("systemApp", false),
                                installedTime = it.rawData.optLong("installedTime", 0L),
                            )
                        )
                    }

                    cont.resume(appList)
                }

                override fun onError(error: ServiceCommandError) {
                    scope.launch {
                        _errors.emit(
                            Snackbar.error(
                                "Failed to load app list from TV",
                                error.message
                            )
                        )
                    }
                    cont.resume(mutableListOf())
                }
            })
        }

        return suspendCoroutine { continuation ->
            val webOSTVService = device.device.getServiceByName(WebOSTVService.ID) as WebOSTVService
            webOSTVService.getLaunchPoints(object : WebOSTVService.LaunchPointsListener {
                override fun onSuccess(arr: JSONArray) {
                    val list = mutableListOf<App>()
                    for (i in 0 until arr.length()) {
                        val raw = arr.getJSONObject(i)
                        if (raw.optBoolean("systemApp", false))
                            continue

                        val id = raw.getString("id")
                        val found = appList.firstOrNull { it.id == id }
                        if (found != null && !found.systemApp) {
                            list.add(
                                App(
                                    id = found.id,
                                    name = found.name,
                                    icon = raw.getString("largeIcon"),
                                    installedTime = found.installedTime,
                                )
                            )
                        }
                    }

                    continuation.resume(list)
                }

                override fun onError(error: ServiceCommandError) {
                    scope.launch {
                        _errors.emit(
                            Snackbar.error(
                                "Failed to load launch points from TV",
                                error.message
                            )
                        )
                    }
                    continuation.resume(emptyList())
                }
            })
        }
    }

    fun subscribeRunningApp() {
        device.device.launcher.subscribeRunningApp(object : Launcher.AppInfoListener {
            override fun onSuccess(appInfo: AppInfo) {
                _state.update { it.copy(runningApp = appInfo.id) }
            }

            override fun onError(error: ServiceCommandError) {
            }
        })
    }

    override fun sendText(text: String) {
        device.device.textInputControl.sendText(text)
    }

    override fun sendEnter() {
        device.device.textInputControl.sendEnter()
    }

    override fun sendDelete() {
        device.device.textInputControl.sendDelete()
    }

    override fun mouseClick() {
        service.mouseControl.click()
    }

    override fun moveMouse(x: Double, y: Double) {
        service.mouseControl.move(x, y)
    }

    override fun scroll(x: Double, y: Double) {
        service.mouseControl.scroll(x, y)
    }

    fun subscribeTextInputListener() {
        val textInputControl = device.device.getCapability(TextInputControl::class.java)
        textInputControl.subscribeTextInputStatus(object :
            TextInputControl.TextInputStatusListener {
            override fun onSuccess(statusInfo: TextInputStatusInfo) {
                if (statusInfo.contentType != null) {
                    _state.update { it.copy(isKeyboardOpen = true) }
                } else {
                    _state.update { it.copy(isKeyboardOpen = false) }
                }

                println("TextInputStatusListener: ${statusInfo.rawData.toString(2)}")
            }

            override fun onError(error: ServiceCommandError) {
                println("TextInputStatusListener error: $error")
            }
        })
    }

    val TextInputStatusInfo.contentType: String?
        get() {
            return this::class.java.getDeclaredField("contentType").let { field ->
                field.isAccessible = true
                return@let field[this] as String?
            }
        }

    suspend fun loadAppsAndInputs() {
        try {
            val inputList = getExternalInputList()
            tv.inputs = inputList
            _state.update { it.copy(inputs = inputList) }
        } catch (e: Exception) {
            tv.inputs = emptyList()
            _state.update { it.copy(inputs = emptyList()) }
            AppLog.e("LgDevice", "Failed to load inputs: ${e.message}")
            _errors.emit(Snackbar.error("Failed to load inputs", e.message))
        }

        try {
            val appList = getAppList().sortedByDescending { it.installedTime }
            tv.apps = appList
            _state.update { it.copy(apps = appList) }
        } catch (e: Exception) {
            tv.apps = emptyList()
            _state.update { it.copy(apps = emptyList()) }
            AppLog.e("LgDevice", "Failed to load apps: ${e.message}")
            _errors.emit(Snackbar.error("Failed to load apps", e.message))
        }

        manager.updateTv(tv)

        try {
            subscribeRunningApp()
        } catch (e: Exception) {
            AppLog.e("LgDevice", "Failed to subscribe to running app: ${e.message}")
            _errors.emit(Snackbar.error("Failed to subscribe to running app", e.message))
        }

        try {
            service.subscribeVolume(object : VolumeControl.VolumeListener {
                override fun onSuccess(volume: Float) {
                    _state.update { it.copy(volume = volume.toInt()) }
                }

                override fun onError(error: ServiceCommandError) {}
            })
        } catch (e: Exception) {
            AppLog.w("LgDevice", "Failed to subscribe to volume: ${e.message}")
        }

        try {
            subscribeTextInputListener()
        } catch (e: Exception) {
            AppLog.e("LgDevice", "Failed to subscribe to text input: ${e.message}")
            _errors.emit(Snackbar.error("Failed to subscribe to text input", e.message))
        }
    }

    fun updateDisplayName() {
        _state.update {
            it.copy(displayName = tv.displayName, brightnessControls = tv.brightnessControls)
        }
    }
}

private const val BACKLIGHT_CACHE_MS = 20_000L

private val SETTINGS_GET = listOf(
    "ssap://settings/getSystemSettings",
    "ssap://com.webos.settingsservice/getSystemSettings",
)

private val SETTINGS_SET = listOf(
    "ssap://settings/setSystemSettings",
    "ssap://com.webos.settingsservice/setSystemSettings",
)
