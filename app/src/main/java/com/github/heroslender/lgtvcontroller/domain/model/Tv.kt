package com.github.heroslender.lgtvcontroller.domain.model

data class Tv(
    val id: String,
    val name: String = "",
    var displayName: String? = "",
    var autoConnect: Boolean = false,
    var apps: List<App> = emptyList(),
    var inputs: List<Input> = emptyList(),
    /** MAC address used to wake the TV with Wake-on-LAN, in `AA:BB:CC:DD:EE:FF` form. */
    var macAddress: String? = null,
    /** App launched by the Home button instead of the TV's home screen, null for the default. */
    var homeAppId: String? = null,
    /** Name of the [com.github.heroslender.lgtvcontroller.device.DeviceControllerButton] sent when OK is long-pressed, null for a normal OK. */
    var okLongPressAction: String? = null,
    /** Last known IPv4 address of the TV, used to reach it without waiting for discovery. */
    var ipAddress: String? = null,
    /** UUID the TV announced itself with, needed to rebuild the device without discovery. */
    var serviceUuid: String? = null,
    /** Key the TV gave us when it was paired, lets us reconnect without asking again. */
    var clientKey: String? = null,
)