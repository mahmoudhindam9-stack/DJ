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
fun FullPlayerScreen(playerController: AudioPlayerController, onBack: () -> Unit) {
    val song = playerController.currentSong
    if (song == null) {
        onBack()
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Large Album Art / Visualizer Placeholder
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Album, contentDescription = null, modifier = Modifier.size(128.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(song.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Controls
        val maxPos = if (playerController.durationMs > 0) playerController.durationMs.toFloat() else 1f
        val currentPos = playerController.currentPositionMs.toFloat().coerceIn(0f, maxPos)

        Slider(
            value = currentPos,
            onValueChange = { playerController.seekTo(it.toLong()) },
            valueRange = 0f..maxPos,
            modifier = Modifier.fillMaxWidth()
        )
        
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(MusicScanner.formatMs(playerController.currentPositionMs))
            Text(MusicScanner.formatMs(playerController.durationMs))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
            IconButton(onClick = { playerController.toggleShuffle() }) {
                Icon(Icons.Filled.Shuffle, contentDescription = null, tint = if (playerController.isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { playerController.playPrevious() }) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = null, modifier = Modifier.size(48.dp))
            }
            FloatingActionButton(
                onClick = { playerController.togglePlayPause() },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(if (playerController.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = { playerController.playNext() }) {
                Icon(Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(48.dp))
            }
            IconButton(onClick = { playerController.toggleRepeat() }) {
                Icon(Icons.Filled.Repeat, contentDescription = null, tint = if (playerController.repeatOption != RepeatOption.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun NowPlayingCard(
    playerController: AudioPlayerController, 
    onPauseDJ: () -> Unit,
    onClick: () -> Unit
) {
    val song = playerController.currentSong ?: return

    // Rotating vinyl animation when playing
    val infiniteTransition = rememberInfiniteTransition(label = "VinylRotate")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Vinyl Record Visualizer
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .rotate(if (playerController.isPlaying) rotationAngle else 0f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Album,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Seekbar Slider
            val maxPos = if (playerController.durationMs > 0) playerController.durationMs.toFloat() else 1f
            val currentPos = playerController.currentPositionMs.toFloat().coerceIn(0f, maxPos)

            Slider(
                value = currentPos,
                onValueChange = { newPos ->
                    playerController.seekTo(newPos.toLong())
                },
                valueRange = 0f..maxPos,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = MusicScanner.formatMs(playerController.currentPositionMs),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = MusicScanner.formatMs(playerController.durationMs),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Playback Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Toggle
                IconButton(onClick = { playerController.toggleShuffle() }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playerController.isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Previous
                IconButton(onClick = {
                    onPauseDJ()
                    playerController.playPrevious()
                }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
                }

                // Play / Pause Toggle
                FilledIconButton(
                    onClick = {
                        onPauseDJ()
                        playerController.togglePlayPause()
                    },
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        imageVector = if (playerController.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Next
                IconButton(onClick = {
                    onPauseDJ()
                    playerController.playNext()
                }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
                }

                // Repeat Mode Toggle
                IconButton(onClick = { playerController.toggleRepeat() }) {
                    val (icon, tint) = when (playerController.repeatOption) {
                        RepeatOption.OFF -> Icons.Filled.Repeat to MaterialTheme.colorScheme.onSurface
                        RepeatOption.ALL -> Icons.Filled.Repeat to MaterialTheme.colorScheme.primary
                        RepeatOption.ONE -> Icons.Filled.RepeatOne to MaterialTheme.colorScheme.primary
                    }
                    Icon(icon, contentDescription = "Repeat", tint = tint)
                }
            }
        }
    }
}