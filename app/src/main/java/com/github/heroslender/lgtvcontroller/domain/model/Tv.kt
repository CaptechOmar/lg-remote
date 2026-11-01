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
)