package com.github.heroslender.lgtvcontroller.ui.home

import com.github.heroslender.lgtvcontroller.device.DeviceControllerButton
import com.github.heroslender.lgtvcontroller.device.DeviceStatus
import com.github.heroslender.lgtvcontroller.domain.model.App
import com.github.heroslender.lgtvcontroller.domain.model.Input

data class HomeUiState(
    /** False until the saved tvs were read, so an empty state is not mistaken for "no tv". */
    val loaded: Boolean = false,
    /** True if there is a tv to show (connected or not). */
    val hasTv: Boolean = false,
    /** True while there is a connection object for the tv (connecting or connected). */
    val isConnected: Boolean = false,
    val deviceID: String? = null,
    val deviceName: String? = null,
    val deviceStatus: DeviceStatus = DeviceStatus.DISCONNECTED,
    val apps: List<App> = emptyList(),
    val inputs: List<Input> = emptyList(),
    val runningApp: String = "",
    val hasCapability: (DeviceControllerButton) -> Boolean = { false },
    val executeButton: (DeviceControllerButton) -> Unit = {},
    val launchApp: (String) -> Unit = {},
)