from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"

text = MAIN.read_text(encoding="utf-8")
marker = "// DJ_AUDIO_CARD_V2"
old_marker = "// DJ_AUDIO_CARD_V1"

# Start from the restored/known-good source. Remove any legacy V1 card that may still exist
# inside MicScreen so the routing UI is not duplicated.
if old_marker in text:
    start = text.find(old_marker)
    end = text.find('Text("DJ Effects"', start)
    if end > start:
        # Remove from the marker through the card's spacer immediately before DJ Effects.
        spacer = text.rfind('                Spacer(Modifier.height(12.dp))', start, end)
        if spacer >= 0:
            end_remove = spacer + len('                Spacer(Modifier.height(12.dp))\n\n')
            text = text[:start] + text[end_remove:]

# Make the real DJ Mixer screen aware of MicController so the audio card controls the
# same input/output routing engine used by Karaoke/Mic.
old_sig = '''fun DJMixerScreen(\n    djMixerController: DJMixerController,\n    audioLibrary: SnapshotStateList<AudioItem>,\n    onPauseMainPlayer: () -> Unit\n) {'''
new_sig = '''fun DJMixerScreen(\n    djMixerController: DJMixerController,\n    audioLibrary: SnapshotStateList<AudioItem>,\n    micController: MicController,\n    onPauseMainPlayer: () -> Unit\n) {'''
if old_sig in text:
    text = text.replace(old_sig, new_sig, 1)

old_call = '''DJMixerScreen(\n                    djMixerController = djMixerController,\n                    audioLibrary = audioLibrary,\n                    onPauseMainPlayer = { playerController.pause() }\n                )'''
new_call = '''DJMixerScreen(\n                    djMixerController = djMixerController,\n                    audioLibrary = audioLibrary,\n                    micController = micController,\n                    onPauseMainPlayer = { playerController.pause() }\n                )'''
if old_call in text:
    text = text.replace(old_call, new_call, 1)

# If V2 is already present, keep the script idempotent after fixing the signature/call.
if marker in text:
    MAIN.write_text(text, encoding="utf-8")
    print("DJ Audio Card V2 already present; legacy Mic card removed")
    raise SystemExit(0)

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
MAIN.write_text(text, encoding="utf-8")
print("DJ Audio Card V2 moved to DJ Mixer and legacy Mic copy removed")
