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


if __name__ == "__main__":
    patch_mic()
    patch_main()
    print("Audio routing patch applied")
