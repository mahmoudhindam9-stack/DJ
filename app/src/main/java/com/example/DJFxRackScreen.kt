
package com.example

import android.content.Context
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONObject
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fx.DspPluginManager
import com.example.player.DJDeckController

data class ModularEffect(val id: String, val displayName: String, val isCustom: Boolean = false)

/** The 8 real-time DSP engines that are always available (see DspPluginManager). */
private val BUILT_IN_ENGINE_LABELS = linkedMapOf(
    "fx_filter" to "🎛️ Filter",
    "fx_delay" to "🔁 Delay",
    "fx_reverb" to "🌊 Reverb",
    "fx_flanger" to "🌀 Flanger",
    "fx_phaser" to "🌈 Phaser",
    "fx_bitcrush" to "👾 Bitcrusher",
    "fx_distortion" to "🔥 Distortion",
    "fx_compressor" to "🗜️ Compressor"
)

fun loadCustomEffects(context: Context): List<ModularEffect> {
    val list = mutableListOf<ModularEffect>()

    // Built-in voice effects (pitch shift on the deck's ExoPlayer).
    list.add(ModularEffect("voice_woman", "👩 Woman Voice"))
    list.add(ModularEffect("voice_kid", "👶 Kid Voice"))
    list.add(ModularEffect("voice_chipmunk", "🐿️ Chipmunk"))
    list.add(ModularEffect("voice_monster", "👹 Monster"))
    list.add(ModularEffect("voice_demon", "👻 Dark Demon"))
    list.add(ModularEffect("voice_giant", "🏔️ Giant Bass"))

    // Real per-sample DSP engines — these actually process the deck's audio.
    BUILT_IN_ENGINE_LABELS.forEach { (id, label) -> list.add(ModularEffect(id, label)) }

    // The user's own saved library entries.
    DspPluginManager(context).getCustomPresets().forEach { preset ->
        list.add(ModularEffect(preset.id, preset.name, isCustom = true))
    }
    return list
}

@Composable
fun EffectTile(
    name: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(45.dp).widthIn(min=90.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        tonalElevation = if (isActive) 6.dp else 1.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun DJFxRack(deck: DJDeckController) {
    val context = LocalContext.current
    val manager = remember { DspPluginManager(context) }
    var amount by remember { mutableStateOf(deck.fxAmount) }
    var showLibraryDialog by remember { mutableStateOf(false) }
    var allPlugins by remember { mutableStateOf(loadCustomEffects(context)) }
    var activePlugins by remember { mutableStateOf(allPlugins.take(4).map { it.id }) }

    fun refreshLibrary() {
        allPlugins = loadCustomEffects(context)
    }

    if (showLibraryDialog) {
        EffectsLibraryDialog(
            manager = manager,
            allPlugins = allPlugins,
            activePlugins = activePlugins,
            onToggleActive = { id ->
                activePlugins = if (activePlugins.contains(id)) activePlugins - id else activePlugins + id
            },
            onPresetsChanged = { refreshLibrary() },
            onDismiss = { showLibraryDialog = false }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "FX RACK",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${activePlugins.size} Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = { showLibraryDialog = true },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Library", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(8.dp))

            if (activePlugins.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No effects loaded. Open the Effects Library to add real DSP FX.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val displayPlugins = allPlugins.filter { activePlugins.contains(it.id) }
                    displayPlugins.forEach { effect ->
                        EffectTile(
                            name = effect.displayName,
                            isActive = deck.isEffectActive(effect.id),
                            onClick = { deck.toggleEffect(effect.id) },
                            modifier = Modifier.height(45.dp).weight(1f, fill = false)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "FX Amount (Dry/Wet): ${(amount * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = amount,
                onValueChange = {
                    amount = it
                    deck.setEffectAmount(it)
                },
                valueRange = 0f..1f
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EffectsLibraryDialog(
    manager: DspPluginManager,
    allPlugins: List<ModularEffect>,
    activePlugins: List<String>,
    onToggleActive: (String) -> Unit,
    onPresetsChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    var showCreateForm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Effects Library") },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                Text(
                    "Tap an effect to add or remove it from this deck's rack. Build your own from real DSP engines below — no coding needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                val context = LocalContext.current
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
                    Button(onClick = { importLauncher.launch("*/*") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp)) // replace with import icon if needed
                        Spacer(Modifier.width(4.dp))
                        Text("Import", maxLines = 1)
                    }
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(allPlugins) { plugin ->
                        val isAdded = activePlugins.contains(plugin.id)
                        Surface(
                            onClick = { onToggleActive(plugin.id) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isAdded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(plugin.displayName, style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (plugin.isCustom) {
                                        IconButton(
                                            onClick = {
                                                manager.deleteCustomPreset(plugin.id)
                                                onPresetsChanged()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Icon(if (isAdded) Icons.Filled.Close else Icons.Filled.Add, null)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )

    if (showCreateForm) {
        CreateEffectDialog(
            manager = manager,
            onCreated = {
                onPresetsChanged()
                showCreateForm = false
            },
            onDismiss = { showCreateForm = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateEffectDialog(
    manager: DspPluginManager,
    onCreated: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(DspPluginManager.ENGINE_TYPES.first().first) }
    var param1 by remember { mutableStateOf(0.5f) }
    var param2 by remember { mutableStateOf(0.5f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Effect") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Engine", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DspPluginManager.ENGINE_TYPES) { (typeKey, label) ->
                        FilterChip(
                            selected = selectedType == typeKey,
                            onClick = { selectedType = typeKey },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Character: ${(param1 * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                Slider(value = param1, onValueChange = { param1 = it }, valueRange = 0f..1f)
                if (selectedType == "compressor") {
                    Text("Ratio: ${(param2 * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    Slider(value = param2, onValueChange = { param2 = it }, valueRange = 0f..1f)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = name.ifBlank {
                        DspPluginManager.ENGINE_TYPES.find { it.first == selectedType }?.second ?: "Custom FX"
                    }
                    manager.addCustomPreset(finalName, selectedType, param1, param2)
                    onCreated()
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
