package com.example.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MusicStudioScreen(controller: MusicStudioController) {
    val scope = rememberCoroutineScope()
    val gridScroll = rememberScrollState()
    val pitches = (72 downTo 48).toList()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Music Studio", style = MaterialTheme.typography.headlineMedium)
                Text("Multi-track arranger • Piano Roll • Chords • Patterns", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledIconButton(onClick = { if (controller.isPlaying) controller.stopPlayback() else controller.startPlayback(scope) }) {
                    Text(if (controller.isPlaying) "■" else "▶")
                }
                OutlinedButton(onClick = controller::saveProject) { Text("Save") }
            }
        }

        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("BPM ${controller.bpm}", modifier = Modifier.weight(1f))
                    Text("Bars ${controller.bars}", modifier = Modifier.weight(1f))
                    Text(if (controller.loopEnabled) "LOOP" else "ONE SHOT", modifier = Modifier.weight(1f))
                }
                Slider(controller.bpm.toFloat(), { controller.bpm = it.toInt().coerceIn(50, 220) }, valueRange = 50f..220f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(controller.loopEnabled, { controller.loopEnabled = !controller.loopEnabled }, label = { Text("Loop") })
                    Text("Key", modifier = Modifier.padding(start = 8.dp, top = 8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                        itemsIndexed(controller.keys) { _, key -> FilterChip(controller.selectedKey == key, { controller.selectedKey = key }, label = { Text(key) }) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Scale / Maqam", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    itemsIndexed(controller.scales) { _, scale -> FilterChip(controller.selectedScale == scale, { controller.selectedScale = scale }, label = { Text(scale) }) }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("Tracks", style = MaterialTheme.typography.titleMedium)
        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(controller.tracks) { _, track ->
                FilterChip(controller.selectedTrackId == track.id, { controller.selectedTrackId = track.id }, label = { Text(track.name) })
            }
            item { AssistChip(onClick = controller::addTrack, label = { Text("+ Track") }) }
        }

        Spacer(Modifier.height(6.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${controller.selectedTrack.name} • ${controller.selectedTrack.instrument}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = controller::toggleTrackMute) { Text(if (controller.selectedTrack.muted) "M" else "m") }
                    IconButton(onClick = controller::toggleTrackSolo) { Text(if (controller.selectedTrack.solo) "S" else "s") }
                    IconButton(onClick = controller::duplicateTrack) { Text("+") }
                    IconButton(onClick = controller::deleteTrack) { Text("×") }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    itemsIndexed(controller.instruments) { _, instrument ->
                        FilterChip(controller.selectedTrack.instrument == instrument, { controller.setTrackInstrument(instrument) }, label = { Text(instrument, fontSize = 10.sp) })
                    }
                }
                Text("Volume ${(controller.selectedTrack.volume * 100).toInt()}%")
                Slider(controller.selectedTrack.volume, controller::setTrackVolume, valueRange = 0f..1f)
            }
        }

        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { controller.addChord(controller.keyBaseForUi(), 0f) }, modifier = Modifier.weight(1f)) { Text("Add Chord") }
                    OutlinedButton(onClick = controller::applyChordProgression, modifier = Modifier.weight(1f)) { Text("Progression") }
                    OutlinedButton(onClick = controller::clearTrack, modifier = Modifier.weight(1f)) { Text("Clear") }
                }
                Text("Melody Presets", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    itemsIndexed(controller.melodyPresets) { index, p -> AssistChip(onClick = { controller.applyMelodyPreset(index) }, label = { Text(p.name, fontSize = 10.sp) }) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Piano Roll — tap cells to add/remove notes. Multiple notes at the same time form chords.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(gridScroll)) {
            Column {
                Row {
                    Spacer(Modifier.width(48.dp))
                    repeat((controller.loopBeats / 0.5f).toInt()) { i ->
                        Box(Modifier.width(34.dp).height(28.dp).background(if (i % 8 == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Text("${i / 2 + 1}", fontSize = 8.sp)
                        }
                    }
                }
                pitches.forEach { pitch ->
                    Row {
                        Box(Modifier.width(48.dp).height(26.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text(noteName(pitch), fontSize = 9.sp) }
                        repeat((controller.loopBeats / 0.5f).toInt()) { i ->
                            val beat = i * 0.5f
                            val active = controller.selectedTrack.notes.any { it.pitch == pitch && kotlin.math.abs(it.startBeat - beat) < 0.01f }
                            Box(
                                Modifier.width(34.dp).height(26.dp).padding(1.dp)
                                    .background(if (active) MaterialTheme.colorScheme.primary else if (i % 8 == 0) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(4.dp))
                                    .clickable { if (active) controller.removeNote(pitch, beat) else controller.addNote(pitch, beat) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(10.dp)) {
                Text("Rhythm Patterns", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    itemsIndexed(controller.rhythms) { i, r -> FilterChip(controller.selectedRhythmIndex == i, { controller.selectedRhythmIndex = i }, label = { Text(r.name) }) }
                }
                Text("Selected: ${controller.selectedRhythm.name} • percussion follows the loop during playback", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun noteName(pitch: Int): String {
    val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    return "${names[pitch % 12]}${pitch / 12 - 1}"
}

private fun MusicStudioController.keyBaseForUi(): Int = 60 + keys.indexOf(selectedKey).coerceAtLeast(0)
