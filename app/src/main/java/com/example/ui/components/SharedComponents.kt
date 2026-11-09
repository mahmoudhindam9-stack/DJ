package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PremiumBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val bgTop = MaterialTheme.colorScheme.background
    val bgMid = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    val bgBottom = MaterialTheme.colorScheme.background
    val primaryGlow = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val secondaryGlow = MaterialTheme.colorScheme.secondary.copy(alpha = 0.09f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(bgTop, bgMid, bgBottom))
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(primaryGlow, Color.Transparent),
                    center = Offset(900f, 180f),
                    radius = 780f
                )
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(secondaryGlow, Color.Transparent),
                    center = Offset(120f, 1500f),
                    radius = 820f
                )
            ),
        content = content
    )
}

@Composable
fun DjSurfaceCard(
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
    color: Color = if (tonal) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
    },
    borderColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = if (tonal) 0.36f else 0.20f),
    shape: Shape = RoundedCornerShape(20.dp),
    elevation: Dp = if (tonal) 6.dp else 4.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            borderColor,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
            borderColor.copy(alpha = (borderColor.alpha * 0.75f).coerceIn(0f, 1f))
        )
    )
    val finalModifier = if (onClick != null) {
        modifier.clip(shape).clickable(onClick = onClick)
    } else {
        modifier
    }
    Card(
        modifier = finalModifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = color),
        border = BorderStroke(1.dp, borderBrush),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        content = content
    )
}

@Composable
fun PremiumStatusPill(
    text: String,
    active: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val activeColor = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = activeColor.copy(alpha = if (active) 0.16f else 0.10f),
        border = BorderStroke(1.dp, activeColor.copy(alpha = if (active) 0.45f else 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(activeColor)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                color = activeColor
            )
        }
    }
}

@Composable
fun DjSectionHeader(
    title: String,
    subtitle: String? = null,
    badge: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (!badge.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f))
            ) {
                Text(
                    text = badge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
