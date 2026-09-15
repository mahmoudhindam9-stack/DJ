import re
content = open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt").read()

# Replace alpha 0.35f -> 0.30f
content = content.replace("Color.Black.copy(alpha = 0.35f)", "Color.Black.copy(alpha = 0.30f)")

new_canvas_drawing = """        Canvas(modifier = Modifier.fillMaxSize()) {
            if (targetRect != null && targetRect.width > 0 && targetRect.height > 0) {
                val expandedRect = targetRect.inflate(8.dp.toPx())
                val cutoutPath = Path().apply {
                    addRoundRect(RoundRect(expandedRect, CornerRadius(12.dp.toPx())))
                }
                clipPath(cutoutPath, clipOp = ClipOp.Difference) {
                    drawRect(color = Color.Black.copy(alpha = 0.30f))
                }
                
                // Draw a clear glowing/outlined rounded rectangle around the EXACT target
                drawRoundRect(
                    color = arrowColor,
                    topLeft = expandedRect.topLeft,
                    size = expandedRect.size,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx())
                )

                if (cardBounds != Rect.Zero) {
                    val isTopHalf = targetRect.center.y < size.height / 2f
                    
                    val startX = cardBounds.center.x
                    val startY = if (isTopHalf) cardBounds.bottom else cardBounds.top
                    val endX = targetRect.center.x
                    // Use actual target boundary directly for the arrowhead to end at target
                    val endY = if (isTopHalf) expandedRect.bottom else expandedRect.top
                    
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
                drawRect(color = Color.Black.copy(alpha = 0.30f))
            }
        }"""

old_canvas_drawing_pattern = r"        Canvas\(modifier = Modifier.fillMaxSize\(\)\) \{.*?\n        \}\n        // Draw callout"

content = re.sub(old_canvas_drawing_pattern, new_canvas_drawing + "\n        // Draw callout", content, flags=re.DOTALL)

open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt", "w").write(content)
