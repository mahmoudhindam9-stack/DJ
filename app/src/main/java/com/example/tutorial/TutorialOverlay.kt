package com.example.tutorial

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
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

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
                    drawRect(color = Color.Black.copy(alpha = 0.8f))
                }
            } else {
                drawRect(color = Color.Black.copy(alpha = 0.8f))
            }
        }

        // Draw callout
        if (targetRect != null && targetRect.width > 0 && targetRect.height > 0) {
            val isTopHalf = targetRect.center.y < (context.resources.displayMetrics.heightPixels / 2)
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .align(if (isTopHalf) Alignment.Center else Alignment.TopCenter)
                    .offset(y = if (isTopHalf) (targetRect.bottom.dp / 3) else (targetRect.top.dp / 3 - 200.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isTopHalf) {
                    Text("↓", fontSize = 48.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                
                if (isTopHalf) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("↑", fontSize = 48.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // Fallback if target rect is not found yet
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
