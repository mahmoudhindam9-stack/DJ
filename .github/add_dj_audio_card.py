from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
ORG = ROOT / "app/src/main/java/com/example/org/OrgController.kt"
SCREEN = ROOT / "app/src/main/java/com/example/org/OrgScreen.kt"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"

# ---------------- DJ Audio Card / Mic duplicate ----------------
text = MAIN.read_text(encoding="utf-8")
marker = "// DJ_AUDIO_CARD_V2"
old_marker = "// DJ_AUDIO_CARD_V1"

# Remove any legacy V1 card that may still exist inside the Mic screen.
while old_marker in text:
    start = text.find(old_marker)
    end = text.find('Text("DJ Effects"', start)
    if end <= start:
        break
    spacer = text.rfind('                Spacer(Modifier.height(12.dp))', start, end)
    if spacer < 0:
        break
    end_remove = spacer + len('                Spacer(Modifier.height(12.dp))\n\n')
    text = text[:start] + text[end_remove:]

old_sig = '''fun DJMixerScreen(\n    djMixerController: DJMixerController,\n    audioLibrary: SnapshotStateList<AudioItem>,\n    onPauseMainPlayer: () -> Unit\n) {'''
new_sig = '''fun DJMixerScreen(\n    djMixerController: DJMixerController,\n    audioLibrary: SnapshotStateList<AudioItem>,\n    micController: MicController,\n    onPauseMainPlayer: () -> Unit\n) {'''
if old_sig in text:
    text = text.replace(old_sig, new_sig, 1)

old_call = '''DJMixerScreen(\n                    djMixerController = djMixerController,\n                    audioLibrary = audioLibrary,\n                    onPauseMainPlayer = { playerController.pause() }\n                )'''
new_call = '''DJMixerScreen(\n                    djMixerController = djMixerController,\n                    audioLibrary = audioLibrary,\n                    micController = micController,\n                    onPauseMainPlayer = { playerController.pause() }\n                )'''
if old_call in text:
    text = text.replace(old_call, new_call, 1)

if marker not in text:
    sig = '''fun DJMixerScreen(\n    djMixerController: DJMixerController,\n    audioLibrary: SnapshotStateList<AudioItem>,\n    micController: MicController,\n    onPauseMainPlayer: () -> Unit\n) {'''
    if sig not in text:
        raise SystemExit("DJMixerScreen signature not found")
    anchor = '''        Text(\n            text = "Live soundboard, instruments, crowd effects & dual deck control",\n            style = MaterialTheme.typography.bodySmall,\n            color = MaterialTheme.colorScheme.onSurfaceVariant\n        )\n\n        Spacer(modifier = Modifier.height(16.dp))\n\n'''
    if anchor not in text:
        raise SystemExit("DJ Mixer header anchor not found")
    card = r'''        // DJ_AUDIO_CARD_V2
        val routingScope = rememberCoroutineScope()
        var djInputExpanded by remember { mutableStateOf(false) }
        var djOutputExpanded by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Audio Card", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "DJ Input / Master Output",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { micController.refreshDevices() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh audio devices")
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("INPUT • Microphone", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { djInputExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(micController.selectedInputDevice?.displayName() ?: "System Default Mic", maxLines = 1)
                    }
                    DropdownMenu(
                        expanded = djInputExpanded,
                        onDismissRequest = { djInputExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("System Default Mic") },
                            onClick = {
                                micController.selectInputDevice(null, routingScope)
                                djInputExpanded = false
                            }
                        )
                        micController.inputDevices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text(device.displayName()) },
                                onClick = {
                                    micController.selectInputDevice(device, routingScope)
                                    djInputExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("OUTPUT • Master", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { djOutputExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(micController.selectedOutputDevice?.displayName() ?: "System Default Output", maxLines = 1)
                    }
                    DropdownMenu(
                        expanded = djOutputExpanded,
                        onDismissRequest = { djOutputExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("System Default Output") },
                            onClick = {
                                micController.selectOutputDevice(null)
                                djOutputExpanded = false
                            }
                        )
                        micController.outputDevices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text(device.displayName()) },
                                onClick = {
                                    micController.selectOutputDevice(device)
                                    djOutputExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        micController.routingStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

'''
    text = text.replace(anchor, anchor + card, 1)
    text = text.replace('@Composable\nfun DJMixerScreen(', '@Composable\n' + marker + '\nfun DJMixerScreen(', 1)

# ---------------- launcher/task reuse ----------------
manifest = MANIFEST.read_text(encoding="utf-8")
activity_re = r'(<activity\s+android:name="\.MainActivity"\s+android:exported="true"\s+android:label="@string/app_name"\s+android:theme="@style/Theme\.MyApplication")'
manifest = re.sub(activity_re, r'\1\n            android:launchMode="singleTask"', manifest, count=1)
MANIFEST.write_text(manifest, encoding="utf-8")

# ---------------- ORG sounds: real playable Western/Oriental banks + wedding/event sounds ----------------
org = ORG.read_text(encoding="utf-8")

voice_marker = '    val voices = listOf('
if voice_marker not in org:
    raise SystemExit("ORG voice bank not found")

