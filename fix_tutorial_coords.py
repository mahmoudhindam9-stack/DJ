import re
content = open("app/src/main/java/com/example/tutorial/TutorialManager.kt").read()

if "positionOnScreen" not in content:
    content = content.replace("import androidx.compose.ui.layout.boundsInRoot", "import androidx.compose.ui.layout.boundsInRoot\nimport androidx.compose.ui.layout.positionOnScreen\nimport androidx.compose.ui.geometry.Size\nimport androidx.compose.ui.geometry.Offset")
    
    old_target = """fun Modifier.tutorialTarget(step: TutorialStep): Modifier = composed {
    this.onGloballyPositioned { coordinates ->
        TutorialManager.registerTarget(step, coordinates.boundsInRoot())
    }
}"""
    
    new_target = """fun Modifier.tutorialTarget(step: TutorialStep): Modifier = composed {
    this.onGloballyPositioned { coordinates ->
        val positionOnScreen = coordinates.positionOnScreen()
        val size = coordinates.size
        TutorialManager.registerTarget(step, Rect(
            offset = positionOnScreen,
            size = androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat())
        ))
    }
}"""
    content = content.replace(old_target, new_target)
    open("app/src/main/java/com/example/tutorial/TutorialManager.kt", "w").write(content)
