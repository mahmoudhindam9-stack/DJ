package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioItem
import com.example.player.DJDeckController
import com.example.player.DJMixerController
import com.example.djfx.DjFxController
import com.example.player.MicController
import com.example.ui.components.DjSurfaceCard
import com.example.ui.components.DjSectionHeader
import com.example.utils.MusicScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DJMixerScreen(
    djMixerController: DJMixerController,
    djFxController: DjFxController,
    audioLibrary: SnapshotStateList<AudioItem>,
    micController: MicController,
    onPauseMainPlayer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deckA = djMixerController.deckA
    val deckB = djMixerController.deckB

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val songs = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    runCatching { MusicScanner.parsePickedUri(context, uri) }.getOrNull()
                }
            }
            val existing = audioLibrary.map { it.uri }.toHashSet()
            songs.forEach { song ->
                if (existing.add(song.uri)) audioLibrary.add(song)
            }
            Toast.makeText(context, "Added ${songs.size} song(s)", Toast.LENGTH_SHORT).show()
        }
    }
    
    val onImportClicked = { filePicker.launch(arrayOf("audio/*")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DJ STUDIO MIXER",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f)
            )
        }

        // Crossfader
        DjSurfaceCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "CROSSFADER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("A", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    var crossfader by remember { mutableFloatStateOf(0.5f) }
                    
                    Slider(
                        value = crossfader,
                        onValueChange = { 
                            crossfader = it 
                            djMixerController.updateCrossfader(it)
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.tertiary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("B", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        // Decks
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                DJDeck(deckA, audioLibrary, onImportClicked, onPauseMainPlayer, MaterialTheme.colorScheme.primary)
            }
            Box(modifier = Modifier.weight(1f)) {
                DJDeck(deckB, audioLibrary, onImportClicked, onPauseMainPlayer, MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun DJDeck(
    deck: DJDeckController,
    audioLibrary: SnapshotStateList<AudioItem>,
    onImportClicked: () -> Unit,
    onPlayStarted: () -> Unit,
    accentColor: Color
) {
    var showTrackSelector by remember { mutableStateOf(false) }

    DjSurfaceCard(
        modifier = Modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        borderColor = accentColor.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Deck Label
            Text(
                text = deck.deckName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = accentColor,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Track Loading Screen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { showTrackSelector = true }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = deck.currentSong?.title ?: "LOAD TRACK",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (deck.currentSong != null) MaterialTheme.colorScheme.onSurface else accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (deck.currentSong != null) {
                        Text(
                            text = deck.currentSong!!.artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cue
                Button(
                    onClick = { 
                        deck.seekTo(0L)
                        if (deck.isPlaying) deck.togglePlay()
                    },
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor.copy(alpha = 0.2f),
                        contentColor = accentColor
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("CUE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                // Play/Pause
                Button(
                    onClick = { 
                        deck.togglePlay()
                        if (deck.isPlaying) onPlayStarted()
                    },
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (deck.isPlaying) accentColor else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (deck.isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = if (deck.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Time & Seek
            LaunchedEffect(deck.isPlaying) {
                if (deck.isPlaying) {
                    while (true) {
                        deck.updateProgress()
                        kotlinx.coroutines.delay(100L)
                    }
                }
            }
            
            val maxPos = if (deck.durationMs > 0L) deck.durationMs.toFloat() else 1f
            var isUserSeeking by remember { mutableStateOf(false) }
            var userSeekPos by remember { mutableFloatStateOf(0f) }
            val currentPos = if (isUserSeeking) userSeekPos else deck.currentPositionMs.toFloat().coerceIn(0f, maxPos)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = com.example.utils.MusicScanner.formatMs(deck.currentPositionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = com.example.utils.MusicScanner.formatMs(deck.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Slider(
                value = currentPos,
                onValueChange = { 
                    isUserSeeking = true
                    userSeekPos = it 
                    deck.seekTo(it.toLong())
                },
                onValueChangeFinished = {
                    deck.seekTo(userSeekPos.toLong())
                    isUserSeeking = false
                },
                valueRange = 0f..maxPos,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = accentColor.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth().height(24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Pitch Control
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("PITCH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(String.format("%.1f%%", (deck.pitch - 1f) * 100f), style = MaterialTheme.typography.labelSmall, color = accentColor, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = deck.pitch,
                onValueChange = { deck.setPlaybackPitch(it) },
                valueRange = 0.5f..1.5f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.tertiary,
                    activeTrackColor = MaterialTheme.colorScheme.tertiary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth().height(24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))

            // FX Rack inside deck
            DJFxRack(deck)
        }
    }

    if (showTrackSelector) {
        AlertDialog(
            onDismissRequest = { showTrackSelector = false },
            title = { Text("Load Track - ${deck.deckName}") },
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                if (audioLibrary.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No tracks available in library.")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            showTrackSelector = false
                            onImportClicked()
                        }) {
                            Text("Import Audio Files")
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(audioLibrary, key = { it.id }) { track ->
                            DjSurfaceCard(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                onClick = {
                                    deck.loadTrack(track)
                                    showTrackSelector = false
                                }
                            ) {
                                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                                    Text(track.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
