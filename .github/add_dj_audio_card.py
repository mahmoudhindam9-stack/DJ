from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
DJ_SCREEN = ROOT / "app/src/main/java/com/example/DJMixerScreen.kt"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"

# ---------------- DJ Audio Card / Mic duplicate ----------------
dj_text = DJ_SCREEN.read_text(encoding="utf-8")
main_text = MAIN.read_text(encoding="utf-8")

marker = "// DJ_AUDIO_CARD_V2"
old_marker = "// DJ_AUDIO_CARD_V1"

# Remove any legacy V1 card that may still exist inside the Mic screen.
while old_marker in dj_text:
    start = dj_text.find(old_marker)
    end = dj_text.find('Text("DJ Effects"', start)
    if end <= start:
        break
    spacer = dj_text.rfind('                Spacer(Modifier.height(12.dp))', start, end)
    if spacer < 0:
        break
    end_remove = spacer + len('                Spacer(Modifier.height(12.dp))\n\n')
    dj_text = dj_text[:start] + dj_text[end_remove:]

old_sig = '''fun DJMixerScreen(
    djMixerController: DJMixerController,
    djFxController: com.example.djfx.DjFxController,
    audioLibrary: SnapshotStateList<AudioItem>,
    onPauseMainPlayer: () -> Unit
) {'''
new_sig = '''fun DJMixerScreen(
    djMixerController: DJMixerController,
    djFxController: com.example.djfx.DjFxController,
    audioLibrary: SnapshotStateList<AudioItem>,
    micController: MicController,
    onPauseMainPlayer: () -> Unit
) {'''

if old_sig in dj_text:
    dj_text = dj_text.replace(old_sig, new_sig, 1)

old_call = '''DJMixerScreen(
                    djMixerController = djMixerController,
                    djFxController = djFxController,
                    audioLibrary = audioLibrary,
                    onPauseMainPlayer = { playerController.pause() }
                )'''
new_call = '''DJMixerScreen(
                    djMixerController = djMixerController,
                    djFxController = djFxController,
                    audioLibrary = audioLibrary,
                    micController = micController,
                    onPauseMainPlayer = { playerController.pause() }
                )'''

if old_call in main_text:
    main_text = main_text.replace(old_call, new_call, 1)

if marker not in dj_text:
    sig = '''fun DJMixerScreen(
    djMixerController: DJMixerController,
    djFxController: com.example.djfx.DjFxController,
    audioLibrary: SnapshotStateList<AudioItem>,
    micController: MicController,
    onPauseMainPlayer: () -> Unit
) {'''
    if sig not in dj_text:
        raise SystemExit("DJMixerScreen signature not found")

    anchor = '''        Text(
            text = "Dual deck control & FX",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

'''
    if anchor not in dj_text:
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
    dj_text = dj_text.replace(anchor, anchor + card, 1)
    dj_text = dj_text.replace('@Composable\nfun DJMixerScreen(', '@Composable\n' + marker + '\nfun DJMixerScreen(', 1)

DJ_SCREEN.write_text(dj_text, encoding="utf-8")
MAIN.write_text(main_text, encoding="utf-8")

# ---------------- launcher/task reuse ----------------
manifest = MANIFEST.read_text(encoding="utf-8")
activity_pattern = r'(<activity\s+android:name="\.MainActivity"[^>]*?)(\s*/>|>)'
match = re.search(activity_pattern, manifest, flags=re.DOTALL)
if match:
    activity = match.group(1)
    activity = re.sub(r'\s+android:launchMode="[^"]*"', '', activity)
    activity += '\n            android:launchMode="singleTask"'
    manifest = manifest[:match.start(1)] + activity + match.group(2) + manifest[match.end(2):]

MANIFEST.write_text(manifest, encoding="utf-8")

print("Batch audio/launcher fixes applied")
