from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ORG = ROOT / "app/src/main/java/com/example/org/OrgController.kt"
SCREEN = ROOT / "app/src/main/java/com/example/org/OrgScreen.kt"

text = ORG.read_text(encoding="utf-8")

# Ensure Compose state imports exist exactly once.
if "import androidx.compose.runtime.mutableStateOf" not in text:
    text = text.replace(
        "import androidx.annotation.RequiresApi\n",
        "import androidx.annotation.RequiresApi\nimport androidx.compose.runtime.getValue\nimport androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.setValue\n",
        1,
    )

old_state = '''    var rhythm: Rhythm = rhythms.first()\n        private set\n    var bpm: Int = rhythm.bpm\n        private set\n    var volume: Float = 0.8f\n    var voiceIndex: Int = 0\n    var accompanimentEnabled: Boolean = false\n    var rhythmEnabled: Boolean = false\n    var effectIndex: Int = 0\n'''
new_state = '''    var rhythm: Rhythm by mutableStateOf(rhythms.first())\n        private set\n    var bpm: Int by mutableStateOf(rhythm.bpm)\n        private set\n    var volume: Float by mutableStateOf(0.8f)\n    var voiceIndex: Int by mutableStateOf(0)\n    var accompanimentEnabled: Boolean by mutableStateOf(false)\n    var rhythmEnabled: Boolean by mutableStateOf(false)\n    var effectIndex: Int by mutableStateOf(0)\n'''
if old_state in text:
    text = text.replace(old_state, new_state, 1)

# Prevent Kotlin/JVM clash between property bpm and a manual setBpm method.
text = text.replace(
    '''    fun setBpm(value: Int) {\n        bpm = value.coerceIn(50, 180)\n    }\n''',
    '''    fun updateBpm(value: Int) {\n        bpm = value.coerceIn(50, 180)\n    }\n''',
    1,
)

# Keep the existing fixed playback implementation when present.
old_tone = '''    private fun playTone(freq: Double, duration: Double, amp: Float, harmonic: Double, effect: Int) {\n        val old = effectIndex\n        effectIndex = effect.coerceIn(effects.indices)\n        playVoice(Voice("Pad", "Pad", freq, harmonic, Character.SYNTH), amp, duration)\n        effectIndex = old\n    }\n'''
new_tone = '''    private fun playTone(freq: Double, duration: Double, amp: Float, harmonic: Double, effect: Int) {\n        val selectedEffect = effect.coerceIn(effects.indices)\n        Thread {\n            val sr = 44100\n            val count = max(1, (sr * duration).toInt())\n            val data = ShortArray(count)\n            for (i in data.indices) {\n                val t = i.toDouble() / sr\n                val p = i.toDouble() / count\n                val env = if (p < 0.015) p / 0.015 else exp(-p * 4.0)\n                val wave = sin(2.0 * PI * freq * t) + harmonic * sin(2.0 * PI * freq * 2.0 * t)\n                val effected = when (selectedEffect) {\n                    0 -> wave\n                    1 -> wave * (0.88 + 0.12 * sin(2.0 * PI * 2.3 * t))\n                    2 -> wave + wave * 0.30 * sin(2.0 * PI * 3.7 * t)\n                    3 -> wave + wave * 0.22 * sin(2.0 * PI * 0.7 * t)\n                    4 -> wave + wave * 0.18 * sin(2.0 * PI * 5.0 * t)\n                    5 -> tanh(wave * 1.7)\n                    6 -> wave * (0.70 + 0.30 * (0.5 + 0.5 * sin(2.0 * PI * 5.0 * t)))\n                    else -> wave + 0.35 * sin(2.0 * PI * 2.0 * t)\n                }\n                data[i] = (effected * 10500.0 * amp * env).toInt()\n                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()\n            }\n            playPcm(data, sr)\n        }.start()\n    }\n'''
if old_tone in text:
    text = text.replace(old_tone, new_tone, 1)

