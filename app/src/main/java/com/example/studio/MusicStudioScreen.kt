package com.example.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MusicStudioScreen(controller: MusicStudioController) {
    val scope = rememberCoroutineScope()
    val gridScroll = rememberScrollState()
    val pitches = (72 downTo 48).toList()
    @Suppress("UNUSED_VARIABLE")
    val revision = controller.uiRevision

    val activeNoteSet = remember(controller.uiRevision, controller.selectedTrackId) {
        controller.selectedTrack.notes.map { Pair(it.pitch, (it.startBeat * 2.0f).toInt()) }.toSet()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("🎵 Music Studio & Arranger", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("استوديو التوزيع الموسيقي • البيانو رول • الآلات والمؤثرات", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledIconButton(
                    onClick = {
                        if (controller.isPlaying) controller.stopPlayback() else controller.startPlayback(scope)
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (controller.isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (controller.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause"
                    )
                }
                OutlinedButton(
                    onClick = { controller.saveProject() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = "Save", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("حفظ", fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Transport & Project Controls
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("السرعة: ${controller.bpm} BPM", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("المازورات: ${controller.bars} Bars", style = MaterialTheme.typography.labelMedium)
                    Text(if (controller.loopEnabled) "🔁 تكرار مفعل" else "تشغيل مفرد", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }

                Slider(
                    value = controller.bpm.toFloat(),
                    onValueChange = { controller.bpm = it.toInt().coerceIn(50, 200) },
                    valueRange = 50f..200f
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = controller.loopEnabled,
                        onClick = { controller.loopEnabled = !controller.loopEnabled },
                        label = { Text("Loop") }
                    )
                    Text("المفتاح (Key):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        itemsIndexed(controller.keys) { _, key ->
                            FilterChip(
                                selected = controller.selectedKey == key,
                                onClick = { controller.selectedKey = key },
                                label = { Text(key, fontSize = 10.sp) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text("المقام أو السلم الموسيقي (Scale / Maqam)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    itemsIndexed(controller.scales) { _, scale ->
                        FilterChip(
                            selected = controller.selectedScale == scale,
                            onClick = { controller.selectedScale = scale },
                            label = { Text(scale, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Tracks Row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("المسارات (Tracks)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            AssistChip(
                onClick = controller::addTrack,
                leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(14.dp)) },
                label = { Text("إضافة مسار", fontSize = 11.sp) }
            )
        }

        Spacer(Modifier.height(4.dp))

        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(controller.tracks) { _, track ->
                FilterChip(
                    selected = controller.selectedTrackId == track.id,
                    onClick = { controller.selectedTrackId = track.id },
                    label = { Text(track.name, fontSize = 11.sp) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Selected Track Controls
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(controller.selectedTrack.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(controller.selectedTrack.instrument, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = controller::toggleTrackMute) {
                        Icon(if (controller.selectedTrack.muted) Icons.Filled.VolumeMute else Icons.Filled.VolumeUp, "Mute", tint = if (controller.selectedTrack.muted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = controller::toggleTrackSolo) {
                        Text("S", fontWeight = FontWeight.Bold, color = if (controller.selectedTrack.solo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = controller::duplicateTrack) {
                        Icon(Icons.Filled.CopyAll, "Duplicate", Modifier.size(18.dp))
                    }
                    IconButton(onClick = controller::deleteTrack) {
                        Icon(Icons.Filled.Delete, "Delete", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("الآلة الموسيقية للمسار:", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    itemsIndexed(controller.instruments) { _, instrument ->
                        FilterChip(
                            selected = controller.selectedTrack.instrument == instrument,
                            onClick = { controller.setTrackInstrument(instrument) },
                            label = { Text(instrument, fontSize = 10.sp) }
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("مستوى الصوت: ${(controller.selectedTrack.volume * 100).toInt()}%", fontSize = 11.sp)
                }
                Slider(
                    value = controller.selectedTrack.volume,
                    onValueChange = controller::setTrackVolume,
                    valueRange = 0f..1f
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Chords & Melody Presets
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { controller.addChord(controller.keyBaseForUi(), 0f) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text("إضافة كورد", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = controller::applyChordProgression,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text("تتابع كوردات", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = controller::clearTrack,
                        modifier = Modifier.weight(0.8f),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text("مسح", fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("ألحان وتقاسيم جاهزة (Melody Presets)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    itemsIndexed(controller.melodyPresets) { index, p ->
                        AssistChip(
                            onClick = { controller.applyMelodyPreset(index) },
                            label = { Text(p.name, fontSize = 10.sp) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Piano Roll Grid
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(10.dp)) {
                Text("🎹 بيانو رول (Piano Roll) — اضغط على المربعات لإضافة/حذف النغمات:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))

                Row(Modifier.fillMaxWidth().horizontalScroll(gridScroll)) {
                    Column {
                        // Beat Headers
                        Row {
                            Spacer(Modifier.width(52.dp))
                            val totalSteps = (controller.loopBeats / 0.5f).toInt()
                            repeat(totalSteps) { i ->
                                val beatNum = i / 2 + 1
                                val isCurrentPlayhead = (controller.playheadBeat * 2).toInt() == i && controller.isPlaying
                                Box(
                                    Modifier
                                        .width(36.dp)
                                        .height(26.dp)
                                        .background(
                                            if (isCurrentPlayhead) MaterialTheme.colorScheme.primary
                                            else if (i % 8 == 0) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "$beatNum.${(i % 2) + 1}",
                                        fontSize = 8.sp,
                                        color = if (isCurrentPlayhead) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Pitches Rows
                        pitches.forEach { pitch ->
                            Row {
                                Box(
                                    Modifier
                                        .width(52.dp)
                                        .height(24.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(noteName(pitch), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }

                                val totalSteps = (controller.loopBeats / 0.5f).toInt()
                                repeat(totalSteps) { i ->
                                    val beat = i * 0.5f
                                    val active = (pitch to i) in activeNoteSet
                                    Box(
                                        Modifier
                                            .width(36.dp)
                                            .height(24.dp)
                                            .padding(1.dp)
                                            .background(
                                                if (active) MaterialTheme.colorScheme.primary
                                                else if (i % 8 == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                else Color.Transparent,
                                                RoundedCornerShape(3.dp)
                                            )
                                            .clickable {
                                                if (active) controller.removeNote(pitch, beat)
                                                else controller.addNote(pitch, beat)
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Master Studio FX Rack (مؤثرات الاستوديو الماستر)
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            )
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("🎛️ مؤثرات الاستوديو الماستر (Studio Master FX)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(8.dp))

                // Reverb & Delay
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Reverb (تردد الصدى): ${(controller.reverbAmount * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Slider(
                            value = controller.reverbAmount,
                            onValueChange = { controller.reverbAmount = it },
                            valueRange = 0f..1f
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Delay (إيكو وتكرار): ${(controller.delayAmount * 100).toInt()}%", fontSize = 11.sp)
                        Slider(
                            value = controller.delayAmount,
                            onValueChange = { controller.delayAmount = it },
                            valueRange = 0f..1f
                        )
                    }
                }

                // Filter & Warmth
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Filter Cutoff (فلتر الصوت): ${(controller.filterCutoff * 100).toInt()}%", fontSize = 11.sp)
                        Slider(
                            value = controller.filterCutoff,
                            onValueChange = { controller.filterCutoff = it },
                            valueRange = 0.05f..1f
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Warmth & Drive (دفء النغم): ${(controller.warmthDrive * 100).toInt()}%", fontSize = 11.sp)
                        Slider(
                            value = controller.warmthDrive,
                            onValueChange = { controller.warmthDrive = it },
                            valueRange = 0f..1f
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Darbuka Live Pads
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🥁 Darbuka Live Pads (طبلة شرقية حية)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("اضغط للعزف المباشر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val pads = listOf(
                        "Doom" to "دوم (Doom)",
                        "Tak" to "تاك (Tak)",
                        "Sak" to "صك (Sak)",
                        "Ka" to "كاب (Ka)",
                        "Riq" to "رق (Riq)",
                        "Bandir" to "بندير (Bandir)"
                    )
                    pads.forEach { (type, label) ->
                        Button(
                            onClick = { controller.playLiveDarbuka(type) },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            contentPadding = PaddingValues(2.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(label, fontSize = 9.sp, maxLines = 1, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Rhythms & Drum Loops
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("إيقاعات وطبلة لووب متزامنة (Oriental Rhythms & Loops)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    itemsIndexed(controller.rhythms) { i, r ->
                        FilterChip(
                            selected = controller.selectedRhythmIndex == i,
                            onClick = { controller.selectedRhythmIndex = i },
                            label = { Text(r.name, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("الإيقاع المختار: ${controller.selectedRhythm.name} • يتزامن تلقائياً مع تشغيل الاستوديو", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun noteName(pitch: Int): String {
    val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    return "${names[pitch % 12]}${pitch / 12 - 1}"
}

private fun MusicStudioController.keyBaseForUi(): Int = 60 + keys.indexOf(selectedKey).coerceAtLeast(0)
