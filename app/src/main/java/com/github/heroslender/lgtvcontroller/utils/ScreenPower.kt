package com.github.heroslender.lgtvcontroller.utils

import com.connectsdk.service.WebOSTVService
import com.connectsdk.service.capability.listeners.ResponseListener
import com.connectsdk.service.command.ServiceCommand
import com.connectsdk.service.command.ServiceCommandError
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sends a raw request to the TV (an `ssap://` address with a JSON payload). The webOS flavour of
 * the sdk's command constructor is needed, the other one makes the TV answer "unknown message type".
 *
 * @param onResult called with null on success or the error otherwise.
 */
fun WebOSTVService.sendRequest(
    uri: String,
    payload: JSONObject,
    onResult: (ServiceCommandError?) -> Unit,
) = sendRequestForResult(uri, payload) { error, _ -> onResult(error) }

/** Like [sendRequest], but also hands over the TV's answer. */
fun WebOSTVService.sendRequestForResult(
    uri: String,
    payload: JSONObject,
    onResult: (ServiceCommandError?, Any?) -> Unit,
) {
    val listener = object : ResponseListener<Any> {
        override fun onSuccess(response: Any?) {
            onResult(null, response)
        }

        override fun onError(error: ServiceCommandError) {
            onResult(error, null)
        }
    }

    ServiceCommand<ResponseListener<Any>>(this, uri, payload, true, listener).send()
}

/** Tries each address in turn (webOS versions differ) until one works. */
fun WebOSTVService.sendRequestAny(
    uris: List<String>,
    payload: JSONObject,
    onResult: (ServiceCommandError?, Any?) -> Unit,
) {
    fun attempt(index: Int) {
        sendRequestForResult(uris[index], payload) { error, response ->
            if (error == null || index == uris.lastIndex) {
                onResult(error, response)
            } else {
                attempt(index + 1)
            }
        }
    }

    attempt(0)
}

/**
 * Turns the TV's screen off or on while the TV itself stays on (sound keeps playing). Different
 * webOS versions want different things, so a few variants are tried in turn until one works.
 *
 * @param onResult called with null on success or the last error if every variant failed.
 */
fun WebOSTVService.setScreenPower(on: Boolean, onResult: (ServiceCommandError?) -> Unit) {
    val power = if (on) "turnOnScreen" else "turnOffScreen"
    val attempts = mutableListOf(
        "ssap://com.webos.service.tvpower/power/$power" to JSONObject().put("standbyMode", "active"),
        "ssap://com.webos.service.tvpower/power/$power" to JSONObject(),
    )
    if (!on) {
        // Opens the "energy saving: screen off" action of the TV
        attempts += "ssap://system.launcher/launch" to JSONObject()
            .put("id", "com.webos.app.tvhotkey")
            .put("params", JSONObject().put("activateType", "energy-saving-mode"))
    }

    fun attempt(index: Int) {
        val (uri, payload) = attempts[index]
        sendRequest(uri, payload) { error ->
            if (error == null || index == attempts.lastIndex) {
                onResult(error)
            } else {
                attempt(index + 1)
            }
        }
    }

    attempt(0)
}

/**
 * Calls one of the TV's internal (luna) services, which the websocket doesn't expose directly. The
 * way in is the alert API: an alert is created whose buttons/close action is the luna call, and
 * closing it makes the TV run that call. The TV's answer isn't available this way.
 *
 * @param uri the service address without the `luna://` prefix.
 */
fun WebOSTVService.lunaRequest(
    uri: String,
    params: JSONObject,
    onResult: (ServiceCommandError?) -> Unit,
) {
    val luna = "luna://$uri"
    fun action() = JSONObject().put("uri", luna).put("params", params)

    val payload = JSONObject()
        .put("message", " ")
        .put("buttons", JSONArray().put(JSONObject().put("label", "").put("onClick", luna).put("params", params)))
        .put("onclose", action())
        .put("onfail", action())

    sendRequestForResult("ssap://system.notifications/createAlert", payload) { error, response ->
        val alertId = (response as? JSONObject)?.optString("alertId")
        if (error != null) {
            onResult(error)
        } else if (alertId.isNullOrEmpty()) {
            onResult(ServiceCommandError(0, "The TV didn't create the alert", response))
        } else {
            sendRequestForResult(
                "ssap://system.notifications/closeAlert",
                JSONObject().put("alertId", alertId),
            ) { closeError, _ -> onResult(closeError) }
        }
    }
}
