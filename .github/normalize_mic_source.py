from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MIC = ROOT / "app/src/main/java/com/example/player/MicController.kt"
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"


def remove_duplicate_toggle_functions(text: str) -> str:
    pattern = re.compile(r'\n    fun toggleVoiceProcessing\(enabled: Boolean\) \{.*?\n    \}\n', re.S)
    body = '''\n    fun toggleVoiceProcessing(enabled: Boolean) {\n        voiceProcessingEnabled = enabled\n        try { echoCanceler?.enabled = enabled } catch (_: Throwable) { }\n        try { noiseSuppressor?.enabled = enabled } catch (_: Throwable) { }\n        recordingStatus = if (enabled) "AEC + noise suppression enabled" else "Voice cleanup disabled"\n    }\n'''
    text = pattern.sub("\n", text)
    anchor = '    private fun startMicForegroundService() {'
    if body.strip() not in text:
        if anchor not in text:
            raise SystemExit("startMicForegroundService anchor not found")
        text = text.replace(anchor, body + "\n" + anchor, 1)
    return text


def normalize_stop_mic(text: str) -> str:
    pair = ('        if (isOutputRecording) stopOutputRecording()\n'
            '        stopMicForegroundService()\n')
    return re.sub(r'(?:' + re.escape(pair) + r'){2,}', pair, text)


def normalize_beat_fx_controller(text: str) -> str:
    text = text.replace(
        '    var beatFxEnabled by mutableStateOf(false)\n',
        '    var beatFxEnabled by mutableStateOf(true)\n',
        1,
    )
    return text


def normalize_main(text: str) -> str:
    text = text.replace('                Spacer(Modifier.height(10.dp))\n                Spacer(Modifier.height(10.dp))\n', '                Spacer(Modifier.height(10.dp))\n', 1)

    old = '''        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {\n            Column(Modifier.padding(14.dp)) {\n                Text("Beat FX", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)\n                Spacer(Modifier.height(6.dp))\n                Text("BPM: ${micController.bpm.toInt()}")\n                Slider(micController.bpm, { micController.bpm = it }, valueRange = 70f..180f)\n                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {\n                    items(BeatFxDivision.values().toList()) { div -> FilterChip(div == micController.beatFxDivision, { micController.beatFxDivision = div }, label = { Text(div.displayName) }) }\n                }\n            }\n        }'''
    new = '''        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {\n            Column(Modifier.padding(14.dp)) {\n                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {\n                    Column(Modifier.weight(1f)) {\n                        Text("Beat FX", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)\n                        Text(if (micController.beatFxEnabled) "ACTIVE • synced to BPM" else "BYPASSED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n                    }\n                    Switch(checked = micController.beatFxEnabled, onCheckedChange = { micController.beatFxEnabled = it })\n                }\n                Spacer(Modifier.height(6.dp))\n                Text("BPM: ${micController.bpm.toInt()}")\n                Slider(micController.bpm, { micController.bpm = it }, valueRange = 70f..180f)\n                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {\n                    items(BeatFxDivision.values().toList()) { div -> FilterChip(div == micController.beatFxDivision, { micController.beatFxDivision = div }, label = { Text(div.displayName) }) }\n                }\n            }\n        }'''
    if old not in text:
        raise SystemExit("Beat FX UI block not found")
    return text.replace(old, new, 1)


def main():
    mic = MIC.read_text(encoding="utf-8")
    main_text = MAIN.read_text(encoding="utf-8")
    mic = remove_duplicate_toggle_functions(mic)
    mic = normalize_stop_mic(mic)
    mic = normalize_beat_fx_controller(mic)
    main_text = normalize_main(main_text)
    MIC.write_text(mic, encoding="utf-8")
    MAIN.write_text(main_text, encoding="utf-8")
    print("Microphone source normalized idempotently: Beat FX + stopMic cleanup")


if __name__ == "__main__":
    main()
