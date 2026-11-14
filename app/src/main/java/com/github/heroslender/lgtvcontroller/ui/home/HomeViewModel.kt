package com.github.heroslender.lgtvcontroller.ui.home

import androidx.lifecycle.viewModelScope
import com.github.heroslender.lgtvcontroller.DeviceManager
import com.github.heroslender.lgtvcontroller.LastTv
import com.github.heroslender.lgtvcontroller.device.DeviceControllerButton
import com.github.heroslender.lgtvcontroller.device.DeviceStatus
import com.github.heroslender.lgtvcontroller.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
) : BaseViewModel(deviceManager) {
    val uiState: StateFlow<HomeUiState> =
        deviceManager.lastTv.flatMapLatest { last ->
            deviceManager.connectedDevice.flatMapLatest { device ->
                if (device == null) {
                    return@flatMapLatest flowOf(offlineState(last))
                }

                device.state.map { deviceState ->
                    HomeUiState(
                        loaded = true,
                        hasTv = true,
                        isConnected = true,
                        deviceID = device.id,
                        deviceName = if (deviceState.displayName.isNullOrEmpty()) device.friendlyName else deviceState.displayName,
                        deviceStatus = deviceState.status,
                        runningApp = deviceState.runningApp,
                        apps = deviceState.apps,
                        inputs = deviceState.inputs,
                        hasCapability = device::hasCapability,
                        executeButton = device::executeControllerButton,
                        launchApp = device::launchApp,
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    /**
     * The last used tv while there is no connection to it (it is off, or still being reached): its
     * remote is shown disabled, except the power button, which wakes it up when its MAC is known.
     */
    private fun offlineState(last: LastTv): HomeUiState {
        val tv = last.tv ?: return HomeUiState(loaded = last.loaded, hasTv = false)
        val canWake = !tv.macAddress.isNullOrBlank()

        return HomeUiState(
            loaded = true,
            hasTv = true,
            isConnected = false,
            deviceID = tv.id,
            deviceName = if (tv.displayName.isNullOrEmpty()) tv.name else tv.displayName,
            deviceStatus = DeviceStatus.DISCONNECTED,
            apps = tv.apps,
            inputs = tv.inputs,
            hasCapability = { it == DeviceControllerButton.POWER && canWake },
            executeButton = { button ->
                if (button == DeviceControllerButton.POWER) deviceManager.wakeTv(tv)
            },
        )
    }
}
