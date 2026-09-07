
package com.example

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.provider.OpenableColumns
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
        modifier = modifier.height(45.dp).width(110.dp),
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


    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            var fileName = "Custom FX"
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx)
                }
            }
            fileName = fileName.substringBeforeLast(".")
            
            val prefs = context.getSharedPreferences("modular_fx", Context.MODE_PRIVATE)
            val array = JSONArray(prefs.getString("plugins", "[]") ?: "[]")
            val newObj = JSONObject()
            val newId = "custom_${System.currentTimeMillis()}"
            newObj.put("id", newId)
            newObj.put("name", fileName)
            newObj.put("type", "imported")
            array.put(newObj)
            prefs.edit().putString("plugins", array.toString()).apply()
            allPlugins = loadCustomEffects(context)
        }
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
                        launcher.launch("*/*")
                    }, ) {
                        Icon(Icons.Filled.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import New Plugin File (.json)")
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    androidx.compose.foundation.lazy.LazyColumn(
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
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = true
                ) {
                    val displayPlugins = allPlugins.filter { activePlugins.contains(it.id) }
                    items(displayPlugins) { effect ->
                        EffectTile(
                            name = effect.displayName,
                            isActive = deck.isEffectActive(effect.id),
                            onClick = { deck.toggleEffect(effect.id) }
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
