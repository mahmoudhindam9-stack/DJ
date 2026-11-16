import re
content = open("app/src/main/java/com/example/DJMixerScreen.kt").read()

import_tutorial = "import com.example.tutorial.*\n"
if "import com.example.tutorial.*" not in content:
    content = content.replace("import com.example.model.AudioItem", import_tutorial + "import com.example.model.AudioItem")

content = content.replace(
    """DJFxRack(deckA)""",
    """Box(modifier = Modifier.tutorialTarget(TutorialStep.DJ_FX)) { DJFxRack(deckA) }"""
)

content = content.replace(
    """com.example.djfx.DjFxBoard(controller = djFxController)""",
    """Box(modifier = Modifier.tutorialTarget(TutorialStep.DJ_SAMPLER)) { com.example.djfx.DjFxBoard(controller = djFxController) }"""
)

content = content.replace(
    """.clickable { showTrackSelector = true }""",
    """.clickable { showTrackSelector = true }.run { if (deck.deckName == "DECK A") tutorialTarget(TutorialStep.DJ_LOAD) else this }"""
)

open("app/src/main/java/com/example/DJMixerScreen.kt", "w").write(content)
