import re

content = open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt").read()

def replace_between(content, start_str, end_str, replacement):
    start_idx = content.find(start_str)
    if start_idx == -1: return content
    end_idx = content.find(end_str, start_idx + len(start_str))
    if end_idx == -1: return content
    return content[:start_idx] + replacement + content[end_idx + len(end_str):]

start_str = "        Canvas(modifier = Modifier.fillMaxSize()) {"
end_str = "        // Draw callout"

new_canvas = """        Canvas(modifier = Modifier.fillMaxSize()) {
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
        }
        
        // Draw callout"""

content = replace_between(content, start_str, end_str, new_canvas)

# Let's also check the black color and the callout position logic 
open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt", "w").write(content)
