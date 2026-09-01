package com.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.DJDeck
import com.example.player.DJEffect

private val defaultFxSlots = listOf(
    DJEffect.FILTER,
    DJEffect.ECHO,
    DJEffect.REVERB
)

@Composable
fun DJFxRack(deck: DJDeck) {
    val slots = remember { mutableStateListOf(*defaultFxSlots.toTypedArray()) }
    var amount by remember { mutableStateOf(deck.fxProcessor.amount) }
    var beatDivision by remember { mutableStateOf(deck.fxProcessor.beatDivision) }
    var browserExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("FX Rack", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("3-slot performance rack • beat-synced", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = { browserExpanded = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                ) { Text("FX Browser", fontSize = 10.sp) }
                DropdownMenu(expanded = browserExpanded, onDismissRequest = { browserExpanded = false }) {
                    DJEffect.values().forEach { effect ->
                        DropdownMenuItem(
                            text = { Text(effect.displayName) },
                            onClick = {
                                if (!deck.isEffectActive(effect)) deck.toggleEffect(effect)
                                browserExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            slots.forEachIndexed { index, effect ->
                FxSlotRow(
                    slotNumber = index + 1,
                    effect = effect,
                    active = deck.isEffectActive(effect),
                    onToggle = { deck.toggleEffect(effect) },
                    onSelect = { selected ->
                        val old = slots[index]
                        if (old != selected && deck.isEffectActive(old)) deck.toggleEffect(old)
                        slots[index] = selected
                    }
                )
                if (index < slots.lastIndex) Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(10.dp))
            Text("FX Amount: ${(amount * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Slider(
                value = amount,
                onValueChange = {
                    amount = it
                    deck.setEffectAmount(it)
                },
                valueRange = 0f..1f
            )

            Text("Beat Division", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf(0.0625f to "1/16", 0.125f to "1/8", 0.25f to "1/4", 0.5f to "1/2", 1f to "1")) { item ->
                    FilterChip(
                        selected = kotlin.math.abs(beatDivision - item.first) < 0.001f,
                        onClick = {
                            beatDivision = item.first
                            deck.setEffectBeatDivision(item.first)
                        },
                        label = { Text(item.second, fontSize = 10.sp) }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "Available effects: ${DJEffect.values().size} • legacy pads remain available above",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FxSlotRow(
    slotNumber: Int,
    effect: DJEffect,
    active: Boolean,
    onToggle: () -> Unit,
    onSelect: (DJEffect) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$slotNumber", modifier = Modifier.width(20.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Button(
            onClick = { expanded = true },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 5.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background)
        ) { Text(effect.displayName, fontSize = 11.sp, maxLines = 1) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DJEffect.values().forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(candidate.displayName) },
                    onClick = {
                        onSelect(candidate)
                        expanded = false
                    }
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = active, onCheckedChange = { onToggle() })
    }
}
