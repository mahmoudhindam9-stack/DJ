package com.example.player

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Pro equalizer UI using the existing navigation entry.
 * The DSP is completely bypassed until the user enables the switch.
 */
@Composable
fun EqualizerScreen(eqController: EqualizerController) {
    val context = LocalContext.current
    val bands = eqController.bands
    val presets = eqController.presets

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Pro Equalizer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("10-band • GM presets • isolated DSP", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = eqController.isEnabled, onCheckedChange = { eqController.toggleEnable() })
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (eqController.isEnabled) "ACTIVE — EQ is applied to the player session"
                        else "BYPASSED — original player audio path is untouched",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        item {
            Text("Presets", fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(presets) { preset ->
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
                    Text("GM Quick Control", fontWeight = FontWeight.Bold)
                    Text("Three controls shared with the home-screen widget", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Bass: ${eqController.quickBassDb} dB")
                    Slider(
                        value = eqController.quickBassDb.toFloat(),
                        onValueChange = { eqController.setQuickBass(it.toInt()) },
                        valueRange = -12f..12f
                    )
                    Text("Mid: ${eqController.quickMidDb} dB")
                    Slider(
                        value = eqController.quickMidDb.toFloat(),
                        onValueChange = { eqController.setQuickMid(it.toInt()) },
                        valueRange = -12f..12f
                    )
                    Text("Treble: ${eqController.quickTrebleDb} dB")
                    Slider(
                        value = eqController.quickTrebleDb.toFloat(),
                        onValueChange = { eqController.setQuickTreble(it.toInt()) },
                        valueRange = -12f..12f
                    )
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
                        valueRange = -12f..12f,
                        steps = 23
                    )
                }
            }
        }

        item {
            Button(onClick = { eqController.applyPreset("Flat") }, Modifier.fillMaxWidth()) {
                Text("Reset to Flat")
            }
        }

        item {
            Button(onClick = { openDolby(context) }, Modifier.fillMaxWidth()) {
                Text("Open Dolby Atmos")
            }
        }
    }
}

private fun openDolby(context: Context) {
    val packageNames = listOf("com.dolby.daxappui2", "com.dolby.daxappui")
    val intent = packageNames.asSequence()
        .mapNotNull { context.packageManager.getLaunchIntentForPackage(it) }
        .firstOrNull()
    if (intent != null) {
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "Dolby Atmos is not available on this device", Toast.LENGTH_SHORT).show()
    }
}
