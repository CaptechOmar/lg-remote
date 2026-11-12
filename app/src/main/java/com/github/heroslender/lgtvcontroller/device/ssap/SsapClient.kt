package com.github.heroslender.lgtvcontroller.device.ssap

import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume

/**
 * A small direct client for the webOS TV control protocol (SSAP): a websocket to the TV that is
 * registered with a stored client key, plus the TV's "pointer" socket used for remote keys.
 *
 * It talks to a known IP address and does no discovery, so (re)connecting takes a fraction of a
 * second. All callbacks come from OkHttp's threads.
 */
class SsapClient(
    private val ip: String,
    private var clientKey: String?,
    private val listener: Listener,
) {
    interface Listener {
        /** The TV accepted us. [clientKey] must be stored and passed in next time. */
        fun onRegistered(clientKey: String)

        /** The TV is showing the "allow this device" prompt. */
        fun onPairingPrompt()

        /** The connection ended or could not be made. Called at most once per [connect]. */
        fun onClosed(reason: String)
    }

    private class Pending(val callback: (JSONObject?, String?) -> Unit, val subscription: Boolean)

    private val ids = AtomicInteger(1)
    private val pending = ConcurrentHashMap<String, Pending>()

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var pointerSocket: WebSocket? = null

    @Volatile
    private var pointerOpening = false

    @Volatile
    private var closedReported = false

    @Volatile
    var isRegistered = false
        private set

    @Volatile
    private var closing = false

    /** Opens the connection. Tries the secure port first, then the plain one. */
    fun connect() {
        closedReported = false
        closing = false
        open(secure = true)
    }

    private fun open(secure: Boolean) {
        val url = if (secure) "wss://$ip:3001" else "ws://$ip:3000"
        val request = Request.Builder().url(url).build()

        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (socket === webSocket) register(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (socket === webSocket) handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket === webSocket) reportClosed("closed $code $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket !== webSocket) return
                if (secure && !isRegistered && !closing) {
                    // Some TVs only offer the plain port
                    Log.d(TAG, "wss failed (${t.message}), trying ws")
                    open(secure = false)
                } else {
                    reportClosed(t.message ?: "connection failed")
                }
            }
        })
    }

    private fun register(webSocket: WebSocket) {
        val payload = JSONObject()
        payload.put("forcePairing", false)
        payload.put("pairingType", "PROMPT")
        clientKey?.let { payload.put("client-key", it) }
        payload.put(
            "manifest", JSONObject()
                .put("manifestVersion", 1)
                .put("permissions", JSONArray(PERMISSIONS))
        )

        webSocket.send(
            JSONObject()
                .put("type", "register")
                .put("id", REGISTER_ID)
                .put("payload", payload)
                .toString()
        )
    }

    private fun handleMessage(text: String) {
        val message = try {
            JSONObject(text)
        } catch (_: Exception) {
            return
        }

        val id = message.optString("id")
        val type = message.optString("type")
        val payload = message.optJSONObject("payload")

        if (id == REGISTER_ID) {
            when {
                type == "registered" -> {
                    val key = payload?.optString("client-key").orEmpty()
                    if (key.isNotEmpty()) clientKey = key
                    isRegistered = true
                    listener.onRegistered(clientKey.orEmpty())
                }

                type == "response" && payload?.optString("pairingType") == "PROMPT" ->
                    listener.onPairingPrompt()

                type == "error" -> {
                    // E.g. the stored key was rejected: forget it so the next attempt asks again
                    clientKey = null
                    reportClosed(message.optString("error", "registration failed"))
                    socket?.close(1000, null)
                }
            }
            return
        }

        val entry = pending[id] ?: return
        if (!entry.subscription) pending.remove(id)

        if (type == "error") {
            entry.callback(null, message.optString("error", "error"))
        } else {
            entry.callback(payload ?: JSONObject(), null)
        }
    }

    private fun reportClosed(reason: String) {
        isRegistered = false
        pointerSocket?.cancel()
        pointerSocket = null

        val failed = pending.values.toList()
        pending.clear()
        failed.forEach { it.callback(null, "closed") }

        if (!closedReported) {
            closedReported = true
            listener.onClosed(reason)
        }
    }

    /** Ends the connection. [Listener.onClosed] is not called for a deliberate close. */
    fun close() {
        closing = true
        closedReported = true
        isRegistered = false
        pointerSocket?.cancel()
        pointerSocket = null
        socket?.cancel()
        socket = null
        pending.clear()
    }

    private fun send(uri: String, payload: JSONObject?, entry: Pending?): String? {
        val ws = socket ?: return null
        if (!isRegistered) return null

        val id = "req_${ids.getAndIncrement()}"
        if (entry != null) pending[id] = entry

        val message = JSONObject().put("type", if (entry?.subscription == true) "subscribe" else "request")
            .put("id", id)
            .put("uri", uri)
        if (payload != null) message.put("payload", payload)

        if (!ws.send(message.toString())) {
            pending.remove(id)
            return null
        }
        return id
    }

    /** Sends a request and doesn't wait for the answer. */
    fun fire(uri: String, payload: JSONObject? = null) {
        send(uri, payload, null)
    }

    /** Sends a request and waits for the answer, or null on error or after [timeoutMs]. */
    suspend fun request(uri: String, payload: JSONObject? = null, timeoutMs: Long = 5000): JSONObject? {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val id = send(uri, payload, Pending({ result, _ ->
                    if (cont.isActive) cont.resume(result)
                }, subscription = false))

                if (id == null) {
                    if (cont.isActive) cont.resume(null)
                } else {
                    cont.invokeOnCancellation { pending.remove(id) }
                }
            }
        }
    }

    /** Keeps [onUpdate] called for every update the TV pushes for [uri]. */
    fun subscribe(uri: String, payload: JSONObject? = null, onUpdate: (JSONObject) -> Unit) {
        send(uri, payload, Pending({ result, _ -> if (result != null) onUpdate(result) }, subscription = true))
    }

    /**
     * Opens the TV's pointer socket, which carries remote key presses and pointer movement.
     * Does nothing if it's already open or opening.
     */
    suspend fun connectPointer() {
        if (pointerSocket != null || pointerOpening) return
        pointerOpening = true
        try {
            val path = request("ssap://com.webos.service.networkinput/getPointerInputSocket")
                ?.optString("socketPath")
                ?.takeIf { it.isNotEmpty() }
                ?: return

            pointerSocket = http.newWebSocket(Request.Builder().url(path).build(), object : WebSocketListener() {
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (pointerSocket === webSocket) pointerSocket = null
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (pointerSocket === webSocket) pointerSocket = null
                }
            })
        } finally {
            pointerOpening = false
        }
    }

    private fun sendPointer(text: String): Boolean = pointerSocket?.send(text) ?: false

    /** Presses a remote key, e.g. `UP`, `ENTER`, `HOME`, `RED`. Returns false if the socket isn't ready. */
    fun button(name: String): Boolean = sendPointer("type:button\nname:$name\n\n")

    fun click(): Boolean = sendPointer("type:click\n\n")

    fun move(dx: Double, dy: Double): Boolean =
        sendPointer("type:move\ndx:$dx\ndy:$dy\ndown:0\n\n")

    fun scroll(dx: Double, dy: Double): Boolean =
        sendPointer("type:scroll\ndx:$dx\ndy:$dy\n\n")

    val isPointerReady: Boolean get() = pointerSocket != null

    companion object {
        private const val TAG = "SsapClient"
        private const val REGISTER_ID = "register_0"

        private val PERMISSIONS = listOf(
            "LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CONTROL_AUDIO",
            "CONTROL_INPUT_MEDIA_PLAYBACK", "UPDATE_FROM_REMOTE_APP",
            "CONTROL_POWER", "READ_INSTALLED_APPS", "CONTROL_DISPLAY", "CONTROL_INPUT_JOYSTICK",
            "CONTROL_INPUT_MEDIA_RECORDING", "CONTROL_INPUT_TV", "READ_INPUT_DEVICE_LIST",
            "READ_NETWORK_STATE", "READ_TV_CHANNEL_LIST", "WRITE_NOTIFICATION_TOAST",
            "CONTROL_BLUETOOTH", "CHECK_BLUETOOTH_DEVICE", "CONTROL_USER_INFO",
            "CONTROL_TIMER_INFO", "READ_SETTINGS", "CONTROL_TV_SCREEN",
            "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD", "READ_CURRENT_CHANNEL",
            "READ_RUNNING_APPS",
        )

        /**
         * The TV uses a self-signed certificate, so any certificate is accepted. This client only
         * ever talks to the TV's address on the local network.
         */
        private val http: OkHttpClient by lazy {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val ssl = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustAll), SecureRandom())
            }

            OkHttpClient.Builder()
                .sslSocketFactory(ssl.socketFactory, trustAll)
                .hostnameVerifier(HostnameVerifier { _, _ -> true })
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
        }
    }
}