# Make recorder state observable.
text = text.replace(
    '''    var isRecording: Boolean = false\n        private set\n    var lastFile: File? = null\n        private set\n''',
    '''    var isRecording: Boolean by mutableStateOf(false)\n        private set\n    var lastFile: File? by mutableStateOf(null)\n        private set\n''',
    1,
)

# Add dedicated Arabic performance SFX and an all-voice audition button.
if "ORG_SOUND_BANK_V2" not in text:
    anchor = '''    fun triggerVoice() {\n        val voice = voices[voiceIndex]\n        playVoice(voice, volume, 0.80)\n    }\n\n'''
    addition = '''    // ORG_SOUND_BANK_V2\n    val specialSounds = listOf(\n        "طبلة", "دربكة", "زفة", "زغروطة", "دف", "رق", "كسرات شعبية", "تصفيق"\n    )\n\n    fun triggerSpecial(index: Int) {\n        when (index.coerceIn(specialSounds.indices)) {\n            0 -> tabla(0.95, 0.95)\n            1 -> tabla(0.48, 0.72)\n            2 -> weddingSequence()\n            3 -> ululation()\n            4 -> noise(0.18, 0.72, 1800.0)\n            5 -> noise(0.10, 0.62, 3200.0)\n            6 -> {\n                tabla(0.62, 0.70)\n                Thread { Thread.sleep(110); tabla(0.36, 0.52) }.start()\n            }\n            else -> clap()\n        }\n    }\n\n    fun previewAllVoices() {\n        Thread {\n            voices.forEach { voice ->\n                val sr = 44100\n                val duration = 0.24\n                val count = (sr * duration).toInt()\n                val data = ShortArray(count)\n                for (i in data.indices) {\n                    val t = i.toDouble() / sr\n                    val p = i.toDouble() / count\n                    val env = when { p < 0.02 -> p / 0.02 else -> exp(-p * 6.0) }\n                    val f = voice.baseHz\n                    val sample = sin(2.0 * PI * f * t) + voice.harmonic * sin(2.0 * PI * f * 2.0 * t)\n                    data[i] = (sample * 8500.0 * volume * env).toInt()\n                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()\n                }\n                playPcm(data, sr)\n                Thread.sleep(55L)\n            }\n        }.start()\n    }\n\n    private fun tabla(low: Double, high: Double) {\n        Thread {\n            val sr = 44100\n            val duration = 0.32\n            val count = (sr * duration).toInt()\n            val data = ShortArray(count)\n            for (i in data.indices) {\n                val t = i.toDouble() / sr\n                val p = i.toDouble() / count\n                val env = exp(-p * 8.0)\n                val body = sin(2.0 * PI * (82.0 + 38.0 * low) * t) + 0.55 * sin(2.0 * PI * (165.0 + 70.0 * high) * t)\n                val slap = sin(2.0 * PI * (2100.0 + 1200.0 * high) * t) * exp(-p * 22.0)\n                val n = (Random.nextDouble() * 2.0 - 1.0) * 0.10\n                data[i] = ((body * 0.72 + slap * 0.22 + n) * 15000.0 * env).toInt()\n                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()\n            }\n            playPcm(data, sr)\n        }.start()\n    }\n\n    private fun weddingSequence() {\n        Thread {\n            val notes = doubleArrayOf(261.63, 293.66, 329.63, 349.23, 392.00, 440.00)\n            notes.forEachIndexed { i, f ->\n                playTone(f, 0.18, volume * 0.55f, 0.32, 1)\n                if (i % 2 == 1) { Thread.sleep(30L); tabla(0.72, 0.68) }\n                Thread.sleep(105L)\n            }\n        }.start()\n    }\n\n    private fun ululation() {\n        Thread {\n            val sr = 44100\n            val duration = 1.35\n            val count = (sr * duration).toInt()\n            val data = ShortArray(count)\n            for (i in data.indices) {\n                val t = i.toDouble() / sr\n                val p = i.toDouble() / count\n                val vibrato = 6.0 * sin(2.0 * PI * 6.2 * t)\n                val f = 720.0 + 55.0 * sin(2.0 * PI * 1.2 * t) + vibrato\n                val carrier = sin(2.0 * PI * f * t) + 0.42 * sin(2.0 * PI * f * 2.0 * t)\n                val trill = 0.45 * sin(2.0 * PI * (f * 2.05) * t)\n                val gate = if (((t * 11.0).toInt() % 2) == 0) 1.0 else 0.72\n                val env = minOf(1.0, p * 20.0) * exp(-p * 0.45)\n                data[i] = ((carrier + trill) * gate * 8500.0 * volume * env).toInt()\n                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()\n            }\n            playPcm(data, sr)\n        }.start()\n    }\n\n    private fun clap() {\n        Thread {\n            val sr = 44100\n            val duration = 0.24\n            val count = (sr * duration).toInt()\n            val data = ShortArray(count)\n            for (i in data.indices) {\n                val p = i.toDouble() / count\n                val env = exp(-p * 15.0)\n                val n = Random.nextDouble() * 2.0 - 1.0\n                data[i] = (n * 12000.0 * volume * env).toInt().toShort()\n            }\n            playPcm(data, sr)\n        }.start()\n    }\n\n'''
    if anchor not in text:
        raise SystemExit("triggerVoice anchor not found")
    text = text.replace(anchor, anchor + addition, 1)

