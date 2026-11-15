package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.EqualizerController
import com.example.ui.components.CustomVerticalSlider
import com.example.ui.components.DjSurfaceCard
import com.example.ui.components.PremiumBackdrop
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(eqController: EqualizerController) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }
    var presetSaveError by remember { mutableStateOf<String?>(null) }
    var presetToDelete by remember { mutableStateOf<String?>(null) }
    var presetStatusMessage by remember { mutableStateOf<String?>(null) }

    PremiumBackdrop {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MASTER EQUALIZER",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "System-wide audio processing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = eqController.isEnabled,
                onCheckedChange = { eqController.toggleEnable() },
                modifier = Modifier.testTag("eq_enable_switch"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        // Presets header + Save/Update custom preset actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "PRESETS",
                modifier = Modifier,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val editingName = eqController.editingCustomPresetName
                if (editingName != null && eqController.isCustomPreset(editingName)) {
                    FilledTonalButton(
                        onClick = {
                            eqController.updateCustomPreset(editingName)
                                .onSuccess { presetStatusMessage = "Updated \"${it.name}\"" }
                                .onFailure { presetStatusMessage = it.message }
                        },
                        enabled = eqController.isEnabled,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("eq_update_preset_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Update $editingName", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedButton(
                    onClick = {
                        presetNameInput = eqController.editingCustomPresetName.orEmpty()
                        presetSaveError = null
                        showSaveDialog = true
                    },
                    enabled = eqController.isEnabled,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("eq_save_preset_button")
                ) {
                    Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save Preset")
                }
            }
        }

        presetStatusMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Custom Presets Row (when user has saved any)
        if (eqController.customPresets.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                items(eqController.customPresets, key = { it.name }) { custom ->
                    val isSelected = eqController.selectedPreset.equals(custom.name, ignoreCase = true)
                    InputChip(
                        selected = isSelected,
                        onClick = { eqController.applyPreset(custom.name) },
                        label = {
                            Text(
                                custom.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        trailingIcon = {
                            IconButton(
                                onClick = { presetToDelete = custom.name },
                                modifier = Modifier.size(20.dp).testTag("eq_delete_preset_${custom.name}")
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Delete ${custom.name}",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        },
                        enabled = eqController.isEnabled,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("eq_custom_preset_${custom.name}")
                    )
                }
            }
        }

        // Built-in Presets Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(eqController.builtInPresets) { preset ->
                val isSelected = eqController.selectedPreset == preset
                FilterChip(
                    selected = isSelected,
                    onClick = { eqController.applyPreset(preset) },
                    label = {
                        Text(
                            preset,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    enabled = eqController.isEnabled,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("eq_preset_$preset")
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // EQ Bands
        DjSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "FREQUENCIES",
                    modifier = Modifier,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().height(250.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    eqController.bands.forEach { band ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${band.currentLevelDb} dB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CustomVerticalSlider(
                                    value = band.currentLevelDb.toFloat(),
                                    onValueChange = {
                                        eqController.updateBandLevel(band.id, it.roundToInt())
                                    },
                                    valueRange = band.minLevelDb.toFloat()..band.maxLevelDb.toFloat(),
                                    enabled = eqController.isEnabled,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(48.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = band.name.replace(" ", "\n"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        // Bass Boost & Treble Boost
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DjSurfaceCard(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.surface,
                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "BASS BOOST",
                        modifier = Modifier,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${(eqController.bassBoostLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = eqController.bassBoostLevel,
                        onValueChange = { eqController.updateBassBoost(it) },
                        valueRange = 0f..1f,
                        enabled = eqController.isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            DjSurfaceCard(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.surface,
                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "TREBLE BOOST",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${(eqController.trebleBoostLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Slider(
                        value = eqController.trebleBoostLevel,
                        onValueChange = { eqController.updateTrebleBoost(it) },
                        valueRange = 0f..1f,
                        enabled = eqController.isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
            }
        }


        Spacer(modifier = Modifier.height(24.dp))

        // Preamp
        DjSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "PREAMP GAIN",
                        modifier = Modifier,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${eqController.preampDb.toInt()} dB",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = eqController.preampDb,
                    onValueChange = { eqController.updatePreampDb(it) },
                    valueRange = 0f..12f,
                    enabled = eqController.isEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.tertiary,
                        activeTrackColor = MaterialTheme.colorScheme.tertiary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Custom Preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = presetNameInput,
                        onValueChange = {
                            presetNameInput = it
                            presetSaveError = null
                        },
                        label = { Text("Preset Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("eq_preset_name_input")
                    )
                    if (eqController.customPresets.isNotEmpty()) {
                        Text(
                            "Or tap to overwrite:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(eqController.customPresets, key = { it.name }) { existing ->
                                AssistChip(
                                    onClick = {
                                        presetNameInput = existing.name
                                        presetSaveError = null
                                    },
                                    label = { Text(existing.name, maxLines = 1) }
                                )
                            }
                        }
                    }
                    presetSaveError?.let { err ->
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = presetNameInput.trim()
                        val res = eqController.saveCustomPreset(trimmed)
                        res.onSuccess {
                            presetStatusMessage = "Saved custom preset \"${it.name}\""
                            showSaveDialog = false
                        }.onFailure {
                            presetSaveError = it.message ?: "Invalid preset name"
                        }
                    },
                    modifier = Modifier.testTag("eq_confirm_save_preset")
                ) {
                    Text(if (eqController.isCustomPreset(presetNameInput)) "Overwrite" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }

    presetToDelete?.let { targetName ->
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = { Text("Delete Custom Preset") },
            text = { Text("Delete custom preset \"$targetName\"? Built-in presets are unaffected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (eqController.deleteCustomPreset(targetName)) {
                            presetStatusMessage = "Deleted \"$targetName\""
                        }
                        presetToDelete = null
                    },
                    modifier = Modifier.testTag("eq_confirm_delete_preset")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }) { Text("Cancel") }
            }
        )
    }
}