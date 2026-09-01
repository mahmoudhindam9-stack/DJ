from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
MIC = ROOT / "app/src/main/java/com/example/player/MicController.kt"


def patch_mic():
    text = MIC.read_text(encoding="utf-8")
    # V5 microphone page: ensure the explicit UI control method exists and
    # cannot collide with the JVM setter generated for the state property.
    if "// KARAOKE_MIC_PAGE_V5" in text or "// KARAOKE_MIC_PAGE_V6" in text:
        text = re.sub(r'(\n\s*)(?:@kotlin\.jvm\.JvmName\([^\n]+\)\n\s*)?fun\s+)setVoiceProcessingEnabled\s*\(', r'\1\2toggleVoiceProcessing(', text, count=1)
        if "fun toggleVoiceProcessing(enabled: Boolean)" not in text:
            needle = '    @SuppressLint("MissingPermission")\n    private fun applyInputRouting()'
            method = '''    fun toggleVoiceProcessing(enabled: Boolean) {\n        voiceProcessingEnabled = enabled\n        try { echoCanceler?.enabled = enabled } catch (_: Throwable) { }\n        try { noiseSuppressor?.enabled = enabled } catch (_: Throwable) { }\n        recordingStatus = if (enabled) "AEC + noise suppression enabled" else "Voice cleanup disabled"\n    }\n\n'''
            if needle not in text: raise SystemExit("MicController routing anchor not found")
            text = text.replace(needle, method + needle, 1)
        MIC.write_text(text, encoding="utf-8")
        return
    if "fun selectInputDevice(device: AudioDeviceInfo?, coroutineScope: CoroutineScope)" not in text:
        needle = '    private fun stopMic() {'
        if needle not in text: raise SystemExit("MicController stopMic anchor not found")
        insert = '''    fun selectInputDevice(device: AudioDeviceInfo?, coroutineScope: CoroutineScope) {\n        selectedInputState = device\n        if (isMicEnabled) { stopMic(); startMic(coroutineScope) }\n        else routingStatus = if (device == null) "Input: System Default Mic" else "Input: ${device.displayName()}"\n    }\n\n    fun selectOutputDevice(device: AudioDeviceInfo?) {\n        selectedOutputState = device\n        if (isMicEnabled) applyOutputRouting() else AudioPlayerController.updateGlobalPreferredAudioDevice(device)\n        routingStatus = if (device == null) "Output: System Default" else "Output: ${device.displayName()}"\n    }\n\n'''
        text = text.replace(needle, insert + needle, 1)
    MIC.write_text(text, encoding="utf-8")


def patch_main():
    text = MAIN.read_text(encoding="utf-8")
    text = text.replace("micController::setVoiceProcessingEnabled", "micController::toggleVoiceProcessing")
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
    old_start = '''            val inputDevice = selectedInputDevice\n            val useBluetoothHfp = inputDevice?.isBluetoothSco() == true\n            val audioSource = if (useBluetoothHfp) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC\n            audioRecord = AudioRecord(audioSource, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) audioRecord?.setPreferredDevice(inputDevice)\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useBluetoothHfp) {\n                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION\n                if (selectedOutputDevice?.id == inputDevice?.id) audioManager.setCommunicationDevice(inputDevice)\n            }\n'''
    new_start = '''            // INDEPENDENT_BT_ROUTING_V1\n            val inputDevice = selectedInputDevice\n            val useBluetoothHfp = inputDevice?.isBluetoothSco() == true\n            val audioSource = if (useBluetoothHfp) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC\n            audioRecord = AudioRecord(audioSource, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) audioRecord?.setPreferredDevice(inputDevice)\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useBluetoothHfp) audioManager.mode = AudioManager.MODE_IN_COMMUNICATION\n'''
    if old_start in text: text = text.replace(old_start, new_start, 1)
    MIC.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    patch_mic(); patch_main(); patch_independent_bluetooth_routing(); print("Audio routing patch applied")
