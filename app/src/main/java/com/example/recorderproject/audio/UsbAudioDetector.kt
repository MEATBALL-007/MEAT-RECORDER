package com.example.recorderproject.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Phase 3 feature — USB-C external mic detection (VM40, Røde, Saramonic, DJI Mic, Shure MV7, etc.).
 *
 * Enumerates UAC-compliant USB audio inputs via `AudioManager.getDevices(GET_DEVICES_INPUTS)` and
 * exposes a flow that the ViewModel observes. Hook this into the audio source picker; route
 * `AudioRecord.setPreferredDevice()` to the chosen device.
 */
class UsbAudioDetector(context: Context) {

    data class UsbDevice(
        val id: Int,
        val productName: String,
        val sampleRates: IntArray,
        val channelCounts: IntArray,
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
        val usb = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY }
            .map {
                UsbDevice(
                    id = it.id,
                    productName = it.productName?.toString() ?: "USB Audio",
                    sampleRates = it.sampleRates ?: intArrayOf(),
                    channelCounts = it.channelCounts ?: intArrayOf(),
                )
            }
        _devices.value = usb
    }
}
