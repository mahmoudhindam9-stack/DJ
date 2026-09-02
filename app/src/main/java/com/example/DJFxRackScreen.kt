package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.DJDeck
import com.example.player.DJEffect
import com.example.player.MaqamPlayer
import com.example.player.MaqamPreset

@Composable
fun EffectTile(
    name: String,
    isActive: Boolean,
    onClick: () -> Unit,
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
                fontSize = 10.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Mixxx-style effect section embedded directly in each Android DJ deck.
 * All supported 29 live effects are clearly visible and interactive.
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
                        "MIXXX FX RACK",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "29 live DSP effects • tap to activate • per-deck control",
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
                    .height(240.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                userScrollEnabled = true
            ) {
                items(DJEffect.values().toList()) { effect ->
                    EffectTile(
                        name = effect.displayName,
                        isActive = deck.isEffectActive(effect),
                        onClick = { deck.toggleEffect(effect) },
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
                    "FX Amount: ${(amount * 100).toInt()}%",
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

            Spacer(Modifier.height(4.dp))

            Text(
                "Beat Division",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    0.0625f to "1/16",
                    0.125f to "1/8",
                    0.25f to "1/4",
                    0.5f to "1/2",
                    1f to "1"
                ).forEach { item ->
                    val selected = kotlin.math.abs(beatDivision - item.first) < 0.001f
                    Surface(
                        onClick = {
                            beatDivision = item.first
                            deck.setEffectBeatDivision(item.first)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                item.second,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated, complete Oriental Maqamat & Taqasim Section with Play, Stop, Loop,
 * Instrument selection, and 12+ real authentic scales and solos.
 */
@Composable
fun MaqamatSection(player: MaqamPlayer) {
    var selectedCategory by remember { mutableStateOf("Scales (سلالم)") }
    val categories = listOf("Scales (سلالم)", "Taqasim (تقاسيم)", "Songs (أغاني)")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            // Header with current status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            "قسم المقامات والتقاسيم الشرقية (Maqamat)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "سلالم • تقاسيم حية • ألحان شرقية جاهزة وشغالة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (player.isPlaying) {
                    Button(
                        onClick = { player.stop() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إيقاف (Stop)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Now playing banner if active
            if (player.isPlaying) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("▶ جاري التشغيل:", fontSize = 11.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                player.currentPlayingTitle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            if (player.isLooping) "🔁 تكرار مفعل" else "تشغيل مفرد",
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Instrument Selector
            Text("الآلة الموسيقية (Instrument)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(player.instruments) { inst ->
                    FilterChip(
                        selected = player.selectedInstrument == inst,
                        onClick = { player.selectedInstrument = inst },
                        label = { Text(inst, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Category Selector and Loop Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }

                FilterChip(
                    selected = player.isLooping,
                    onClick = { player.isLooping = !player.isLooping },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    label = { Text("Loop", fontSize = 11.sp) }
                )
            }

            Spacer(Modifier.height(8.dp))

            // Presets List
            val displayedPresets = player.presets.filter { it.category == selectedCategory }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                displayedPresets.forEach { preset ->
                    val isThisPlaying = player.isPlaying && player.currentPlayingTitle == preset.arabicName
                    Surface(
                        onClick = {
                            if (isThisPlaying) {
                                player.stop()
                            } else {
                                player.playPreset(preset)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isThisPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (isThisPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        tonalElevation = if (isThisPlaying) 4.dp else 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        preset.arabicName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "(${preset.scaleType})",
                                        fontSize = 11.sp,
                                        color = if (isThisPlaying) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    preset.description,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isThisPlaying) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            FilledIconButton(
                                onClick = {
                                    if (isThisPlaying) {
                                        player.stop()
                                    } else {
                                        player.playPreset(preset)
                                    }
                                },
                                modifier = Modifier.size(34.dp),
                                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isThisPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = if (isThisPlaying) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = if (isThisPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                    contentDescription = if (isThisPlaying) "Stop" else "Play",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Volume & Tempo Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("حجم الصوت: ${(player.volume * 100).toInt()}%", fontSize = 11.sp)
                Text("السرعة: ${player.tempoBpm.toInt()} BPM", fontSize = 11.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Slider(
                    value = player.volume,
                    onValueChange = { player.volume = it },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f)
                )
                Slider(
                    value = player.tempoBpm,
                    onValueChange = { player.tempoBpm = it },
                    valueRange = 60f..180f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
