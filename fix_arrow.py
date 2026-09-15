import re
content = open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt").read()

old_arrow = """                    val strokeW = 4.dp.toPx()
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
                    )"""

new_arrow = """                    val strokeW = 6.dp.toPx()
                    drawLine(
                        color = arrowColor,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    
                    val angle = atan2(endY - startY, endX - startX)
                    val arrowLength = 32.dp.toPx()
                    val angleOffset = PI / 5
                    val arrowPath = Path().apply {
                        moveTo(endX, endY)
                        lineTo(
                            endX - arrowLength * cos(angle - angleOffset).toFloat(),
                            endY - arrowLength * sin(angle - angleOffset).toFloat()
                        )
                        lineTo(
                            endX - arrowLength * cos(angle + angleOffset).toFloat(),
                            endY - arrowLength * sin(angle + angleOffset).toFloat()
                        )
                        close()
                    }
                    drawPath(
                        path = arrowPath,
                        color = arrowColor,
                        style = androidx.compose.ui.graphics.drawscope.Fill
                    )"""

content = content.replace(old_arrow, new_arrow)

open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt", "w").write(content)
