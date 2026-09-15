import re
content = open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt").read()

content = content.replace(
"""    val targetRect = TutorialManager.getTargetRect(step)
    val actualTargetRect = targetRect?.translate(-overlayScreenOffset)
    val isLast = step == TutorialStep.ONLINE_DOWNLOAD || step == TutorialStep.UPDATE_NEW_THEMES
    
    var cardBounds by remember { mutableStateOf(Rect.Zero) }
    var overlayScreenOffset by remember { mutableStateOf(Offset.Zero) }""",
"""    var overlayScreenOffset by remember { mutableStateOf(Offset.Zero) }
    val targetRect = TutorialManager.getTargetRect(step)
    val actualTargetRect = targetRect?.translate(-overlayScreenOffset)
    val isLast = step == TutorialStep.ONLINE_DOWNLOAD || step == TutorialStep.UPDATE_NEW_THEMES
    
    var cardBounds by remember { mutableStateOf(Rect.Zero) }"""
)

open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt", "w").write(content)
