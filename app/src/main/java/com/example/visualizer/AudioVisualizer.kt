package com.example.visualizer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.AudioPlayerController
import kotlin.math.*

@Composable
fun AudioVisualizerContainer(
    playerController: AudioPlayerController,
    modifier: Modifier = Modifier,
    artworkContent: (@Composable () -> Unit)? = null
) {
    val visualizerState by playerController.visualizerEngine.state.collectAsState()
    val mode = playerController.visualizerMode

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when (mode) {
            VisualizerMode.OFF -> {
                if (artworkContent != null) {
                    artworkContent()
                } else {
                    DefaultArtworkPlaceholder()
                }
            }
            VisualizerMode.SPECTRUM -> {
                SpectrumVisualizer(
                    state = visualizerState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp)
                )
            }
            VisualizerMode.CIRCULAR -> {
                CircularVisualizer(
                    state = visualizerState,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
            VisualizerMode.WAVE -> {
                WaveVisualizer(
                    state = visualizerState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp)
                )
            }
        }

        // Mode selector overlay at the bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            VisualizerModeSelector(
                currentMode = mode,
                onModeSelected = { newMode ->
                    playerController.visualizerMode = newMode
                }
            )
        }
    }
}

@Composable
fun DefaultArtworkPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Album,
            contentDescription = "Album Artwork",
            modifier = Modifier.size(160.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun SpectrumVisualizer(
    state: AudioVisualizerState,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier) {
        val barCount = state.bands.size.coerceAtMost(32)
        if (barCount == 0) return@Canvas

        val canvasWidth = size.width
        val canvasHeight = size.height

        val totalGap = canvasWidth * 0.25f
        val gap = totalGap / (barCount + 1)
        val barWidth = (canvasWidth - totalGap) / barCount

        val gradient = Brush.verticalGradient(
            colors = listOf(
                tertiaryColor,
                secondaryColor,
                primaryColor
            ),
            startY = 0f,
            endY = canvasHeight
        )

        for (i in 0 until barCount) {
            val magnitude = state.bands[i].coerceIn(0.02f, 1f)
            val barHeight = (canvasHeight * 0.88f * magnitude).coerceAtLeast(4.dp.toPx())
            val x = gap + i * (barWidth + gap)
            val y = canvasHeight - barHeight

            // Draw frequency bar
            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth * 0.5f, barWidth * 0.5f)
            )

            // Draw floating peak indicator dot on top
            val peakY = (y - 5.dp.toPx()).coerceAtLeast(0f)
            drawCircle(
                color = tertiaryColor.copy(alpha = 0.9f),
                radius = (barWidth * 0.35f).coerceAtLeast(1.5f),
                center = Offset(x + barWidth / 2f, peakY)
            )
        }
    }
}

@Composable
fun CircularVisualizer(
    state: AudioVisualizerState,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val minDim = min(size.width, size.height)

        // Pulsing base circle radius driven by energy and bass
        val pulse = 1f + (state.energy * 0.22f) + (state.bass * 0.28f)
        val baseRadius = (minDim * 0.20f) * pulse
        val maxRayLength = minDim * 0.25f

        // Center glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = (0.28f + state.bass * 0.35f).coerceIn(0f, 0.75f)),
                    secondaryColor.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = baseRadius * 1.5f
            ),
            radius = baseRadius * 1.5f,
            center = Offset(centerX, centerY)
        )

        // Core base ring
        drawCircle(
            color = primaryColor.copy(alpha = 0.85f),
            radius = baseRadius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 3.dp.toPx())
        )

        // Radial frequency rays (64 rays mirrored for symmetry)
        val rayCount = 64
        val halfBands = state.bands.size.coerceAtMost(32)

        for (i in 0 until rayCount) {
            val bandIdx = if (i < 32) i else (63 - i)
            val mag = (state.bands.getOrElse(bandIdx) { 0f }).coerceIn(0.04f, 1f)
            val rayLength = maxRayLength * mag

            val angleRad = (2.0 * Math.PI * i / rayCount).toFloat() - (Math.PI / 2.0).toFloat()
            val cosA = cos(angleRad)
            val sinA = sin(angleRad)

            val startX = centerX + cosA * baseRadius
            val startY = centerY + sinA * baseRadius
            val endX = centerX + cosA * (baseRadius + rayLength)
            val endY = centerY + sinA * (baseRadius + rayLength)

            val rayColor = if (bandIdx < 10) {
                primaryColor
            } else if (bandIdx < 22) {
                secondaryColor
            } else {
                tertiaryColor
            }

            drawLine(
                color = rayColor.copy(alpha = (0.55f + mag * 0.45f).coerceIn(0f, 1f)),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun WaveVisualizer(
    state: AudioVisualizerState,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        val wavePoints = state.wave
        val count = wavePoints.size
        if (count < 2) return@Canvas

        // Amplitude driven by audio energy and peak
        val ampMultiplier = height * 0.40f * (0.35f + state.energy * 0.65f)

        val stepX = width / (count - 1)

        val mainPath = Path()
        val glowPath = Path()

        for (i in 0 until count) {
            val x = i * stepX
            val rawY = wavePoints[i]
            val y = centerY - (rawY * ampMultiplier).coerceIn(-height * 0.45f, height * 0.45f)

            if (i == 0) {
                mainPath.moveTo(x, y)
                glowPath.moveTo(x, y)
            } else {
                val prevX = (i - 1) * stepX
                val prevY = centerY - (wavePoints[i - 1] * ampMultiplier).coerceIn(-height * 0.45f, height * 0.45f)
                val midX = (prevX + x) / 2f
                mainPath.quadraticTo(prevX, prevY, midX, (prevY + y) / 2f)
                glowPath.quadraticTo(prevX, prevY, midX, (prevY + y) / 2f)
            }
        }

        // Draw center baseline (subtle)
        drawLine(
            color = primaryColor.copy(alpha = 0.15f),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1.dp.toPx()
        )

        // Draw background glow wave
        drawPath(
            path = glowPath,
            color = secondaryColor.copy(alpha = 0.35f),
            style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Draw sharp foreground wave
        drawPath(
            path = mainPath,
            color = primaryColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun VisualizerModeSelector(
    currentMode: VisualizerMode,
    onModeSelected: (VisualizerMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VisualizerMode.values().forEach { mode ->
                val isSelected = (mode == currentMode)
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = tween(200),
                    label = "mode_bg"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "mode_content"
                )

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(bgColor)
                        .clickable { onModeSelected(mode) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
