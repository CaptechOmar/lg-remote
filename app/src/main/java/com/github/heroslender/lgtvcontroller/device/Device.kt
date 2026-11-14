package com.github.heroslender.lgtvcontroller.device

import com.connectsdk.service.capability.ExternalInputControl
import com.connectsdk.service.capability.KeyControl
import com.connectsdk.service.capability.PowerControl
import com.connectsdk.service.capability.TVControl
import com.connectsdk.service.capability.VolumeControl
import com.github.heroslender.lgtvcontroller.domain.model.Tv
import com.github.heroslender.lgtvcontroller.ui.snackbar.Snackbar
import kotlinx.coroutines.flow.Flow

interface Device {
    val tv: Tv

    val id: String
        get() = tv.id

    val friendlyName: String
        get() = tv.name

    val state: Flow<DeviceState>

    val errors: Flow<Snackbar>

    fun hasCapability(btn: DeviceControllerButton): Boolean {
        when (btn) {
            DeviceControllerButton.POWER -> return hasCapability(PowerControl.Any)
            DeviceControllerButton.VOLUME_UP,
            DeviceControllerButton.VOLUME_DOWN,
                -> return hasCapability(VolumeControl.Volume_Up_Down)

            DeviceControllerButton.MUTE -> return hasCapability(VolumeControl.Mute_Set)
            DeviceControllerButton.CHANNEL_UP -> return hasCapability(TVControl.Channel_Up)
            DeviceControllerButton.CHANNEL_DOWN -> return hasCapability(TVControl.Channel_Down)
            DeviceControllerButton.UP -> return hasCapability(KeyControl.Up)
            DeviceControllerButton.DOWN -> return hasCapability(KeyControl.Down)
            DeviceControllerButton.LEFT -> return hasCapability(KeyControl.Left)
            DeviceControllerButton.RIGHT -> return hasCapability(KeyControl.Right)
            DeviceControllerButton.OK,
            DeviceControllerButton.OK_LONG_PRESS,
            DeviceControllerButton.OK_LONG_PRESS_END,
                -> return hasCapability(KeyControl.OK)
            DeviceControllerButton.BACK,
            DeviceControllerButton.EXIT,
                -> return hasCapability(KeyControl.Back)

            DeviceControllerButton.BRIGHTNESS_UP,
            DeviceControllerButton.BRIGHTNESS_DOWN,
                -> return hasCapability(KeyControl.Any)

            DeviceControllerButton.HOME -> return hasCapability(KeyControl.Home)
            DeviceControllerButton.INFO -> return hasCapability(KeyControl.Any)
            DeviceControllerButton.SOURCE -> return hasCapability(ExternalInputControl.Any)
            DeviceControllerButton.SCREEN_OFF -> return hasCapability(KeyControl.Any)
            DeviceControllerButton.MENU -> return hasCapability(KeyControl.Any)
            DeviceControllerButton.QMENU -> return hasCapability(KeyControl.Any)
            DeviceControllerButton.RED,
            DeviceControllerButton.GREEN,
            DeviceControllerButton.YELLOW,
            DeviceControllerButton.BLUE,
                -> return hasCapability(KeyControl.Any)
        }
    }

    fun hasCapability(capability: String): Boolean

    fun executeControllerButton(btn: DeviceControllerButton) {
        when (btn) {
            DeviceControllerButton.POWER -> powerOff()
            DeviceControllerButton.VOLUME_UP -> volumeUp()
            DeviceControllerButton.VOLUME_DOWN -> volumeDown()
            DeviceControllerButton.MUTE -> mute()
            DeviceControllerButton.CHANNEL_UP -> channelUp()
            DeviceControllerButton.CHANNEL_DOWN -> channelDown()
            DeviceControllerButton.BRIGHTNESS_UP -> adjustBrightness(BRIGHTNESS_STEP)
            DeviceControllerButton.BRIGHTNESS_DOWN -> adjustBrightness(-BRIGHTNESS_STEP)
            DeviceControllerButton.UP -> up()
            DeviceControllerButton.DOWN -> down()
            DeviceControllerButton.LEFT -> left()
            DeviceControllerButton.RIGHT -> right()
            DeviceControllerButton.OK -> ok()
            DeviceControllerButton.OK_LONG_PRESS -> okLongPress()
            DeviceControllerButton.OK_LONG_PRESS_END -> okLongPressEnd()
            DeviceControllerButton.BACK -> back()
            DeviceControllerButton.EXIT -> exit()
            DeviceControllerButton.HOME -> home()
            DeviceControllerButton.INFO -> info()
            DeviceControllerButton.SOURCE -> source()
            DeviceControllerButton.SCREEN_OFF -> toggleScreen()
            DeviceControllerButton.MENU -> menu()
            DeviceControllerButton.QMENU -> qmenu()
            DeviceControllerButton.RED -> red()
            DeviceControllerButton.GREEN -> green()
            DeviceControllerButton.YELLOW -> yellow()
            DeviceControllerButton.BLUE -> blue()
        }
    }

    fun powerOff()

    fun volumeUp()

    fun volumeDown()

    fun mute()

    fun channelUp()

    fun channelDown()

    /** Changes the screen brightness (backlight) by [delta] percentage points. */
    fun adjustBrightness(delta: Int)

    fun up()

    fun down()

    fun left()

    fun right()

    fun ok()

    /** Long press on OK: runs the action the user picked in the TV settings, or a normal OK. */
    fun okLongPress()

    /** OK was released after a long press. Only matters for the "hold" long press action. */
    fun okLongPressEnd()

    fun back()

    fun exit()

    fun home()

    fun info()

    /** Opens the TV's input picker. */
    fun source()

    /** Turns the screen off (the TV stays on), or back on if it was turned off from here. */
    fun toggleScreen()

    fun menu()

    fun qmenu()

    fun red()

    fun green()

    fun yellow()

    fun blue()

    fun launchNetflix()

    fun mouseClick()

    fun moveMouse(x: Double, y: Double)

    fun scroll(x: Double, y: Double)

    fun launchApp(appId: String)

    fun sendText(text: String)
    fun sendEnter()
    fun sendDelete()

    fun disconnect()
    fun updateStatus(status: DeviceStatus)
}

/** How much one press of a brightness button changes the brightness, in percentage points. */
const val BRIGHTNESS_STEP = 5
