from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
MIC = ROOT / "app/src/main/java/com/example/player/MicController.kt"


def patch_mic():
    text = MIC.read_text(encoding="utf-8")
    if "fun selectInputDevice(device: AudioDeviceInfo?, coroutineScope: CoroutineScope)" not in text:
        needle = '    private fun stopMic() {'
        if needle not in text:
            raise SystemExit("MicController stopMic anchor not found")
        insert = '''    /** Apply an input selection immediately. If live monitoring is active, the recorder is restarted\n     * so Android gets a fresh preferred input route instead of keeping the previous device. */\n    fun selectInputDevice(device: AudioDeviceInfo?, coroutineScope: CoroutineScope) {\n        selectedInputState = device\n        if (isMicEnabled) {\n            stopMic()\n            startMic(coroutineScope)\n        } else {\n            routingStatus = if (device == null) "Input: System Default Mic" else "Input: ${device.displayName()}"\n        }\n    }\n\n    /** Apply an output selection to both the live mic monitor and the Media3 music player. */\n    fun selectOutputDevice(device: AudioDeviceInfo?) {\n        selectedOutputState = device\n        if (isMicEnabled) applyOutputRouting() else AudioPlayerController.updateGlobalPreferredAudioDevice(device)\n        routingStatus = if (device == null) "Output: System Default" else "Output: ${device.displayName()}"\n    }\n\n'''
        text = text.replace(needle, insert + needle, 1)
    MIC.write_text(text, encoding="utf-8")


def patch_main():
    text = MAIN.read_text(encoding="utf-8")
    replacements = {
        'micController.selectedInputDevice = null; inputExpanded = false': 'micController.selectInputDevice(null, scope); inputExpanded = false',
        'micController.selectedInputDevice = device; inputExpanded = false': 'micController.selectInputDevice(device, scope); inputExpanded = false',
        'micController.selectedOutputDevice = null; outputExpanded = false': 'micController.selectOutputDevice(null); outputExpanded = false',
        'micController.selectedOutputDevice = device; outputExpanded = false': 'micController.selectOutputDevice(device); outputExpanded = false',
    }
    for old, new in replacements.items():
        text = text.replace(old, new)
    MAIN.write_text(text, encoding="utf-8")


def patch_independent_bluetooth_routing():
    text = MIC.read_text(encoding="utf-8")
    marker = "// INDEPENDENT_BT_ROUTING_V1"
    if marker in text:
        return

    old_start = '''            val inputDevice = selectedInputDevice\n            val useBluetoothHfp = inputDevice?.isBluetoothSco() == true\n            val audioSource = if (useBluetoothHfp) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC\n            audioRecord = AudioRecord(audioSource, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) audioRecord?.setPreferredDevice(inputDevice)\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useBluetoothHfp) {\n                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION\n                if (selectedOutputDevice?.id == inputDevice?.id) audioManager.setCommunicationDevice(inputDevice)\n            }\n'''
    new_start = '''            // INDEPENDENT_BT_ROUTING_V1\n            val inputDevice = selectedInputDevice\n            val useBluetoothHfp = inputDevice?.isBluetoothSco() == true\n            // SCO/HFP input needs communication mode, but we deliberately do NOT call\n            // setCommunicationDevice() here because that API selects a matching output too.\n            // AudioRecord.setPreferredDevice() and AudioTrack.setPreferredDevice() are used\n            // independently so Bluetooth input and Bluetooth output can be selected separately\n            // whenever the Android device/audio stack supports that combination.\n            val audioSource = if (useBluetoothHfp) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC\n            audioRecord = AudioRecord(audioSource, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {\n                val accepted = audioRecord?.setPreferredDevice(inputDevice) ?: false\n                if (inputDevice != null && !accepted) {\n                    routingStatus = "تعذر اختيار مدخل الصوت: ${inputDevice.displayName()}"\n                }\n            }\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useBluetoothHfp) {\n                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION\n            }\n'''
    if old_start not in text:
        raise SystemExit("startMic routing block not found")
    text = text.replace(old_start, new_start, 1)

    old_input = '''            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device?.isBluetoothSco() == true) {\n                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION\n                if (selectedOutputDevice?.id == device.id) {\n                    audioManager.setCommunicationDevice(device)\n                }\n            }\n            updateRoutingStatus()\n'''
    new_input = '''            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device?.isBluetoothSco() == true) {\n                // Keep communication mode for SCO/HFP capture, but do not bind the output\n                // to this same device. The output is routed independently below.\n                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION\n            }\n            applyOutputRouting()\n            updateRoutingStatus()\n'''
    if old_input not in text:
        raise SystemExit("applyInputRouting Bluetooth block not found")
    text = text.replace(old_input, new_input, 1)

    old_output = '''            AudioPlayerController.updateGlobalPreferredAudioDevice(selectedOutputDevice)\n\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {\n                val input = selectedInputDevice\n                if (input?.isBluetoothSco() == true && selectedOutputDevice?.id == input.id) {\n                    audioManager.setCommunicationDevice(input)\n                } else if (selectedOutputDevice == null && audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) {\n                    audioManager.clearCommunicationDevice()\n                }\n            }\n            updateRoutingStatus()\n'''
    new_output = '''            AudioPlayerController.updateGlobalPreferredAudioDevice(selectedOutputDevice)\n\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {\n                val inputNeedsCommunicationMode = selectedInputDevice?.isBluetoothSco() == true\n                if (inputNeedsCommunicationMode) {\n                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION\n                } else if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) {\n                    audioManager.mode = AudioManager.MODE_NORMAL\n                    // Do not call clearCommunicationDevice(): this implementation no longer\n                    // uses a global communication-device lock for independent routing.\n                }\n            }\n            updateRoutingStatus()\n'''
    if old_output not in text:
        raise SystemExit("applyOutputRouting communication-device block not found")
    text = text.replace(old_output, new_output, 1)

    old_stop = '''        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {\n            try { audioManager.clearCommunicationDevice() } catch (_: Throwable) { }\n        }\n        audioManager.mode = AudioManager.MODE_NORMAL\n'''
    new_stop = '''        // We do not own a global communication-device selection anymore; only reset the\n        // communication mode used to make SCO/HFP capture available.\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {\n            audioManager.mode = AudioManager.MODE_NORMAL\n        } else {\n            audioManager.mode = AudioManager.MODE_NORMAL\n        }\n'''
    if old_stop not in text:
        raise SystemExit("stopMic communication-device block not found")
    text = text.replace(old_stop, new_stop, 1)

    status_anchor = '''        val inputName = actualInput?.displayName() ?: selectedInputDevice?.displayName() ?: "تلقائي"\n        val outputName = actualOutput?.displayName() ?: selectedOutputDevice?.displayName() ?: "تلقائي"\n        routingStatus = "الإدخال: $inputName  •  الإخراج: $outputName"\n'''
    status_replacement = '''        val inputName = actualInput?.displayName() ?: selectedInputDevice?.displayName() ?: "تلقائي"\n        val outputName = actualOutput?.displayName() ?: selectedOutputDevice?.displayName() ?: "تلقائي"\n        val independent = selectedInputDevice != null && selectedOutputDevice != null && selectedInputDevice?.id != selectedOutputDevice?.id\n        val suffix = if (independent) "  •  مستقل" else ""\n        routingStatus = "الإدخال: $inputName  •  الإخراج: $outputName$suffix"\n'''
    if status_anchor in text:
        text = text.replace(status_anchor, status_replacement, 1)

    MIC.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    patch_mic()
    patch_main()
    patch_independent_bluetooth_routing()
    print("Audio routing patch applied")
