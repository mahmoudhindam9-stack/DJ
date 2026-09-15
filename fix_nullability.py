import re
content = open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt").read()

content = content.replace(
"""    val targetRect = TutorialManager.getTargetRect(step)
        val actualTargetRect = targetRect.translate(-overlayScreenOffset)""",
"""    var overlayScreenOffset by remember { mutableStateOf(Offset.Zero) }
    val targetRect = TutorialManager.getTargetRect(step)
    val actualTargetRect = targetRect?.translate(-overlayScreenOffset)""")

content = content.replace("    var overlayScreenOffset by remember { mutableStateOf(Offset.Zero) }\n", "", 1) # remove the duplicate later one

open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt", "w").write(content)
