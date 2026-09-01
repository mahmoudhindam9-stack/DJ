from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
MIC = ROOT / "app/src/main/java/com/example/player/MicController.kt"


def patch_mic():
    text = MIC.read_text(encoding="utf-8")
    if "// KARAOKE_MIC_PAGE_V5" in text or "// KARAOKE_MIC_PAGE_V6" in text:
        # Kotlin properties named voiceProcessingEnabled generate setVoiceProcessingEnabled(Boolean).
        # Rename the explicit control method so it cannot collide with that generated setter.
        text = text.replace("fun setVoiceProcessingEnabled(enabled: Boolean)", "fun toggleVoiceProcessing(enabled: Boolean)", 1)
        MIC.write_text(text, encoding="utf-8")
        return
    if "fun selectInputDevice(device: AudioDeviceInfo?, coroutineScope: CoroutineScope)" not in text:
        needle = '    private fun stopMic() {'
        if needle not in text: raise SystemExit("MicController stopMic anchor not found")
        insert = '''    fun selectInputDevice(device: AudioDeviceInfo?, coroutineScope: CoroutineScope) {
        selectedInputState = device
        if (isMicEnabled) { stopMic(); startMic(coroutineScope) }
        else routingStatus = if (device == null) "Input: System Default Mic" else "Input: ${device.displayName()}"
    }

    fun selectOutputDevice(device: AudioDeviceInfo?) {
        selectedOutputState = device
        if (isMicEnabled) applyOutputRouting() else AudioPlayerController.updateGlobalPreferredAudioDevice(device)
        routingStatus = if (device == null) "Output: System Default" else "Output: ${device.displayName()}"
    }

'''
        text = text.replace(needle, insert + needle, 1)
    MIC.write_text(text, encoding="utf-8")


def patch_main():
    text = MAIN.read_text(encoding="utf-8")
    text = text.replace("micController::setVoiceProcessingEnabled", "micController::toggleVoiceProcessing", 1)
    replacements = {
        'micController.selectedInputDevice = null; inputExpanded = false': 'micController.selectInputDevice(null, scope); inputExpanded = false',
        'micController.selectedInputDevice = device; inputExpanded = false': 'micController.selectInputDevice(device, scope); inputExpanded = false',
        'micController.selectedOutputDevice = null; outputExpanded = false': 'micController.selectOutputDevice(null); outputExpanded = false',
        'micController.selectedOutputDevice = device; outputExpanded = false': 'micController.selectOutputDevice(device); outputExpanded = false',
    }
    for old, new in replacements.items(): text = text.replace(old, new)
    MAIN.write_text(text, encoding="utf-8")


def patch_independent_bluetooth_routing():
    text = MIC.read_text(encoding="utf-8")
    marker = "// INDEPENDENT_BT_ROUTING_V1"
    if marker in text or "// KARAOKE_MIC_PAGE_V5" in text or "// KARAOKE_MIC_PAGE_V6" in text:
        return
    old_start = '''            val inputDevice = selectedInputDevice
            val useBluetoothHfp = inputDevice?.isBluetoothSco() == true
            val audioSource = if (useBluetoothHfp) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC
            audioRecord = AudioRecord(audioSource, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) audioRecord?.setPreferredDevice(inputDevice)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useBluetoothHfp) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                if (selectedOutputDevice?.id == inputDevice?.id) audioManager.setCommunicationDevice(inputDevice)
            }
'''
    new_start = '''            // INDEPENDENT_BT_ROUTING_V1
            val inputDevice = selectedInputDevice
            val useBluetoothHfp = inputDevice?.isBluetoothSco() == true
            val audioSource = if (useBluetoothHfp) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC
            audioRecord = AudioRecord(audioSource, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) audioRecord?.setPreferredDevice(inputDevice)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useBluetoothHfp) audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
'''
    if old_start in text: text = text.replace(old_start, new_start, 1)
    MIC.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    patch_mic()
    patch_main()
    patch_independent_bluetooth_routing()
    print("Audio routing patch applied")
