package com.example

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.player.AudioPlayerController
import com.example.player.RepeatOption
import com.example.utils.MusicScanner
import com.example.visualizer.AudioVisualizerContainer
import com.example.visualizer.VisualizerMode
import com.example.visualizer.VisualizerModeSelector

@Composable
fun FullPlayerScreen(playerController: AudioPlayerController, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val window = activity?.window

    BackHandler {
        onBack()
    }

    DisposableEffect(window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var lastValidSong by remember { mutableStateOf(playerController.currentSong) }
    LaunchedEffect(playerController.currentSong) {
        if (playerController.currentSong != null) {
            lastValidSong = playerController.currentSong
        }
    }
    val song = playerController.currentSong
        ?: playerController.playlist.getOrNull(playerController.currentSongIndex.coerceAtLeast(0))
        ?: lastValidSong
    if (song == null) {
        onBack()
        return
    }

    // Toggleable HUD overlay for pure immersive visualizer experience
    var isOverlayVisible by remember { mutableStateOf(true) }
    val currentMode = playerController.visualizerMode

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isOverlayVisible = !isOverlayVisible
            }
    ) {
        // 1. 100% Screen Visualizer (Takes the entire display)
        AudioVisualizerContainer(
            playerController = playerController,
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            showSelector = false,
            showBadge = false
        )

        // 2. Floating Immersive HUD Overlay
        AnimatedVisibility(
            visible = isOverlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                // Top Floating Glass Pill: Song Info & Exit Button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = currentMode.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Exit full screen",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Bottom Floating Control Stack
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Visualizer Mode Selector Pill (Scrollable for all 7 high-fidelity modes)
                    VisualizerModeSelector(
                        currentMode = currentMode,
                        onModeSelected = { newMode ->
                            playerController.visualizerMode = newMode
                        }
                    )

                    // Floating Glass Playback Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                        tonalElevation = 6.dp,
                        shadowElevation = 10.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            // Progress Slider & Timestamps
                            var isUserSeeking by remember { mutableStateOf(false) }
                            var userSeekPos by remember { mutableFloatStateOf(0f) }
                            val maxPos = if (playerController.durationMs > 0L) playerController.durationMs.toFloat() else 1f
                            val currentPos = if (isUserSeeking) userSeekPos else playerController.currentPositionMs.toFloat().coerceIn(0f, maxPos)

                            Slider(
                                value = currentPos,
                                onValueChange = {
                                    isUserSeeking = true
                                    userSeekPos = it
                                    playerController.seekTo(it.toLong())
                                },
                                onValueChangeFinished = {
                                    playerController.seekTo(userSeekPos.toLong())
                                    isUserSeeking = false
                                },
                                valueRange = 0f..maxPos,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = MusicScanner.formatMs(playerController.currentPositionMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = MusicScanner.formatMs(playerController.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Playback Controls Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val shuffleTint = if (playerController.isShuffle) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                IconButton(
                                    onClick = { playerController.toggleShuffle() },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = shuffleTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { playerController.playPrevious() },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.SkipPrevious,
                                        contentDescription = "Previous",
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                // Play/Pause Floating Button
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .clickable { playerController.togglePlayPause() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (playerController.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { playerController.playNext() },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.SkipNext,
                                        contentDescription = "Next",
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                val repeatIcon = when (playerController.repeatOption) {
                                    RepeatOption.ONE -> Icons.Filled.RepeatOne
                                    else -> Icons.Filled.Repeat
                                }
                                val repeatTint = if (playerController.repeatOption == RepeatOption.OFF) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                                IconButton(
                                    onClick = { playerController.toggleRepeat() },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        repeatIcon,
                                        contentDescription = "Repeat",
                                        tint = repeatTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
