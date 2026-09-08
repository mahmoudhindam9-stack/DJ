import re

with open("/app/applet/app/src/main/java/com/example/DJFxRackScreen.kt", "r") as f:
    content = f.read()

import_target = """                Button(onClick = { showCreateForm = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create New Effect")
                }"""

import_replacement = """                val context = LocalContext.current
                val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                    uri?.let {
                        try {
                            val inputStream = context.contentResolver.openInputStream(it)
                            val jsonString = inputStream?.bufferedReader().use { reader -> reader?.readText() }
                            if (jsonString != null) {
                                val json = JSONObject(jsonString)
                                val name = json.optString("name", "Imported FX")
                                val engine = json.optString("engine", "fx_filter")
                                val p1 = json.optDouble("param1", 0.5).toFloat()
                                val p2 = json.optDouble("param2", 0.5).toFloat()
                                manager.addCustomPreset(name, engine, p1, p2)
                                onPresetsChanged()
                                Toast.makeText(context, "Effect imported successfully!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("DJFxRack", "Import failed", e)
                            Toast.makeText(context, "Invalid effect file.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showCreateForm = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Create", maxLines = 1)
                    }
                    Button(onClick = { importLauncher.launch("application/json") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp)) // replace with import icon if needed
                        Spacer(Modifier.width(4.dp))
                        Text("Import", maxLines = 1)
                    }
                }"""

content = content.replace(import_target, import_replacement)

with open("/app/applet/app/src/main/java/com/example/DJFxRackScreen.kt", "w") as f:
    f.write(content)
