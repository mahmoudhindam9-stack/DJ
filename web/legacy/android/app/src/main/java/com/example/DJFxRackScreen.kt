package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fx.DspPluginManager
import com.example.player.DJDeckController
import com.example.ui.components.DjSurfaceCard
import org.json.JSONObject
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

data class ModularEffect(val id: String, val displayName: String, val isCustom: Boolean = false)

private val BUILT_IN_ENGINE_LABELS = linkedMapOf(
    "fx_filter" to "🎛️ Filter",
    "fx_delay" to "🔁 Delay",
    "fx_reverb" to "🌊 Reverb",
    "fx_flanger" to "🌀 Flanger",
    "fx_phaser" to "🌈 Phaser",
    "fx_bitcrush" to "👾 Bitcrush",
    "fx_distortion" to "🔥 Distort",
    "fx_compressor" to "🗜️ Comp"
)

fun loadCustomEffects(context: Context): List<ModularEffect> {
    val list = mutableListOf<ModularEffect>()
    list.add(ModularEffect("voice_woman", "👩 Woman Voice"))
    list.add(ModularEffect("voice_kid", "👶 Kid Voice"))
    list.add(ModularEffect("voice_chipmunk", "🐿️ Chipmunk"))
    list.add(ModularEffect("voice_monster", "👹 Monster"))
    list.add(ModularEffect("voice_demon", "👻 Dark Demon"))
    list.add(ModularEffect("voice_giant", "🏔️ Giant Bass"))
    BUILT_IN_ENGINE_LABELS.forEach { (id, label) -> list.add(ModularEffect(id, label)) }
    DspPluginManager(context).getCustomPresets().forEach { preset ->
        list.add(ModularEffect(preset.id, preset.name, isCustom = true))
    }
    return list
}

@Composable
fun DJFxRack(deck: DJDeckController) {
    val context = LocalContext.current
    val manager = remember { DspPluginManager(context) }
    var allPlugins by remember { mutableStateOf(loadCustomEffects(context)) }
    
    var amount by remember { mutableStateOf(deck.fxAmount) }
    val scrollState = rememberScrollState()

    var showLibraryDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FX RACK",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            IconButton(
                onClick = { showLibraryDialog = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Filled.Settings, "Effects Library", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            allPlugins.forEach { effect ->
                val isActive = deck.isEffectActive(effect.id)
                val color = when {
                    effect.id.contains("filter") -> Color(0xFF00B0FF)
                    effect.id.contains("delay") -> Color(0xFFE040FB)
                    effect.id.contains("reverb") -> Color(0xFF00E676)
                    effect.id.contains("distortion") -> Color(0xFFFF3D00)
                    else -> MaterialTheme.colorScheme.tertiary
                }
                
                DjSurfaceCard(
                    modifier = Modifier.width(90.dp).height(100.dp),
                    color = if (isActive) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    borderColor = if (isActive) color else Color.Transparent,
                    onClick = { deck.toggleEffect(effect.id) }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = effect.displayName.take(12),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Amount slider applies to all active FX on this deck
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "FX AMOUNT", 
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 8.dp)
            )
            Slider(
                value = amount,
                onValueChange = {
                    amount = it
                    deck.setEffectAmount(it)
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.tertiary,
                    activeTrackColor = MaterialTheme.colorScheme.tertiary
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showLibraryDialog) {
        EffectsLibraryDialog(
            manager = manager,
            allPlugins = allPlugins,
            activePlugins = emptyList(), // Not used anymore since we just toggle directly
            onToggleActive = { deck.toggleEffect(it) },
            onPresetsChanged = { 
                allPlugins = loadCustomEffects(context)
                deck.fxProcessor.refreshPlugins()
            },
            onDismiss = { showLibraryDialog = false }
        )
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
        title = { Text("FX Library", fontWeight = FontWeight.Bold) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                Text(
                    "Select effects for this deck",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showCreateForm = true }, 
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text("Create Custom", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(allPlugins) { plugin ->
                        DjSurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f),
                            onClick = { onToggleActive(plugin.id) }
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
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
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
        title = { Text("New Custom Effect") },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Effect Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                
                Text("Base Engine", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DspPluginManager.ENGINE_TYPES) { (typeKey, label) ->
                        FilterChip(
                            selected = selectedType == typeKey,
                            onClick = { selectedType = typeKey },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                Text("Character: ${(param1 * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                Slider(value = param1, onValueChange = { param1 = it }, valueRange = 0f..1f, colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary))
                
                if (selectedType == "compressor") {
                    Text("Ratio: ${(param2 * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    Slider(value = param2, onValueChange = { param2 = it }, valueRange = 0f..1f, colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary))
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
