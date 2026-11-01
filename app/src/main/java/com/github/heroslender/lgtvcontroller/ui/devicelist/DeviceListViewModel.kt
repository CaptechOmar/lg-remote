package com.github.heroslender.lgtvcontroller.ui.devicelist

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.github.heroslender.lgtvcontroller.DeviceManager
import com.github.heroslender.lgtvcontroller.device.DeviceStatus
import com.github.heroslender.lgtvcontroller.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceListViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
) : BaseViewModel(deviceManager) {
    val uiState: StateFlow<DeviceListUiState> =
        combine(deviceManager.devices, deviceManager.savedTvs) { devices, savedTvs ->
            val onlineDevices = devices.map { device ->
                DeviceItemData(
                    displayName = if (device.displayName.isNullOrEmpty()) device.friendlyName else device.displayName!!,
                    status = device.status,
                    isPoweredOn = true,
                    connect = device::connect
                )
            }

            // TVs that were connected before but aren't on the network right now (they're off).
            // If we know their MAC address they can be woken up with Wake-on-LAN.
            val discoveredIds = devices.map { it.id }.toSet()
            val offlineDevices = savedTvs
                .filter { it.id !in discoveredIds && !it.macAddress.isNullOrBlank() }
                .map { tv ->
                    DeviceItemData(
                        displayName = if (tv.displayName.isNullOrEmpty()) tv.name else tv.displayName!!,
                        status = mutableStateOf(DeviceStatus.DISCONNECTED),
                        isPoweredOn = false,
                        connect = { deviceManager.wakeTv(tv) }
                    )
                }

            DeviceListUiState(devices = onlineDevices + offlineDevices)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, DeviceListUiState())

    var deviceConnectedListener: () -> Unit = {}

    init {
        viewModelScope.launch {
            deviceManager.connectedDevice.stateIn(viewModelScope).collect {
                if (it != null) {
                    deviceConnectedListener()
                }
            }
        }
    }

    fun onDeviceConnected(onDeviceConnected: () -> Unit = {}) {
        this.deviceConnectedListener = onDeviceConnected
    }
}