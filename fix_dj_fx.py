import re

with open('app/src/main/java/com/example/DJFxRackScreen.kt', 'r') as f:
    text = f.read()

replacement = """
package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.DJDeckController

data class ModularEffect(val id: String, val displayName: String)

val MASTER_FX_LIBRARY = listOf(
    ModularEffect("fx_filter", "Filter"),
    ModularEffect("fx_delay", "Delay"),
    ModularEffect("fx_reverb", "Reverb"),
    ModularEffect("fx_flanger", "Flanger"),
    ModularEffect("fx_phaser", "Phaser"),
    ModularEffect("fx_bitcrush", "Bitcrusher"),
    ModularEffect("fx_distortion", "Distortion"),
    ModularEffect("fx_compressor", "Compressor")
)

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
        modifier = modifier.height(38.dp),
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
    var amount by remember { mutableStateOf(deck.fxAmount) }
    var showLibraryDialog by remember { mutableStateOf(false) }
    var activePlugins by remember { mutableStateOf(MASTER_FX_LIBRARY.take(6).map { it.id }) }

    if (showLibraryDialog) {
        AlertDialog(
            onDismissRequest = { showLibraryDialog = false },
            title = { Text("FX Plugin Library") },
            text = {
                Column {
                    Text("Select plugins to add to your active rack:", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(300.dp)
                    ) {
                        items(MASTER_FX_LIBRARY) { plugin ->
                            val isAdded = activePlugins.contains(plugin.id)
                            Surface(
                                onClick = {
                                    if (isAdded) activePlugins = activePlugins - plugin.id
                                    else activePlugins = activePlugins + plugin.id
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isAdded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(plugin.displayName, style = MaterialTheme.typography.bodyMedium)
                                    Icon(if (isAdded) Icons.Filled.Close else Icons.Filled.Add, null)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLibraryDialog = false }) { Text("Done") }
            }
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
                        "MODULAR FX RACK",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Active Plugins: ${activePlugins.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { showLibraryDialog = true }) {
                    Text("+ FX Library", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                userScrollEnabled = true
            ) {
                val displayPlugins = MASTER_FX_LIBRARY.filter { activePlugins.contains(it.id) }
                items(displayPlugins) { effect ->
                    EffectTile(
                        name = effect.displayName,
                        isActive = deck.isEffectActive(effect.id),
                        onClick = { deck.toggleEffect(effect.id) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "FX Amount (Dry/Wet): ${(amount * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
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
"""

with open('app/src/main/java/com/example/DJFxRackScreen.kt', 'w') as f:
    f.write(replacement)
