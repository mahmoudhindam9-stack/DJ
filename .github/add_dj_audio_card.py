from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"

text = MAIN.read_text(encoding="utf-8")
marker = "// DJ_AUDIO_CARD_V1"

# ExposedDropdownMenuBox/menuAnchor are experimental in the Material3 version used by this app.
# Keep the opt-in attached to MicScreen so the build remains strict without changing global compiler flags.
optin_anchor = '@Composable\n// KARAOKE_DJ_ENGLISH_V2\nfun MicScreen('
optin_replacement = '@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n@Composable\n// KARAOKE_DJ_ENGLISH_V2\nfun MicScreen('
if optin_anchor in text and optin_replacement not in text:
    text = text.replace(optin_anchor, optin_replacement, 1)

if marker in text:
    MAIN.write_text(text, encoding="utf-8")
    print("DJ audio card already present; Material3 opt-in ensured")
    raise SystemExit(0)

anchors = [
    'Text("DJ Effects"',
    'Text("Effects"',
    'Text("المؤثرات"',
    'Text("DJ EFFECTS"'
]
anchor = next((a for a in anchors if a in text), None)
if not anchor:
    raise SystemExit("DJ effects UI anchor not found")

card = r'''// DJ_AUDIO_CARD_V1
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Audio Card", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Independent DJ input / output routing",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { micController.refreshDevices() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh audio devices")
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("INPUT • Microphone", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        var djInputExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = djInputExpanded,
                            onExpandedChange = { djInputExpanded = !djInputExpanded }
                        ) {
                            OutlinedTextField(
                                value = micController.selectedInputDevice?.displayName() ?: "System Default Mic",
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                label = { Text("Input device") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = djInputExpanded) }
                            )
                            ExposedDropdownMenu(
                                expanded = djInputExpanded,
                                onDismissRequest = { djInputExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("System Default Mic") },
                                    onClick = {
                                        micController.selectInputDevice(null, scope)
                                        djInputExpanded = false
                                    }
                                )
                                micController.inputDevices.forEach { device ->
                                    DropdownMenuItem(
                                        text = { Text(device.displayName()) },
                                        onClick = {
                                            micController.selectInputDevice(device, scope)
                                            djInputExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text("OUTPUT • Master", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        var djOutputExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = djOutputExpanded,
                            onExpandedChange = { djOutputExpanded = !djOutputExpanded }
                        ) {
                            OutlinedTextField(
                                value = micController.selectedOutputDevice?.displayName() ?: "System Default Output",
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                label = { Text("Output device") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = djOutputExpanded) }
                            )
                            ExposedDropdownMenu(
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
                            Icon(Icons.Default.GraphicEq, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                micController.routingStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

'''
text = text.replace(anchor, card + anchor, 1)
MAIN.write_text(text, encoding="utf-8")
print("Persistent DJ Audio Card inserted and Material3 opt-in ensured")
