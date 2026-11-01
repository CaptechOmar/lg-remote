package com.github.heroslender.lgtvcontroller.device

/**
 * Value of the "OK button long press" setting that holds the pointer button down for as long as
 * OK is held, instead of sending another key. Experimental.
 */
const val OK_LONG_PRESS_HOLD = "HOLD"

enum class DeviceControllerButton {
    POWER,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
    CHANNEL_UP,
    CHANNEL_DOWN,
    UP,
    DOWN,
    LEFT,
    RIGHT,
    OK,
    OK_LONG_PRESS,
    OK_LONG_PRESS_END,
    BACK,
    EXIT,
    HOME,
    INFO,
    SOURCE,
    MENU,
    QMENU,
    RED,
    GREEN,
    YELLOW,
    BLUE,
}