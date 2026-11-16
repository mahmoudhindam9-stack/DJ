import re
content = open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt").read()

if "import androidx.compose.ui.layout.positionOnScreen" not in content:
    content = content.replace("import androidx.compose.ui.layout.onGloballyPositioned", "import androidx.compose.ui.layout.onGloballyPositioned\nimport androidx.compose.ui.layout.positionOnScreen")

# Add overlayScreenOffset state
if "var overlayScreenOffset by remember { mutableStateOf(Offset.Zero) }" not in content:
    content = content.replace("var cardBounds by remember { mutableStateOf(Rect.Zero) }", 
                              "var cardBounds by remember { mutableStateOf(Rect.Zero) }\n    var overlayScreenOffset by remember { mutableStateOf(Offset.Zero) }")

# Add onGloballyPositioned to the Box
if "overlayScreenOffset = coords.positionOnScreen()" not in content:
    content = content.replace(
"""        modifier = Modifier
            .fillMaxSize()
            .zIndex(1000f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )""",
"""        modifier = Modifier
            .fillMaxSize()
            .zIndex(1000f)
            .onGloballyPositioned { coords ->
                overlayScreenOffset = coords.positionOnScreen()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )"""
    )

# Apply offset to targetRect in drawing logic
old_canvas = """        Canvas(modifier = Modifier.fillMaxSize()) {
            if (targetRect != null && targetRect.width > 0 && targetRect.height > 0) {
                val expandedRect = targetRect.inflate(8.dp.toPx())"""

new_canvas = """        Canvas(modifier = Modifier.fillMaxSize()) {
            if (targetRect != null && targetRect.width > 0 && targetRect.height > 0) {
                val actualTargetRect = targetRect.translate(-overlayScreenOffset)
                val expandedRect = actualTargetRect.inflate(8.dp.toPx())"""

content = content.replace(old_canvas, new_canvas)
content = content.replace("targetRect.center.y", "actualTargetRect.center.y")
content = content.replace("targetRect.center.x", "actualTargetRect.center.x")
content = content.replace("targetRect.bottom", "actualTargetRect.bottom")
content = content.replace("targetRect.top", "actualTargetRect.top")
content = content.replace("val targetRect = TutorialManager.getTargetRect(step)", "val rawTargetRect = TutorialManager.getTargetRect(step)\n    val targetRect = rawTargetRect")

open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt", "w").write(content)
