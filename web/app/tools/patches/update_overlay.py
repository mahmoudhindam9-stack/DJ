import os

content = """package com.example.tutorial

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

@Composable
fun TutorialOverlay(onNavigate: (String) -> Unit) {
    val step by TutorialManager.currentStep.collectAsState()
    if (step == TutorialStep.NONE) return

    val context = LocalContext.current
    var version by remember { mutableStateOf("1.0") }
    LaunchedEffect(Unit) {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            version = pInfo.versionName ?: "1.0"
        } catch (e: Exception) {}
    }

    LaunchedEffect(step) {
        if (step != TutorialStep.NONE) {
            onNavigate(step.tabRoute)
        }
    }

    val targetRect = TutorialManager.getTargetRect(step)
    val isLast = step == TutorialStep.ONLINE_DOWNLOAD || step == TutorialStep.UPDATE_NEW_THEMES
    
    var cardBounds by remember { mutableStateOf(Rect.Zero) }
    val arrowColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1000f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (targetRect != null && targetRect.width > 0 && targetRect.height > 0) {
                val expandedRect = targetRect.inflate(8.dp.toPx())
                val cutoutPath = Path().apply {
                    addRoundRect(RoundRect(expandedRect, CornerRadius(12.dp.toPx())))
                }
                clipPath(cutoutPath, clipOp = ClipOp.Difference) {
                    drawRect(color = Color.Black.copy(alpha = 0.35f))
                }

                if (cardBounds != Rect.Zero) {
                    val isTopHalf = targetRect.center.y < size.height / 2f
                    
                    val startX = cardBounds.center.x
                    val startY = if (isTopHalf) cardBounds.top - 8.dp.toPx() else cardBounds.bottom + 8.dp.toPx()
                    val endX = targetRect.center.x
                    val endY = if (isTopHalf) expandedRect.bottom + 8.dp.toPx() else expandedRect.top - 8.dp.toPx()
                    
                    val strokeW = 4.dp.toPx()
                    drawLine(
                        color = arrowColor,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    
                    val angle = atan2(endY - startY, endX - startX)
                    val arrowLength = 20.dp.toPx()
                    val angleOffset = PI / 6
                    val arrowPath = Path().apply {
                        moveTo(endX, endY)
                        lineTo(
                            endX - arrowLength * cos(angle - angleOffset).toFloat(),
                            endY - arrowLength * sin(angle - angleOffset).toFloat()
                        )
                        moveTo(endX, endY)
                        lineTo(
                            endX - arrowLength * cos(angle + angleOffset).toFloat(),
                            endY - arrowLength * sin(angle + angleOffset).toFloat()
                        )
                    }
                    drawPath(
                        path = arrowPath,
                        color = arrowColor,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            } else {
                drawRect(color = Color.Black.copy(alpha = 0.35f))
            }
        }

        // Draw callout
        if (targetRect != null && targetRect.width > 0 && targetRect.height > 0) {
            val isTopHalf = targetRect.center.y < (context.resources.displayMetrics.heightPixels / 2)
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val expandedTargetBottom = targetRect.bottom + 8.dp.toPx()
                        val expandedTargetTop = targetRect.top - 8.dp.toPx()
                        val gap = 60.dp.toPx()
                        
                        val yPos = if (isTopHalf) {
                            (expandedTargetBottom + gap).toInt()
                        } else {
                            (expandedTargetTop - gap - placeable.height).toInt()
                        }
                        
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.placeRelative(
                                x = (constraints.maxWidth - placeable.width) / 2,
                                y = yPos.coerceIn(0, constraints.maxHeight - placeable.height)
                            )
                        }
                    }
                    .onGloballyPositioned { coords ->
                        cardBounds = coords.boundsInRoot()
                    },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (TutorialManager.isUpdateTour()) {
                        Text(
                            text = "WHAT'S NEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = step.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        // Bottom Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .padding(bottom = 56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { 
                TutorialManager.end(context, version)
                onNavigate("player")
            }) {
                Text("ESCAPE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Button(
                onClick = {
                    if (isLast) {
                        TutorialManager.end(context, version)
                        onNavigate("player")
                    } else {
                        TutorialManager.next(context, version)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    if (isLast) "GET STARTED" else "NEXT",
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
"""

with open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt", "w") as f:
    f.write(content)
