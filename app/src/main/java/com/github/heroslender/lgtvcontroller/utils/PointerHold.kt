package com.github.heroslender.lgtvcontroller.utils

import android.util.Log
import com.connectsdk.service.WebOSTVService

/**
 * Presses ([down] = true) or releases the TV's pointer button, like holding the click of the
 * Magic Remote. The sdk only exposes this on its internal pointer socket, hence the reflection.
 *
 * @return false if the sdk doesn't allow it.
 */
fun WebOSTVService.setPointerButtonDown(down: Boolean): Boolean {
    return try {
        val field = WebOSTVService::class.java.getDeclaredField("mouseSocket")
        field.isAccessible = true
        val socket = field.get(this) ?: return false

        val move = socket.javaClass.getDeclaredMethod(
            "move",
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        )
        move.isAccessible = true
        move.invoke(socket, 0.0, 0.0, down)
        true
    } catch (e: Exception) {
        Log.e("PointerHold", "Failed to ${if (down) "press" else "release"} the pointer button", e)
        false
    }
}

/** True if the sdk currently has a pointer socket (the one the arrows and OK are sent through). */
fun WebOSTVService.hasMouseSocket(): Boolean {
    return try {
        val field = WebOSTVService::class.java.getDeclaredField("mouseSocket")
        field.isAccessible = true
        field.get(this) != null
    } catch (e: Exception) {
        false
    }
}
