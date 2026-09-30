package com.example.visualizer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
    val transition = rememberInfiniteTransition(label = "visualizer-motion")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "visualizer-phase"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        VisualizerBackdrop(visualizerState, phase, Modifier.fillMaxSize())

        when (mode) {
            VisualizerMode.OFF -> {
                if (artworkContent != null) artworkContent() else DefaultArtworkPlaceholder()
            }
            VisualizerMode.SPECTRUM -> {
                SpectrumVisualizer(
                    state = visualizerState,
                    phase = phase,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 16.dp)
                )
            }
            VisualizerMode.CIRCULAR -> {
                CircularVisualizer(
                    state = visualizerState,
                    phase = phase,
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                )
            }
            VisualizerMode.WAVE -> {
                WaveVisualizer(
                    state = visualizerState,
                    phase = phase,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 18.dp)
                )
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
            tonalElevation = 2.dp
        ) {
            Text(
                text = when (mode) {
                    VisualizerMode.OFF -> "ARTWORK"
                    else -> "LIVE • " + mode.label.uppercase()
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Box(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
        ) {
            VisualizerModeSelector(
                currentMode = mode,
                onModeSelected = { newMode -> playerController.visualizerMode = newMode }
            )
        }
    }
}

@Composable
private fun VisualizerBackdrop(
    state: AudioVisualizerState,
    phase: Float,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val energy = state.energy
        val beat = state.beat
        val bass = state.bass

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = 0.15f + energy * 0.18f),
                    secondary.copy(alpha = 0.08f + bass * 0.10f),
                    Color.Transparent
                ),
                center = center,
                radius = max(size.width, size.height) * (0.44f + energy * 0.10f)
            )
        )

        val pulse = 1f + beat * 0.18f + energy * 0.05f
        val orbit = min(size.width, size.height) * (0.30f + beat * 0.07f)
        drawCircle(
            color = tertiary.copy(alpha = 0.045f + beat * 0.10f),
            radius = orbit * pulse,
            center = center
        )
        drawCircle(
            color = primary.copy(alpha = 0.035f + energy * 0.07f),
            radius = orbit * 1.55f * pulse,
            center = center
        )

        repeat(18) { i ->
            val angle = phase * (0.32f + i * 0.009f) + i * 0.349f
            val radius = min(size.width, size.height) * (0.28f + (i % 5) * 0.035f)
            val particle = 1.2.dp.toPx() + (i % 4) * 0.65.dp.toPx()
            val particleColor = when (i % 3) {
                0 -> primary
                1 -> secondary
                else -> tertiary
            }
            drawCircle(
                color = particleColor.copy(
                    alpha = (0.08f + energy * 0.22f + beat * 0.18f).coerceIn(0f, 0.42f)
                ),
                radius = particle * (1f + beat * 0.7f),
                center = Offset(
                    center.x + cos(angle) * radius,
                    center.y + sin(angle) * radius
                )
            )
        }
    }
}

