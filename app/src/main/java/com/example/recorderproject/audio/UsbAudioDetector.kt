package com.example.recorderproject.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Detects all external audio input devices: USB-C mics, Bluetooth headsets, wired headsets,
 * and BLE audio devices (API 31+). Exposes a live-updating flow for the mic picker UI.
 * Routes AudioRecord.setPreferredDevice() to the chosen device for true hardware routing.
 */
class UsbAudioDetector(context: Context) {

    enum class DeviceCategory { USB, BLUETOOTH, WIRED, BLE }

    data class UsbDevice(
        val id: Int,
        val productName: String,
        val sampleRates: IntArray,
        val channelCounts: IntArray,
        val category: DeviceCategory = DeviceCategory.USB,
    )

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _devices = MutableStateFlow<List<UsbDevice>>(emptyList())
    val devices: StateFlow<List<UsbDevice>> = _devices

    private val callback = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
    }

    fun start() {
        refresh()
        audioManager.registerAudioDeviceCallback(callback, null)
    }

    fun stop() {
        audioManager.unregisterAudioDeviceCallback(callback)
    }

    fun preferredDeviceById(id: Int): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == id }

    private fun refresh() {
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val found = mutableListOf<UsbDevice>()

        for (dev in inputs) {
            val category = when (dev.type) {
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_ACCESSORY -> DeviceCategory.USB

                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> DeviceCategory.BLUETOOTH

                AudioDeviceInfo.TYPE_WIRED_HEADSET -> DeviceCategory.WIRED

                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        (dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                         dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
                    ) DeviceCategory.BLE else null
                }
            } ?: continue

            val name = when {
                !dev.productName.isNullOrBlank() -> dev.productName.toString()
                category == DeviceCategory.BLUETOOTH -> "Bluetooth Headset"
                category == DeviceCategory.WIRED -> "Wired Headset"
                category == DeviceCategory.BLE -> "BLE Audio Device"
                else -> "USB Audio"
            }
            found += UsbDevice(
                id = dev.id,
                productName = name,
                sampleRates = dev.sampleRates ?: intArrayOf(),
                channelCounts = dev.channelCounts ?: intArrayOf(),
                category = category,
            )
        }
        _devices.value = found
    }
}
