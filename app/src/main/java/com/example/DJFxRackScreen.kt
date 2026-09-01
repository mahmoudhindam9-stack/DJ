package com.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.DJDeck
import com.example.player.DJEffect

/**
 * Mixxx-style effect section embedded directly in each Android DJ deck.
 * All supported effects are visible without opening a secondary browser.
 */
@Composable
fun DJFxRack(deck: DJDeck) {
    var amount by remember { mutableStateOf(deck.fxProcessor.amount) }
    var beatDivision by remember { mutableStateOf(deck.fxProcessor.beatDivision) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
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
                        "MIXXX FX",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "29 live effects • tap to enable • selectable per deck",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "${DJEffect.values().size} FX",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                userScrollEnabled = true
            ) {
                items(DJEffect.values().toList()) { effect ->
                    FilterChip(
                        selected = deck.isEffectActive(effect),
                        onClick = { deck.toggleEffect(effect) },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                effect.displayName,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                "FX Amount: ${(amount * 100).toInt()}%",
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

            Text(
                "Beat Division",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(
                    0.0625f to "1/16",
                    0.125f to "1/8",
                    0.25f to "1/4",
                    0.5f to "1/2",
                    1f to "1"
                ).forEach { item ->
                    FilterChip(
                        selected = kotlin.math.abs(beatDivision - item.first) < 0.001f,
                        onClick = {
                            beatDivision = item.first
                            deck.setEffectBeatDivision(item.first)
                        },
                        label = { Text(item.second, fontSize = 9.sp) }
                    )
                }
            }
        }
    }
}
