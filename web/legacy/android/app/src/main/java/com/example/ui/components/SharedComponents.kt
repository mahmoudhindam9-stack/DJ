package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium global background: subtle animated light fields create depth without
 * adding a heavyweight 3D engine or changing any app behaviour.
 */
@Composable
fun PremiumBackdrop(content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "premium-backdrop")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "premium-phase"
    )

    val backgroundColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(backgroundColor)
                val w = size.width
                val h = size.height
                val x = w * (0.18f + 0.12f * sin(phase * Math.PI * 2.0).toFloat())
                val y = h * (0.18f + 0.08f * cos(phase * Math.PI * 2.0).toFloat())
                drawCircle(
                    color = primaryColor.copy(alpha = 0.12f),
                    radius = maxOf(w, h) * 0.42f,
                    center = Offset(x, y)
                )
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.08f),
                    radius = maxOf(w, h) * 0.34f,
                    center = Offset(w * 0.84f - x * 0.14f, h * 0.76f)
                )
                drawCircle(
                    color = tertiaryColor.copy(alpha = 0.045f),
                    radius = maxOf(w, h) * 0.25f,
                    center = Offset(w * 0.54f, h * 0.42f)
                )
            },
        content = content
    )
}

@Composable
fun DjSurfaceCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(140),
        label = "card-scale"
    )
    val rotationY = if (pressed) 1.2f else 0f
    val rotationX = if (pressed) -1.2f else 0f

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else Modifier

    Surface(
        modifier = modifier
            .then(clickModifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.rotationX = rotationX
                this.rotationY = rotationY
                cameraDistance = 28f * density
                shadowElevation = if (pressed) 18f else 9f
            },
        shape = RoundedCornerShape(22.dp),
        color = color.copy(alpha = if (pressed) 0.98f else 0.97f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, borderColor.copy(alpha = if (pressed) 0.46f else 0.32f)),
        tonalElevation = 3.dp,
        shadowElevation = if (pressed) 18.dp else 9.dp
    ) {
        Column(content = content)
    }
}

@Composable
fun DjSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    val headerPrimary = MaterialTheme.colorScheme.primary
    val headerSecondary = MaterialTheme.colorScheme.secondary

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.2.sp,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(54.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .drawBehind {
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(
                                headerPrimary,
                                headerSecondary,
                                Color.Transparent
                            )
                        )
                    )
                }
        )
    }
}

@Composable
fun DjIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    size: Int = 48
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        animationSpec = tween(120),
        label = "icon-scale"
    )

    Surface(
        modifier = modifier
            .size(size.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationX = if (pressed) -4f else 0f
                rotationY = if (pressed) 4f else 0f
                cameraDistance = 24f * density
                shadowElevation = if (pressed) 16f else 6f
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(17.dp),
        color = containerColor.copy(alpha = 0.92f),
        contentColor = contentColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)),
        shadowElevation = if (pressed) 14.dp else 6.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    }
}

@Composable
fun PremiumStatusPill(
    text: String,
    active: Boolean = true,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "status-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.70f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "status-alpha"
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = if (active) pulse else 0.25f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
