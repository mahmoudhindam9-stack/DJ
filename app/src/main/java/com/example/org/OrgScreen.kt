package com.example.org

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun OrgScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { OrgEngine(context) }
    val recorder = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) OrgOutputRecorder(context) else null }
    var page by remember { mutableStateOf(0) }
    var accompaniment by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }

    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null && recorder != null) {
            recorder.start(result.dataProjection(context, result.data), scope)
        } else Toast.makeText(context, "Recording permission was not granted", Toast.LENGTH_SHORT).show()
    }

    DisposableEffect(Unit) { onDispose { engine.stopRhythm(); recorder?.stop() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("ORG Workstation", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Arranger • Sound Bank • Rhythm • Pads • Master Recorder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Sounds", "Rhythms", "Pads", "Recorder").forEachIndexed { i, label ->
                FilterChip(selected = page == i, onClick = { page = i }, label = { Text(label) }, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(14.dp))

        when (page) {
            0 -> {
                SectionTitle("Instrument / Voice Bank")
                Text("${engine.voices[engine.voiceIndex].name} • ${engine.voices[engine.voiceIndex].category}", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(engine.voices) { voice ->
                        val idx = engine.voices.indexOf(voice)
                        FilterChip(selected = engine.voiceIndex == idx, onClick = { engine.selectVoice(idx) }, label = { Text(voice.name) })
                    }
                }
                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Direct Voice Trigger", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { engine.triggerVoice() }, Modifier.fillMaxWidth()) { Text("Play ${engine.voices[engine.voiceIndex].name}") }
                        Spacer(Modifier.height(8.dp))
                        Text("Volume ${(engine.volume * 100).toInt()}%")
                        Slider(value = engine.volume, onValueChange = { engine.volume = it }, valueRange = 0f..1f)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Automatic Accompaniment", fontWeight = FontWeight.Bold)
                            Text("Chord-style backing follows the rhythm engine.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = accompaniment, onCheckedChange = { accompaniment = it; engine.accompanimentEnabled = it; if (it && !engine.rhythmEnabled) engine.startRhythm(scope) })
                    }
                }
            }
            1 -> {
                SectionTitle("Rhythm / Arranger")
                Text("${engine.rhythm.name} • ${engine.bpm} BPM", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(engine.rhythms) { r ->
                        val idx = engine.rhythms.indexOf(r)
                        FilterChip(selected = engine.rhythm.name == r.name, onClick = { engine.setRhythm(idx) }, label = { Text(r.name) })
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Tempo: ${engine.bpm} BPM")
                Slider(value = engine.bpm.toFloat(), onValueChange = { engine.setBpm(it.toInt()) }, valueRange = 50f..180f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { engine.startRhythm(scope) }, Modifier.weight(1f)) { Text("START") }
                    OutlinedButton(onClick = { engine.stopRhythm() }, Modifier.weight(1f)) { Text("STOP") }
                }
                Spacer(Modifier.height(12.dp))
                Text(if (engine.rhythmEnabled) "● Rhythm running" else "○ Rhythm stopped", color = if (engine.rhythmEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            2 -> {
                SectionTitle("Performance Pads")
                val labels = listOf("C", "D", "E", "G", "A", "C+", "Stab", "Hit")
                labels.chunked(2).forEachIndexed { row, pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEachIndexed { col, label ->
                            Box(Modifier.weight(1f).height(76.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer).clickable { engine.triggerPad(row * 2 + col) }, contentAlignment = Alignment.Center) {
                                Text(label, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Text("Pads are trigger-style controls only — no piano keyboard.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            3 -> {
                SectionTitle("Record Master Output")
                if (recorder == null) {
                    Text("Master output recording requires Android 10 (API 29) or newer.")
                } else {
                    Text("Records the app's media playback mix through Android AudioPlaybackCapture. Android requires microphone permission and user approval for the capture session.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = {
                        if (recorder.isRecording) recorder.stop() else {
                            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            projectionLauncher.launch(mgr.createScreenCaptureIntent())
                        }
                    }, Modifier.fillMaxWidth()) {
                        Text(if (recorder.isRecording) "STOP RECORDING" else "START RECORDING")
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(if (recorder.isRecording) "● Recording" else "○ Idle", fontWeight = FontWeight.Bold)
                    recorder.lastFile?.let { file ->
                        Spacer(Modifier.height(8.dp))
                        Text("Last saved: ${file.name}", style = MaterialTheme.typography.bodySmall)
                        Text(file.parent ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(14.dp))
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Recording format", fontWeight = FontWeight.Bold)
                            Text("WAV • PCM 16-bit • 48 kHz • Stereo", style = MaterialTheme.typography.bodySmall)
                            Text("Saved inside the app's external files/recordings folder.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)) }

private fun android.app.ActivityResult.resultData(): Intent? = data

private fun Intent?.dataProjection(context: Context, data: Intent?): android.media.projection.MediaProjection {
    requireNotNull(data)
    val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    return mgr.getMediaProjection(Activity.RESULT_OK, data)
}
