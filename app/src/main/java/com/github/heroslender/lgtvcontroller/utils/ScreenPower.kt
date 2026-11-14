package com.github.heroslender.lgtvcontroller.utils

import com.connectsdk.service.WebOSTVService
import com.connectsdk.service.capability.listeners.ResponseListener
import com.connectsdk.service.command.ServiceCommand
import com.connectsdk.service.command.ServiceCommandError
import org.json.JSONObject

/**
 * Turns the TV's screen off or on while the TV itself stays on (sound keeps playing), like the
 * "Screen Off" feature of the TV's quick settings.
 *
 * @param onResult called with null on success or the error otherwise.
 */
fun WebOSTVService.setScreenPower(on: Boolean, onResult: (ServiceCommandError?) -> Unit) {
    val uri = if (on) {
        "ssap://com.webos.service.tvpower/power/turnOnScreen"
    } else {
        "ssap://com.webos.service.tvpower/power/turnOffScreen"
    }

    val listener = object : ResponseListener<Any> {
        override fun onSuccess(response: Any?) {
            onResult(null)
        }

        override fun onError(error: ServiceCommandError) {
            onResult(error)
        }
    }

    val payload = JSONObject().put("standbyMode", "active")
    ServiceCommand<ResponseListener<Any>>(this, uri, payload, listener).send()
}