@Composable
fun DefaultArtworkPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
    phase: Float,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        val barCount = state.bands.size.coerceAtMost(32)
        if (barCount == 0) return@Canvas

        val width = size.width
        val height = size.height
        val gap = (width * 0.008f).coerceAtLeast(2.dp.toPx())
        val barWidth = ((width - gap * (barCount - 1)) / barCount).coerceAtLeast(2.dp.toPx())
        val baseY = height * 0.96f
        val beatScale = 1f + state.beat * 0.18f

        drawRoundRect(
            color = primary.copy(alpha = 0.07f + state.energy * 0.07f),
            topLeft = Offset(0f, baseY - 3.dp.toPx()),
            size = Size(width, 6.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx())
        )

        for (i in 0 until barCount) {
            val value = state.bands[i].coerceIn(0f, 1f)
            val shimmer = 1f + 0.06f * sin(phase * 1.5f + i * 0.55f)
            val barHeight = (height * 0.80f * value * beatScale * shimmer)
                .coerceIn(5.dp.toPx(), height * 0.88f)
            val x = i * (barWidth + gap)
            val y = baseY - barHeight
            val glowWidth = barWidth * 1.5f

            drawRoundRect(
                color = secondary.copy(alpha = 0.08f + value * 0.20f + state.beat * 0.06f),
                topLeft = Offset(x - (glowWidth - barWidth) / 2f, y - 2.dp.toPx()),
                size = Size(glowWidth, barHeight + 4.dp.toPx()),
                cornerRadius = CornerRadius(glowWidth / 2f, glowWidth / 2f)
            )
            drawRoundRect(
                brush = Brush.verticalGradient(colors = listOf(tertiary, secondary, primary)),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
            drawRoundRect(
                color = primary.copy(alpha = 0.06f + value * 0.08f),
                topLeft = Offset(x, baseY + 2.dp.toPx()),
                size = Size(barWidth, min(height * 0.16f, barHeight * 0.18f)),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
            val capY = (y - 5.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(
                color = tertiary.copy(alpha = 0.70f + state.beat * 0.26f),
                topLeft = Offset(x + barWidth * 0.18f, capY),
                size = Size(barWidth * 0.64f, 3.dp.toPx()),
                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )
        }
    }
}

@Composable
fun CircularVisualizer(
    state: AudioVisualizerState,
    phase: Float,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val minDim = min(size.width, size.height)
        val energy = state.energy
        val beat = state.beat
        val baseRadius = minDim * (0.15f + energy * 0.03f)
        val pulse = 1f + beat * 0.32f + state.bass * 0.12f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = 0.24f + beat * 0.24f),
                    secondary.copy(alpha = 0.08f + energy * 0.10f),
                    Color.Transparent
                ),
                center = center,
                radius = baseRadius * 3.4f
            ),
            radius = baseRadius * 3.4f,
            center = center
        )

        val outerHalo = baseRadius * (1.55f + beat * 0.12f)
        val midHalo = baseRadius * (1.22f + energy * 0.08f)
        drawCircle(
            color = tertiary.copy(alpha = 0.15f + energy * 0.11f),
            radius = outerHalo,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = secondary.copy(alpha = 0.30f + beat * 0.17f),
            radius = midHalo,
            center = center,
            style = Stroke(width = 2.5.dp.toPx())
        )
        drawCircle(
            color = primary.copy(alpha = 0.94f),
            radius = baseRadius,
            center = center,
            style = Stroke(width = 4.dp.toPx())
        )

        val rays = 96
        for (i in 0 until rays) {
            val bandIndex = (i * state.bands.size / rays).coerceIn(0, state.bands.lastIndex)
            val magnitude = state.bands[bandIndex].coerceIn(0f, 1f)
            val angle = phase * 0.12f + (2f * Math.PI.toFloat() * i / rays)
            val startRadius = baseRadius * (1.12f + beat * 0.06f)
            val length = minDim * (0.08f + magnitude * 0.30f) * pulse
            val start = Offset(
                center.x + cos(angle) * startRadius,
                center.y + sin(angle) * startRadius
            )
            val end = Offset(
                center.x + cos(angle) * (startRadius + length),
                center.y + sin(angle) * (startRadius + length)
            )
            val rayColor = when {
                bandIndex < 8 -> primary
                bandIndex < 20 -> secondary
                else -> tertiary
            }
            drawLine(
                color = rayColor.copy(alpha = 0.22f + magnitude * 0.70f),
                start = start,
                end = end,
                strokeWidth = (1.2.dp.toPx() + magnitude * 2.dp.toPx()).coerceAtMost(4.dp.toPx()),
                cap = StrokeCap.Round
            )
        }

        repeat(24) { i ->
            val angle = phase * (0.10f + i * 0.004f) + i * (Math.PI * 2.0 / 24.0).toFloat()
            val orbit = minDim * (0.31f + (i % 4) * 0.028f)
            val particle = (1.3.dp.toPx() + (i % 3) * 0.75.dp.toPx()) * (1f + beat)
            val particleColor = when (i % 3) {
                0 -> primary
                1 -> secondary
                else -> tertiary
            }
            drawCircle(
                color = particleColor.copy(alpha = 0.12f + energy * 0.30f + beat * 0.18f),
                radius = particle,
                center = Offset(
                    center.x + cos(angle) * orbit,
                    center.y + sin(angle) * orbit
                )
            )
        }

        drawArc(
            color = tertiary.copy(alpha = 0.72f + beat * 0.22f),
            startAngle = phase * 57.3f,
            sweepAngle = 88f + beat * 35f,
            useCenter = false,
            topLeft = Offset(center.x - outerHalo, center.y - outerHalo),
            size = Size(outerHalo * 2f, outerHalo * 2f),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun WaveVisualizer(
    state: AudioVisualizerState,
    phase: Float,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val points = state.wave
        val count = points.size
        if (count < 2) return@Canvas

        val amplitude = height * (0.24f + state.energy * 0.22f + state.beat * 0.08f)
        val stepX = width / (count - 1)
        val mainPath = Path()
        val mirrorPath = Path()
        val fillPath = Path()

        for (i in 0 until count) {
            val x = i * stepX
            val normalized = points[i].coerceIn(-1f, 1f)
            val offset = normalized * amplitude
            val y = (centerY - offset).coerceIn(height * 0.10f, height * 0.90f)
            val mirrorY = (centerY + offset * 0.82f).coerceIn(height * 0.12f, height * 0.88f)

            if (i == 0) {
                mainPath.moveTo(x, y)
                mirrorPath.moveTo(x, mirrorY)
                fillPath.moveTo(x, y)
            } else {
                val px = (i - 1) * stepX
                val prevOffset = points[i - 1].coerceIn(-1f, 1f)
                val py = (centerY - prevOffset * amplitude).coerceIn(height * 0.10f, height * 0.90f)
                val pm = (centerY + prevOffset * amplitude * 0.82f).coerceIn(height * 0.12f, height * 0.88f)
                val midX = (px + x) / 2f
                mainPath.quadraticTo(px, py, midX, (py + y) / 2f)
                mirrorPath.quadraticTo(px, pm, midX, (pm + mirrorY) / 2f)
                fillPath.quadraticTo(px, py, midX, (py + y) / 2f)
            }
        }

        fillPath.lineTo(width, centerY)
        fillPath.lineTo(0f, centerY)
        fillPath.close()

        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    primary.copy(alpha = 0.10f + state.energy * 0.10f),
                    Color.Transparent
                )
            )
        )
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                listOf(
                    tertiary.copy(alpha = 0.18f + state.beat * 0.18f),
                    secondary.copy(alpha = 0.03f),
                    Color.Transparent
                )
            )
        )

        drawPath(
            path = mainPath,
            color = secondary.copy(alpha = 0.30f + state.beat * 0.18f),
            style = Stroke(width = 13.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = mirrorPath,
            color = tertiary.copy(alpha = 0.18f + state.energy * 0.10f),
            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = mainPath,
            color = primary.copy(alpha = 0.98f),
            style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = mirrorPath,
            color = tertiary.copy(alpha = 0.72f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val pulseRadius = 8.dp.toPx() + state.beat * 28.dp.toPx()
        drawCircle(
            color = primary.copy(alpha = 0.14f + state.beat * 0.22f),
            radius = pulseRadius * 1.8f,
            center = Offset(width * 0.5f, centerY)
        )
        drawCircle(
            color = tertiary.copy(alpha = 0.70f + state.beat * 0.22f),
            radius = pulseRadius.coerceAtLeast(3.dp.toPx()),
            center = Offset(width * 0.5f, centerY)
        )

        val scanX = ((phase / (Math.PI * 2.0).toFloat()) * width) % width
        drawLine(
            color = secondary.copy(alpha = 0.24f + state.energy * 0.12f),
            start = Offset(scanX, height * 0.14f),
            end = Offset(scanX, height * 0.86f),
            strokeWidth = 1.5.dp.toPx()
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        tonalElevation = 6.dp,
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VisualizerMode.values().forEach { visualMode ->
                val isSelected = visualMode == currentMode
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = tween(180),
                    label = "mode_bg"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(180),
                    label = "mode_content"
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(bgColor)
                        .clickable { onModeSelected(visualMode) }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = visualMode.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