ORG.write_text(text, encoding="utf-8")

screen = SCREEN.read_text(encoding="utf-8")
screen = screen.replace("engine.setBpm(it.toInt())", "engine.updateBpm(it.toInt())")

# Add a dedicated sound bank card to the Sounds tab.
if "SPECIAL_SOUNDS_UI_V1" not in screen:
    anchor = '''                Button(onClick = { engine.triggerVoice() }, modifier = Modifier.fillMaxWidth()) {\n                            Text("PLAY ${engine.voices[engine.voiceIndex].name.uppercase()}")\n                        }\n'''
    card = '''                Button(onClick = { engine.triggerVoice() }, modifier = Modifier.fillMaxWidth()) {\n                            Text("PLAY ${engine.voices[engine.voiceIndex].name.uppercase()}")\n                        }\n                        Spacer(Modifier.height(8.dp))\n                        OutlinedButton(onClick = { engine.previewAllVoices() }, modifier = Modifier.fillMaxWidth()) {\n                            Text("TEST ALL WESTERN + ORIENTAL VOICES")\n                        }\n                        Spacer(Modifier.height(14.dp))\n                        // SPECIAL_SOUNDS_UI_V1\n                        Text("Arabic Performance & Wedding SFX", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)\n                        Text("Tabla • Darbuka • Daf • Riqq • Wedding • Ululation • Claps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n                        Spacer(Modifier.height(8.dp))\n                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                            items(engine.specialSounds) { sound ->\n                                val idx = engine.specialSounds.indexOf(sound)\n                                FilterChip(selected = false, onClick = { engine.triggerSpecial(idx) }, label = { Text(sound) })\n                            }\n                        }\n'''
    if anchor not in screen:
        raise SystemExit("ORG voice play button anchor not found")
    screen = screen.replace(anchor, card, 1)

if "ORG_REFRESH_V1" not in screen:
    anchor2 = '        Spacer(Modifier.height(12.dp))\n\n        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {'
    replacement2 = '''        // ORG_REFRESH_V1\n        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n            OutlinedButton(onClick = {\n                engine.stopRhythm()\n                engine.startRhythm(scope)\n            }, modifier = Modifier.weight(1f)) { Text("Restart Engine") }\n            OutlinedButton(onClick = {\n                engine.stopRhythm()\n                accompaniment = false\n                engine.accompanimentEnabled = false\n            }, modifier = Modifier.weight(1f)) { Text("Reset Rhythm") }\n        }\n        Spacer(Modifier.height(12.dp))\n\n        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {'''
    if anchor2 in screen:
        screen = screen.replace(anchor2, replacement2, 1)

SCREEN.write_text(screen, encoding="utf-8")
print("ORG voice bank and Arabic performance sound bank expanded")
