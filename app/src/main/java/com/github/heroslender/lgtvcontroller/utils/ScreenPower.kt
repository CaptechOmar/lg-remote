package com.github.heroslender.lgtvcontroller.utils

import com.connectsdk.service.WebOSTVService
import com.connectsdk.service.capability.listeners.ResponseListener
import com.connectsdk.service.command.ServiceCommand
import com.connectsdk.service.command.ServiceCommandError
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
) {
    val listener = object : ResponseListener<Any> {
        override fun onSuccess(response: Any?) {
            onResult(null)
        }

        override fun onError(error: ServiceCommandError) {
            onResult(error)
        }
    }

    ServiceCommand<ResponseListener<Any>>(this, uri, payload, true, listener).send()
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
