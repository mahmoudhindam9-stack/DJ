package com.example

import android.media.AudioDeviceInfo

typealias SnapshotStateList<T> = androidx.compose.runtime.snapshots.SnapshotStateList<T>

fun AudioDeviceInfo.displayName(): String {
    val product = productName?.toString()?.trim().orEmpty()
    val fallback = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Microphone"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone Speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Audio"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Headset / Mic"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE Headset"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE Speaker"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset / Mic"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Audio"
        else -> "Audio Device #$id"
    }
    return if (product.isNotEmpty()) product else fallback
}
