from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MIC = ROOT / "app/src/main/java/com/example/player/MicController.kt"
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"

NEW_REFRESH = r'''    @SuppressLint("MissingPermission")
    fun refreshDevices() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                routingStatus = "Allow Bluetooth access, then refresh devices"
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val allInputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
                val allOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
                inputDevices = allInputs.filter { it.isSupportedInputDevice() }.distinctBy { it.id }
                    .sortedWith(compareBy({ it.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO }, { it.displayName() }))
                outputDevices = allOutputs.filter { it.isSupportedOutputDevice() }.distinctBy { it.id }
                    .sortedWith(compareBy({ !it.isBluetoothOutputDevice() }, { it.displayName() }))
                routingStatus = when {
                    inputDevices.isEmpty() && outputDevices.isEmpty() -> "No supported audio devices detected"
                    else -> "${inputDevices.size} input device(s) • ${outputDevices.size} output device(s)"
                }
            }
        } catch (t: Throwable) {
            routingStatus = "Unable to read audio devices: ${t.message ?: "Unknown error"}"
        }
    }

'''

HELPERS = r'''private fun AudioDeviceInfo.isSupportedInputDevice(): Boolean {
    return when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC, AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> isSource
        else -> isSource && !isSink
    }
}

private fun AudioDeviceInfo.isSupportedOutputDevice(): Boolean {
    return when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> isSink
        else -> isSink && !isSource
    }
}

private fun AudioDeviceInfo.isBluetoothOutputDevice(): Boolean =
    type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
    type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER

private fun AudioDeviceInfo.displayName(): String {
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

'''

def patch_mic():
    text = MIC.read_text(encoding="utf-8")
    start = text.find('    @SuppressLint("MissingPermission")\n    fun refreshDevices() {')
    if start < 0: raise SystemExit("refreshDevices function not found")
    end = text.find('    @SuppressLint("MissingPermission")\n    fun toggleMic', start)
    if end < 0: raise SystemExit("toggleMic anchor not found")
    text = text[:start] + NEW_REFRESH + text[end:]
    if 'private fun AudioDeviceInfo.isSupportedInputDevice()' not in text:
        marker = '\nclass MicController(private val context: Context) {'
        text = text.replace(marker, '\n' + HELPERS + 'class MicController(private val context: Context) {', 1)
    MIC.write_text(text, encoding="utf-8")

def patch_main():
    text = MAIN.read_text(encoding="utf-8")
    text = text.replace('Text(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) micController.selectedInputDevice?.productName?.toString() ?: "System Default Mic" else "System Default Mic", maxLines = 1)', 'Text(micController.selectedInputDevice?.displayName() ?: "System Default Mic", maxLines = 1)', 1)
    text = text.replace('Text(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) micController.selectedOutputDevice?.productName?.toString() ?: "System Default Output" else "System Default Output", maxLines = 1)', 'Text(micController.selectedOutputDevice?.displayName() ?: "System Default Output", maxLines = 1)', 1)
    MAIN.write_text(text, encoding="utf-8")

if __name__ == "__main__":
    patch_mic()
    patch_main()
    print("Device routing classification upgrade applied")
