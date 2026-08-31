package com.example.player

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun EqualizerScreen(eqController: EqualizerController) {
    val context = LocalContext.current
    val bands = eqController.bands
    val presets = remember { eqController.presets }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Equalizer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Safe EQ range: -6 dB to +6 dB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = eqController.isEnabled, onCheckedChange = { eqController.toggleEnable() })
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.take(4).forEach { preset ->
                    FilterChip(
                        selected = eqController.selectedPreset == preset,
                        onClick = { eqController.applyPreset(preset) },
                        label = { Text(preset) }
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.drop(4).forEach { preset ->
                    FilterChip(
                        selected = eqController.selectedPreset == preset,
                        onClick = { eqController.applyPreset(preset) },
                        label = { Text(preset) }
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Bass Boost", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = eqController.bassBoostLevel,
                        onValueChange = eqController::updateBassBoost,
                        valueRange = 0f..1f
                    )
                    Text("${(eqController.bassBoostLevel * 100).toInt()}%")
                    Spacer(Modifier.height(8.dp))
                    Text("Treble Boost", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = eqController.trebleBoostLevel,
                        onValueChange = eqController::updateTrebleBoost,
                        valueRange = 0f..1f
                    )
                    Text("${(eqController.trebleBoostLevel * 100).toInt()}%")
                }
            }
        }

        items(bands, key = { it.id }) { band ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(band.name, fontWeight = FontWeight.SemiBold)
                        Text("${band.currentLevelDb} dB", color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = band.currentLevelDb.toFloat(),
                        onValueChange = { eqController.updateBandLevel(band.id, it.toInt()) },
                        valueRange = -6f..6f,
                        steps = 11
                    )
                }
            }
        }

        item {
            Button(
                onClick = { openDolby(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Dolby Atmos")
            }
        }
    }
}

private fun openDolby(context: Context) {
    val packageNames = listOf("com.dolby.daxappui2", "com.dolby.daxappui")
    val intent = packageNames
        .asSequence()
        .mapNotNull { context.packageManager.getLaunchIntentForPackage(it) }
        .firstOrNull()

    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "Dolby Atmos is not available on this device", Toast.LENGTH_SHORT).show()
    }
}
