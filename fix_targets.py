import re
content = open("app/src/main/java/com/example/tutorial/TutorialManager.kt").read()

if "import androidx.compose.runtime.mutableStateMapOf" not in content:
    content = content.replace("import androidx.compose.ui.geometry.Rect", "import androidx.compose.ui.geometry.Rect\nimport androidx.compose.runtime.mutableStateMapOf")

content = content.replace("val targets = mutableMapOf<TutorialStep, Rect>()", "val targets = mutableStateMapOf<TutorialStep, Rect>()")

open("app/src/main/java/com/example/tutorial/TutorialManager.kt", "w").write(content)
