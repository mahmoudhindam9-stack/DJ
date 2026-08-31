from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ORG = ROOT / "app/src/main/java/com/example/org/OrgController.kt"
SCREEN = ROOT / "app/src/main/java/com/example/org/OrgScreen.kt"

text = ORG.read_text(encoding="utf-8")
# Compose-observable state for all values displayed or changed by OrgScreen.
if "androidx.compose.runtime.mutableStateOf" not in text:
    text = text.replace("import androidx.annotation.RequiresApi\n", "import androidx.annotation.RequiresApi\nimport androidx.compose.runtime.getValue\nimport androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.setValue\n")

repls = {
'''    var rhythm: Rhythm = rhythms.first()\n        private set\n    var bpm: Int = rhythm.bpm\n        private set\n    var volume: Float = 0.8f\n    var voiceIndex: Int = 0\n    var accompanimentEnabled: Boolean = false\n    var rhythmEnabled: Boolean = false\n    var effectIndex: Int = 0\n''': '''    var rhythm: Rhythm by mutableStateOf(rhythms.first())\n        private set\n    var bpm: Int by mutableStateOf(rhythm.bpm)\n        private set\n    var volume: Float by mutableStateOf(0.8f)\n    var voiceIndex: Int by mutableStateOf(0)\n    var accompanimentEnabled: Boolean by mutableStateOf(false)\n    var rhythmEnabled: Boolean by mutableStateOf(false)\n    var effectIndex: Int by mutableStateOf(0)\n''',
'''    fun setBpm(value: Int) {\n        bpm = value.coerceIn(50, 180)\n    }\n''': '''    fun updateBpm(value: Int) {\n        bpm = value.coerceIn(50, 180)\n    }\n''',
'''    private fun playTone(freq: Double, duration: Double, amp: Float, harmonic: Double, effect: Int) {\n        val old = effectIndex\n        effectIndex = effect.coerceIn(effects.indices)\n        playVoice(Voice("Pad", "Pad", freq, harmonic, Character.SYNTH), amp, duration)\n        effectIndex = old\n    }\n''': '''    private fun playTone(freq: Double, duration: Double, amp: Float, harmonic: Double, effect: Int) {\n        val selectedEffect = effect.coerceIn(effects.indices)\n        Thread {\n            val sr = 44100\n            val count = max(1, (sr * duration).toInt())\n            val data = ShortArray(count)\n            for (i in data.indices) {\n                val t = i.toDouble() / sr\n                val p = i.toDouble() / count\n                val env = if (p < 0.015) p / 0.015 else exp(-p * 4.0)\n                val wave = sin(2.0 * PI * freq * t) + harmonic * sin(2.0 * PI * freq * 2.0 * t)\n                val effected = when (selectedEffect) {\n                    0 -> wave\n                    1 -> wave * (0.88 + 0.12 * sin(2.0 * PI * 2.3 * t))\n                    2 -> wave + wave * 0.30 * sin(2.0 * PI * 3.7 * t)\n                    3 -> wave + wave * 0.22 * sin(2.0 * PI * 0.7 * t)\n                    4 -> wave + wave * 0.18 * sin(2.0 * PI * 5.0 * t)\n                    5 -> tanh(wave * 1.7)\n                    6 -> wave * (0.70 + 0.30 * (0.5 + 0.5 * sin(2.0 * PI * 5.0 * t)))\n                    else -> wave + 0.35 * sin(2.0 * PI * 2.0 * t)\n                }\n                data[i] = (effected * 10500.0 * amp * env).toInt()\n                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()\n            }\n            playPcm(data, sr)\n        }.start()\n    }\n'''
}
for old, new in repls.items():
    if old not in text:
        raise SystemExit("Expected ORG controller block not found")
    text = text.replace(old, new, 1)

text = text.replace('''    var isRecording: Boolean = false\n        private set\n    var lastFile: File? = null\n        private set\n''', '''    var isRecording: Boolean by mutableStateOf(false)\n        private set\n    var lastFile: File? by mutableStateOf(null)\n        private set\n''', 1)
ORG.write_text(text, encoding="utf-8")

screen = SCREEN.read_text(encoding="utf-8")
screen = screen.replace('engine.setBpm(it.toInt())', 'engine.updateBpm(it.toInt())')
if "ORG_REFRESH_V1" not in screen:
    anchor = '        Spacer(Modifier.height(12.dp))\n\n        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {'
    replacement = '''        // ORG_REFRESH_V1\n        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n            OutlinedButton(onClick = {\n                engine.stopRhythm()\n                engine.startRhythm(scope)\n            }, modifier = Modifier.weight(1f)) { Text("Restart Engine") }\n            OutlinedButton(onClick = {\n                engine.stopRhythm()\n                accompaniment = false\n                engine.accompanimentEnabled = false\n            }, modifier = Modifier.weight(1f)) { Text("Reset Rhythm") }\n        }\n        Spacer(Modifier.height(12.dp))\n\n        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {'''
    if anchor not in screen:
        raise SystemExit("ORG screen header anchor not found")
    screen = screen.replace(anchor, replacement, 1)
SCREEN.write_text(screen, encoding="utf-8")
print("ORG state/playback fixes applied; BPM setter clash resolved")
