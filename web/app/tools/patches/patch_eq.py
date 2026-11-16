import re
content = open("app/src/main/java/com/example/EqualizerScreen.kt").read()

import_tutorial = "import com.example.tutorial.*\n"
if "import com.example.tutorial.*" not in content:
    content = content.replace("import com.example.player.EqualizerController", import_tutorial + "import com.example.player.EqualizerController")

content = content.replace(
    """Text(
            "PRESETS",""",
    """Text(
            "PRESETS",
            modifier = Modifier.tutorialTarget(TutorialStep.EQ_PRESETS),"""
)

content = content.replace(
    """Text(
                    "FREQUENCIES",""",
    """Text(
                    "FREQUENCIES",
                    modifier = Modifier.tutorialTarget(TutorialStep.EQ_FREQUENCIES),"""
)

content = content.replace(
    """Text(
                        "BASS BOOST",""",
    """Text(
                        "BASS BOOST",
                        modifier = Modifier.tutorialTarget(TutorialStep.EQ_BASS_TREBLE),"""
)

content = content.replace(
    """Text(
                        "PREAMP GAIN",""",
    """Text(
                        "PREAMP GAIN",
                        modifier = Modifier.tutorialTarget(TutorialStep.EQ_PREAMP),"""
)

open("app/src/main/java/com/example/EqualizerScreen.kt", "w").write(content)
