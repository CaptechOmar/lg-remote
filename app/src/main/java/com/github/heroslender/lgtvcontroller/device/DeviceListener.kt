package com.github.heroslender.lgtvcontroller.device

import com.github.heroslender.lgtvcontroller.utils.AppLog
import com.connectsdk.device.ConnectableDevice
import com.connectsdk.device.ConnectableDeviceListener
import com.connectsdk.service.DeviceService
import com.connectsdk.service.command.ServiceCommandError
import com.github.heroslender.lgtvcontroller.DeviceManager

class DeviceListener(
    private val manager: DeviceManager,
    private val device: Device,
) : ConnectableDeviceListener {

    override fun onDeviceReady(d: ConnectableDevice) {
        device.updateStatus(DeviceStatus.CONNECTED)
        manager.onDeviceConnected(device)
        AppLog.d("Device", "listDevices :::: onDeviceReady")
    }

    override fun onDeviceDisconnected(d: ConnectableDevice) {
        AppLog.d("Device", "listDevices :::: onDeviceDisconnected")
        d.removeListener(this)
        manager.onDeviceDisconnected(device)
        device.updateStatus(DeviceStatus.DISCONNECTED)
    }

    override fun onPairingRequired(
        d: ConnectableDevice,
        service: DeviceService,
        pairingType: DeviceService.PairingType
    ) {
        AppLog.d("Device", "listDevices :::: onPairingRequired")
        manager.onDevicePairing(device)
        device.updateStatus(DeviceStatus.PAIRING)
    }

    override fun onCapabilityUpdated(
        d: ConnectableDevice,
        added: MutableList<String>,
        removed: MutableList<String>
    ) {
        AppLog.d("Device", "listDevices :::: onCapabilityUpdated")
    }

    override fun onConnectionFailed(
        d: ConnectableDevice,
        error: ServiceCommandError
    ) {
        AppLog.e("Device", "Connection failed: ${error.message}")
        d.removeListener(this)
        device.updateStatus(DeviceStatus.DISCONNECTED)
        manager.onDeviceDisconnected(device)
    }
}