content = open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt").read()

compose_path_logic = """            val fullScreenPath = Path().apply {
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
            }"""

import re
content = re.sub(r'            val fullScreenPath = Path\(\)\.apply \{.*?drawPath\(fullScreenPath, color = Color\.Black\.copy\(alpha = 0\.8f\)\)', compose_path_logic, content, flags=re.DOTALL)

open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt", "w").write(content)
