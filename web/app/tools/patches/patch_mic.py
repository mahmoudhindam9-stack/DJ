import re
content = open("app/src/main/java/com/example/MicScreen.kt").read()

import_tutorial = "import com.example.tutorial.*\n"
if "import com.example.tutorial.*" not in content:
    content = content.replace("import com.example.model.*", import_tutorial + "import com.example.model.*")

content = content.replace(
    """Text("Karaoke Studio",""",
    """Text("Karaoke Studio", modifier = Modifier.tutorialTarget(TutorialStep.MIC_CONTROLS),"""
)

open("app/src/main/java/com/example/MicScreen.kt", "w").write(content)
