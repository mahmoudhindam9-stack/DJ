import re
content = open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt").read()

content = content.replace("import androidx.compose.ui.graphics.PathOperation", "import androidx.compose.ui.graphics.PathOperation\nimport androidx.compose.ui.graphics.ClipOp\nimport androidx.compose.ui.graphics.drawscope.clipPath")

canvas_block_old = """        Canvas(modifier = Modifier.fillMaxSize()) {
            val fullScreenPath = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
            }
            if (targetRect != null && targetRect.width > 0 && targetRect.height > 0) {
                val expandedRect = targetRect.inflate(8.dp.toPx())
                val cutoutPath = Path().apply {
                    addRoundRect(RoundRect(expandedRect, CornerRadius(12.dp.toPx())))
                }
                val combinedPath = Path()
                combinedPath.op(fullScreenPath, cutoutPath, PathOperation.Difference)
                drawPath(combinedPath, color = Color.Black.copy(alpha = 0.8f))
            } else {
                drawPath(fullScreenPath, color = Color.Black.copy(alpha = 0.8f))
            }
        }"""

canvas_block_new = """        Canvas(modifier = Modifier.fillMaxSize()) {
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
        }"""

content = content.replace(canvas_block_old, canvas_block_new)

open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt", "w").write(content)
