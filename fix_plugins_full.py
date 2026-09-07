import re

with open('app/src/main/java/com/example/player/DeckFxAudioProcessor.kt', 'r') as f:
    text = f.read()

text = text.replace('private val pluginManager = DspPluginManager()', 'private var pluginManager: DspPluginManager? = null')
text = text.replace('init {\n        pluginChain = pluginManager.getAvailablePlugins()\n    }', 'fun initContext(context: android.content.Context) {\n        pluginManager = DspPluginManager(context)\n        pluginChain = pluginManager!!.getAvailablePlugins()\n    }')

with open('app/src/main/java/com/example/player/DeckFxAudioProcessor.kt', 'w') as f:
    f.write(text)

with open('app/src/main/java/com/example/player/AudioPlayerController.kt', 'r') as f:
    text = f.read()

text = text.replace('val fxProcessor = DeckFxAudioProcessor()', 'val fxProcessor = DeckFxAudioProcessor().apply { initContext(context) }')

with open('app/src/main/java/com/example/player/AudioPlayerController.kt', 'w') as f:
    f.write(text)


with open('app/src/main/java/com/example/DJFxRackScreen.kt', 'r') as f:
    text = f.read()

text = text.replace('val MASTER_FX_LIBRARY = listOf(', 'var MASTER_FX_LIBRARY = listOf(')
# we will dynamically fetch it from context in a LaunchedEffect, but for now just inject logic to load from SharedPreferences.

replacement_ui = """
package com.example

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
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
import com.example.player.DJDeckController
import org.json.JSONArray
import org.json.JSONObject

data class ModularEffect(val id: String, val displayName: String, val isCustom: Boolean = false)

fun loadCustomEffects(context: Context): List<ModularEffect> {
    val prefs = context.getSharedPreferences("modular_fx", Context.MODE_PRIVATE)
    val jsonStr = prefs.getString("plugins", "[]") ?: "[]"
    val list = mutableListOf<ModularEffect>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(ModularEffect(obj.getString("id"), obj.getString("name"), true))
        }
    } catch (e: Exception) {}
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
    val context = LocalContext.current
    var amount by remember { mutableStateOf(deck.fxAmount) }
    var showLibraryDialog by remember { mutableStateOf(false) }
    var activePlugins by remember { mutableStateOf(emptyList<String>()) }
    
    // Empty default rack per user request, only user-installed plugins.
    var allPlugins by remember { mutableStateOf(loadCustomEffects(context)) }

    LaunchedEffect(Unit) {
        // Initially load plugins
        allPlugins = loadCustomEffects(context)
        activePlugins = allPlugins.take(4).map { it.id }
    }

    if (showLibraryDialog) {
        AlertDialog(
            onDismissRequest = { showLibraryDialog = false },
            title = { Text("OpenAirLib Plugins") },
            text = {
                Column {
                    Text("Visit OpenAirLib (openairlib.net) or MusicalArtifacts for IRs/Presets. Here you can load them.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    
                    Button(onClick = {
                        // Dummy Add logic for now (would be file picker)
                        val prefs = context.getSharedPreferences("modular_fx", Context.MODE_PRIVATE)
                        val array = JSONArray(prefs.getString("plugins", "[]") ?: "[]")
                        val newObj = JSONObject()
                        val newId = "custom_${System.currentTimeMillis()}"
                        newObj.put("id", newId)
                        newObj.put("name", "Downloaded FX ${array.length() + 1}")
                        newObj.put("type", "delay") // random
                        array.put(newObj)
                        prefs.edit().putString("plugins", array.toString()).apply()
                        allPlugins = loadCustomEffects(context)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import New Plugin File (.json)")
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(300.dp)
                    ) {
                        items(allPlugins) { plugin ->
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
                        if (allPlugins.isEmpty()) "Rack empty. Add plugins!" else "Active Plugins: ${activePlugins.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { showLibraryDialog = true }) {
                    Text("+ Add Custom FX", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            
            if (activePlugins.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    Text("No effects added. Tap + to browse library.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    userScrollEnabled = true
                ) {
                    val displayPlugins = allPlugins.filter { activePlugins.contains(it.id) }
                    items(displayPlugins) { effect ->
                        EffectTile(
                            name = effect.displayName,
                            isActive = deck.isEffectActive(effect.id),
                            onClick = { deck.toggleEffect(effect.id) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
    f.write(replacement_ui)