special_block = '''\n    data class SpecialSound(val name: String, val description: String, val kind: SpecialKind)\n    enum class SpecialKind { TABLA, DARBUKA, ZAFFA, ULULATION, CLAPS, TAKHTA }\n\n    val specialSounds = listOf(\n        SpecialSound("Tabla", "Deep tabla / wedding drum", SpecialKind.TABLA),\n        SpecialSound("Darbuka", "Oriental darbuka", SpecialKind.DARBUKA),\n        SpecialSound("Zaffa", "Wedding procession", SpecialKind.ZAFFA),\n        SpecialSound("Zaghrouta", "Traditional ululation", SpecialKind.ULULATION),\n        SpecialSound("Claps", "Wedding crowd claps", SpecialKind.CLAPS),\n        SpecialSound("Takhtah", "Short celebratory hit", SpecialKind.TAKHTA)\n    )\n'''
if 'val specialSounds = listOf(' not in org:
    idx = org.find('    val rhythms = listOf(', org.find(voice_marker))
    org = org[:idx] + special_block + '\n' + org[idx:]

method_anchor = '    fun triggerVoice() {\n'
if 'fun triggerSpecialSound(index: Int)' not in org:
    method = '''    fun triggerSpecialSound(index: Int) {\n        when (specialSounds[index.coerceIn(specialSounds.indices)].kind) {\n            SpecialKind.TABLA -> playPercussion(3)\n            SpecialKind.DARBUKA -> playPercussion(0)\n            SpecialKind.ZAFFA -> playZaffa()\n            SpecialKind.ULULATION -> playUlulation()\n            SpecialKind.CLAPS -> playClaps()\n            SpecialKind.TAKHTA -> playTone(98.0, 0.28, 0.82f, 0.05, 5)\n        }\n    }\n\n'''
    if method_anchor not in org:
        raise SystemExit("ORG triggerVoice anchor not found")
    org = org.replace(method_anchor, method + method_anchor, 1)

special_methods_anchor = '    private fun playPercussion(kind: Int) {\n'
if 'private fun playZaffa()' not in org:
    methods = '''    private fun playZaffa() {\n        Thread {\n            val notes = doubleArrayOf(146.83, 174.61, 196.00, 233.08, 196.00, 174.61)\n            notes.forEachIndexed { i, freq ->\n                playTone(freq, 0.22, 0.72f, if (i % 2 == 0) 0.32 else 0.18, 1)\n                playPercussion(if (i % 3 == 0) 3 else 0)\n                Thread.sleep(120L)\n            }\n        }.start()\n    }\n\n    private fun playUlulation() {\n        Thread {\n            val sr = 44100\n            val duration = 1.55\n            val count = (sr * duration).toInt()\n            val data = ShortArray(count)\n            for (i in data.indices) {\n                val t = i.toDouble() / sr\n                val wobble = 430.0 + 55.0 * sin(2.0 * PI * 5.2 * t)\n                val wave = sin(2.0 * PI * wobble * t) + 0.55 * sin(2.0 * PI * (wobble * 2.01) * t)\n                val env = if (t < 0.10) t / 0.10 else exp(-(t - 0.10) * 0.75)\n                val trill = 0.72 + 0.28 * sin(2.0 * PI * 11.0 * t)\n                data[i] = (wave * trill * 10000.0 * env).toInt()\n                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()\n            }\n            playPcm(data, sr)\n        }.start()\n    }\n\n    private fun playClaps() {\n        Thread {\n            repeat(5) {\n                noise(0.10, 0.72, 1900.0)\n                Thread.sleep(170L)\n            }\n        }.start()\n    }\n\n'''
    if special_methods_anchor not in org:
        raise SystemExit("ORG percussion anchor not found")
    org = org.replace(special_methods_anchor, methods + special_methods_anchor, 1)

ORG.write_text(org, encoding="utf-8")

# Add an event-sounds section to the Sounds page, backed by the real OrgEngine methods.
screen = SCREEN.read_text(encoding="utf-8")
if 'Text("Wedding / Event Sounds"' not in screen:
    anchor = '''                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {\n                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {\n'''
    pos = screen.find(anchor)
    if pos < 0:
        raise SystemExit("ORG sound card insertion anchor not found")
    # Insert immediately before the accompaniment card; this keeps controls visible on the Sounds tab.
    event_card = '''                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {\n                    Column(Modifier.padding(16.dp)) {\n                        Text("Wedding / Event Sounds", fontWeight = FontWeight.Bold)\n                        Text("Oriental drum, zaffa, zaghrouta and celebration effects.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n                        Spacer(Modifier.height(8.dp))\n                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                            items(engine.specialSounds) { sound ->\n                                val idx = engine.specialSounds.indexOf(sound)\n                                Button(onClick = { engine.triggerSpecialSound(idx) }) { Text(sound.name) }\n                            }\n                        }\n                    }\n                }\n                Spacer(Modifier.height(12.dp))\n'''
    screen = screen[:pos] + event_card + screen[pos:]
SCREEN.write_text(screen, encoding="utf-8")

print("Batch audio/ORG/launcher fixes applied")
