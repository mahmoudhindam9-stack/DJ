import re
content = open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt").read()

# Fix actualTargetRect scope
content = content.replace("val rawTargetRect = TutorialManager.getTargetRect(step)\n    val targetRect = rawTargetRect",
"""val targetRect = TutorialManager.getTargetRect(step)
    var overlayScreenOffset by remember { mutableStateOf(Offset.Zero) }
    val actualTargetRect = targetRect?.translate(-overlayScreenOffset)""")

# Remove the old definitions
content = content.replace("var overlayScreenOffset by remember { mutableStateOf(Offset.Zero) }\n", "", 1) # remove the duplicate
content = content.replace("val actualTargetRect = targetRect.translate(-overlayScreenOffset)\n                ", "")

content = content.replace("if (targetRect != null && targetRect.width > 0 && targetRect.height > 0) {", "if (actualTargetRect != null && actualTargetRect.width > 0 && actualTargetRect.height > 0) {")
content = content.replace("targetRect?.translate", "targetRect.translate")

open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt", "w").write(content)
