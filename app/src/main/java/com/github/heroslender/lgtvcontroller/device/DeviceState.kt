package com.github.heroslender.lgtvcontroller.device

import com.github.heroslender.lgtvcontroller.domain.model.App
import com.github.heroslender.lgtvcontroller.domain.model.Input

data class DeviceState(
    val displayName: String?,
    val status: DeviceStatus,
    val runningApp: String,
    val apps: List<App>,
    val inputs: List<Input>,
    val isKeyboardOpen: Boolean,
    val brightnessControls: Boolean = false,
    /** Last known TV volume and backlight (0-100), null when not known yet. */
    val volume: Int? = null,
    val brightness: Int? = null,
)