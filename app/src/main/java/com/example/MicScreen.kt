package com.example

import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.utils.MusicScanner
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.rotate
import android.Manifest

import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.*
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.layout.ContentScale
import com.example.model.*
import com.example.player.*
import kotlinx.coroutines.*

// KARAOKE_MIC_PAGE_V5
@Composable
fun MicScreen(micController: MicController, scope: kotlinx.coroutines.CoroutineScope) {
    val context = LocalContext.current
    var inputExpanded by remember { mutableStateOf(false) }
    var outputExpanded by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) micController.toggleMic(true, scope)
        else Toast.makeText(context, "Microphone permission is required", Toast.LENGTH_SHORT).show()
    }
    val saveRecordingLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
        if (uri == null) micController.discardPendingRecording()
        else scope.launch {
            val ok = withContext(kotlinx.coroutines.Dispatchers.IO) { micController.savePendingRecording(uri) }
            Toast.makeText(context, if (ok) "Recording saved" else "Unable to save recording", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Karaoke Studio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Live vocal monitor with DJ-style effects", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier.size(116.dp).clip(CircleShape)
                .background(if (micController.isMicEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    if (micController.isMicEnabled) micController.toggleMic(false, scope)
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(if (micController.isMicEnabled) Icons.Filled.Mic else Icons.Filled.MicOff, null, Modifier.size(46.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(if (micController.isMicEnabled) "LIVE MONITOR ON" else "Tap to enable microphone", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Audio Card", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "DJ Input / Master Output",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { micController.refreshDevices() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh audio devices")
                    }
                }
                Spacer(Modifier.height(8.dp))

                Text("INPUT • Microphone", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { inputExpanded = true }, Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(micController.selectedInputDevice?.displayName() ?: "System Default Mic", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    DropdownMenu(inputExpanded, { inputExpanded = false }) {
                        DropdownMenuItem(leadingIcon = { Icon(Icons.Filled.Mic, null) }, text = { Text("System Default Mic") }, onClick = { micController.selectInputDevice(null, scope); inputExpanded = false })
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) micController.inputDevices.forEach { device ->
                            DropdownMenuItem(leadingIcon = { Icon(Icons.Filled.Mic, null) }, text = { Text(device.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { micController.selectInputDevice(device, scope); inputExpanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                Text("OUTPUT • Master", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { outputExpanded = true }, Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(micController.selectedOutputDevice?.displayName() ?: "System Default Output", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    DropdownMenu(outputExpanded, { outputExpanded = false }) {
                        DropdownMenuItem(leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, null) }, text = { Text("System Default Output") }, onClick = { micController.selectOutputDevice(null); outputExpanded = false })
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) micController.outputDevices.forEach { device ->
                            DropdownMenuItem(leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, null) }, text = { Text(device.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { micController.selectOutputDevice(device); outputExpanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${micController.routingStatus}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Mix & FX Amount", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("Mic Volume: ${(micController.micVolume * 100).toInt()}%")
                Slider(micController.micVolume, { micController.micVolume = it }, valueRange = 0f..2f)
                Text("Echo: ${(micController.echoLevel * 100).toInt()}%")
                Slider(micController.echoLevel, { micController.echoLevel = it }, valueRange = 0f..1f)
                Text("Reverb: ${(micController.reverbLevel * 100).toInt()}%")
                Slider(micController.reverbLevel, { micController.reverbLevel = it }, valueRange = 0f..1f)
                Text("Flanger: ${(micController.flangerMix * 100).toInt()}%")
                Slider(micController.flangerMix, { micController.flangerMix = it }, valueRange = 0f..1f)
                Text("Filter: ${(micController.filterMix * 100).toInt()}%")
                Slider(micController.filterMix, { micController.filterMix = it }, valueRange = 0f..1f)
                Spacer(Modifier.height(4.dp))
                Text("Vocal Filters", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MicFilter.values().toList()) { filter -> FilterChip(filter == micController.currentFilter, { micController.currentFilter = filter }, label = { Text(filter.displayName) }) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Beat FX", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("BPM: ${micController.bpm.toInt()}")
                Slider(micController.bpm, { micController.bpm = it }, valueRange = 70f..180f)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(BeatFxDivision.values().toList()) { div -> FilterChip(div == micController.beatFxDivision, { micController.beatFxDivision = div }, label = { Text(div.displayName) }) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AEC & Noise Suppression", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Live echo cancellation and noise cleanup", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = micController.voiceProcessingEnabled, onCheckedChange = micController::toggleVoiceProcessing)
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FiberManualRecord, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Recording", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Record the processed microphone output", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(micController.recordingDurationText, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    enabled = micController.isMicEnabled || micController.isOutputRecording,
                    onClick = {
                        if (micController.isOutputRecording) {
                            if (micController.stopOutputRecording()) saveRecordingLauncher.launch(micController.suggestedRecordingName())
                        } else if (micController.startOutputRecording()) Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(if (micController.isOutputRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text(if (micController.isOutputRecording) "Stop & Save" else "Start Recording")
                }
                Text(micController.recordingStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(if (micController.isMicEnabled) "Microphone monitor is active" else "Microphone monitor is off", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}