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

@Composable
// DJ_AUDIO_CARD_V2
fun DJMixerScreen(
    djMixerController: DJMixerController,
    djFxController: com.example.djfx.DjFxController,
    audioLibrary: SnapshotStateList<AudioItem>,
    micController: MicController,
    onPauseMainPlayer: () -> Unit
) {
    val context = LocalContext.current
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            // Without this, the read grant for this file only lasts for the
            // current process — the song would vanish (fail to load) the next
            // time the app is opened even though it's still listed.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val audio = MusicScanner.parsePickedUri(context, uri)
            if (audioLibrary.none { it.uri == uri }) {
                audioLibrary.add(audio)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Pro DJ Mixer",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Dual deck control & FX",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // DJ_AUDIO_CARD_V2
        val routingScope = rememberCoroutineScope()
        var djInputExpanded by remember { mutableStateOf(false) }
        var djOutputExpanded by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
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
                    OutlinedButton(
                        onClick = { djInputExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(micController.selectedInputDevice?.displayName() ?: "System Default Mic", maxLines = 1)
                    }
                    DropdownMenu(
                        expanded = djInputExpanded,
                        onDismissRequest = { djInputExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("System Default Mic") },
                            onClick = {
                                micController.selectInputDevice(null, routingScope)
                                djInputExpanded = false
                            }
                        )
                        micController.inputDevices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text(device.displayName()) },
                                onClick = {
                                    micController.selectInputDevice(device, routingScope)
                                    djInputExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                Text("OUTPUT • Master", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { djOutputExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(micController.selectedOutputDevice?.displayName() ?: "System Default Output", maxLines = 1)
                    }
                    DropdownMenu(
                        expanded = djOutputExpanded,
                        onDismissRequest = { djOutputExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("System Default Output") },
                            onClick = {
                                micController.selectOutputDevice(null)
                                djOutputExpanded = false
                            }
                        )
                        micController.outputDevices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text(device.displayName()) },
                                onClick = {
                                    micController.selectOutputDevice(device)
                                    djOutputExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        micController.routingStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // Crossfader Control Section (Moved to top)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("DECK A (${((1f - djMixerController.crossfader) * 100).toInt()}%)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("CROSSFADER", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("DECK B (${(djMixerController.crossfader * 100).toInt()}%)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                Slider(
                    value = djMixerController.crossfader,
                    onValueChange = { djMixerController.updateCrossfader(it) },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dual Decks A & B
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Deck A
            DJDeckItem(
                modifier = Modifier.weight(1f),
                deck = djMixerController.deckA,
                audioLibrary = audioLibrary,
                onImportClicked = { pickerLauncher.launch(arrayOf("audio/*")) },
                onPlayStarted = onPauseMainPlayer
            )

            // Deck B
            DJDeckItem(
                modifier = Modifier.weight(1f),
                deck = djMixerController.deckB,
                audioLibrary = audioLibrary,
                onImportClicked = { pickerLauncher.launch(arrayOf("audio/*")) },
                onPlayStarted = onPauseMainPlayer
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        com.example.djfx.DjFxBoard(controller = djFxController)

    }
}

@Composable
fun DJDeckItem(
    modifier: Modifier = Modifier,
    deck: DJDeckController,
    audioLibrary: List<AudioItem>,
    onImportClicked: () -> Unit,
    onPlayStarted: () -> Unit
) {
    var showTrackSelector by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxHeight(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = deck.deckName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Track Title
            Button(
                onClick = { showTrackSelector = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = deck.currentSong?.title ?: "Select Track",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Play / Cue Button
            FilledIconButton(
                onClick = {
                    deck.togglePlay(); if (deck.isPlaying) onPlayStarted()
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (deck.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause"
                )
            }

            // Seek & Progress Control
            LaunchedEffect(deck.isPlaying) {
                while (true) {
                    deck.updateProgress()
                    kotlinx.coroutines.delay(200L)
                }
            }

            val maxPos = if (deck.durationMs > 0L) deck.durationMs.toFloat() else 1f
            var isUserSeeking by remember { mutableStateOf(false) }
            var userSeekPos by remember { mutableFloatStateOf(0f) }

            val currentPos = if (isUserSeeking) userSeekPos else deck.currentPositionMs.toFloat().coerceIn(0f, maxPos)

            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = currentPos,
                onValueChange = { newPos ->
                    isUserSeeking = true
                    userSeekPos = newPos
                    deck.seekTo(newPos.toLong())
                },
                onValueChangeFinished = {
                    deck.seekTo(userSeekPos.toLong())
                    isUserSeeking = false
                },
                valueRange = 0f..maxPos,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = com.example.utils.MusicScanner.formatMs(deck.currentPositionMs),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.widthIn(min = 44.dp),
                    maxLines = 1
                )
                OutlinedButton(
                    onClick = {
                        val newPos = (deck.currentPositionMs - 5000L).coerceAtLeast(0L)
                        deck.seekTo(newPos)
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("-5s", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = {
                        val newPos = (deck.currentPositionMs + 5000L).coerceAtMost(deck.durationMs)
                        deck.seekTo(newPos)
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("+5s", style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    com.example.utils.MusicScanner.formatMs(deck.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.widthIn(min = 44.dp),
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pitch / Speed Slider (+/- 50%)
            Text(
                text = "Speed: ${String.format("%.2fx", deck.pitch)}",
                style = MaterialTheme.typography.labelSmall
            )
            Slider(
                value = deck.pitch,
                onValueChange = { deck.setPlaybackPitch(it) },
                valueRange = 0.5f..1.5f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Deck Volume
            Text(
                text = "Volume: ${(deck.volume * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall
            )
            Slider(
                value = deck.volume,
                onValueChange = { deck.setVolumeLevel(it) },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // DJ_FX_RACK_V2
            // Replace the four legacy pads with the full Mixxx-style rack so the
            // professional effects are visible directly inside each DJ deck.
            DJFxRack(deck)
        }
    }

    if (showTrackSelector) {
        AlertDialog(
            onDismissRequest = { showTrackSelector = false },
            title = { Text("Select Track for ${deck.deckName}") },
            text = {
                if (audioLibrary.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No audio tracks available in library.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            showTrackSelector = false
                            onImportClicked()
                        }) {
                            Text("Import Audio Files")
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(audioLibrary, key = { it.id }) { track ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        deck.loadTrack(track)
                                        showTrackSelector = false
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(track.title, style = MaterialTheme.typography.titleSmall)
                                    Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrackSelector = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun DJPadButton(label: String, isActive: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.size(width = 65.dp, height = 36.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}