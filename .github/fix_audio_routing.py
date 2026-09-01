from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
MIC = ROOT / "app/src/main/java/com/example/player/MicController.kt"


def patch_mic():
    text = MIC.read_text(encoding="utf-8")
    # Keep the public method name used by the generated MicScreen and avoid
    # JVM setter collisions with the voiceProcessingEnabled property.
    if "fun toggleVoiceProcessing(enabled: Boolean)" not in text:
        anchor = '    @SuppressLint("MissingPermission")\n    private fun applyInputRouting()'
        method = '''    fun toggleVoiceProcessing(enabled: Boolean) {\n        voiceProcessingEnabled = enabled\n        try { echoCanceler?.enabled = enabled } catch (_: Throwable) { }\n        try { noiseSuppressor?.enabled = enabled } catch (_: Throwable) { }\n        recordingStatus = if (enabled) "AEC + noise suppression enabled" else "Voice cleanup disabled"\n    }\n\n'''
        if anchor not in text: raise SystemExit("MicController routing anchor not found")
        text = text.replace(anchor, method + anchor, 1)
    # Do not rename setVoiceProcessingEnabled; the public property setter is generated
    # with the same JVM name. MainActivity is normalized to call toggleVoiceProcessing.
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
    if marker not in text:
        old_start = '''            val inputDevice = selectedInputDevice\n            val useBluetoothHfp = inputDevice?.isBluetoothSco() == true\n            val audioSource = if (useBluetoothHfp) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC\n            audioRecord = AudioRecord(audioSource, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) audioRecord?.setPreferredDevice(inputDevice)\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useBluetoothHfp) {\n                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION\n                if (selectedOutputDevice?.id == inputDevice?.id) audioManager.setCommunicationDevice(inputDevice)\n            }\n'''
        new_start = '''            // INDEPENDENT_BT_ROUTING_V1\n            val inputDevice = selectedInputDevice\n            val useBluetoothHfp = inputDevice?.isBluetoothSco() == true\n            val audioSource = if (useBluetoothHfp) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC\n            audioRecord = AudioRecord(audioSource, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) audioRecord?.setPreferredDevice(inputDevice)\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useBluetoothHfp) audioManager.mode = AudioManager.MODE_IN_COMMUNICATION\n'''
        if old_start in text: text = text.replace(old_start, new_start, 1)
    MIC.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    patch_mic(); patch_main(); patch_independent_bluetooth_routing(); print("Audio routing patch applied")
