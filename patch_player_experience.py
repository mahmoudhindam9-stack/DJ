import re

content = open("app/src/main/java/com/example/MainPlayerExperience.kt").read()

import_tutorial = "import com.example.tutorial.*\nimport androidx.compose.ui.layout.onGloballyPositioned\nimport androidx.compose.ui.layout.boundsInRoot\n"
if "import com.example.tutorial.*" not in content:
    content = content.replace("import com.example.model.AudioItem", import_tutorial + "import com.example.model.AudioItem")

# Add tutorial state tracking to force menu open
state_injection = """    var infoMessage by remember { mutableStateOf<String?>(null) }
    
    val tutorialStep by TutorialManager.currentStep.collectAsState()
    LaunchedEffect(tutorialStep) {
        if (tutorialStep == TutorialStep.MENU_SCAN || 
            tutorialStep == TutorialStep.MENU_ADD || 
            tutorialStep == TutorialStep.MENU_IMPORT || 
            tutorialStep == TutorialStep.MENU_UPDATE) {
            showLibraryMenu = true
        } else if (tutorialStep == TutorialStep.NONE) {
            showLibraryMenu = false
        }
    }"""
content = content.replace("    var infoMessage by remember { mutableStateOf<String?>(null) }", state_injection)

# Add targets
content = content.replace(
    """IconButton(onClick = { showThemeMenu = true })""",
    """IconButton(onClick = { showThemeMenu = true }, modifier = Modifier.tutorialTarget(TutorialStep.UPDATE_NEW_THEMES))"""
)
content = content.replace(
    """IconButton(onClick = { showLibraryMenu = true })""",
    """IconButton(onClick = { showLibraryMenu = true }, modifier = Modifier.tutorialTarget(TutorialStep.MAIN_MENU_BUTTON))"""
)
content = content.replace(
    """DropdownMenuItem(text = { Text("Scan device music") }""",
    """DropdownMenuItem(modifier = Modifier.tutorialTarget(TutorialStep.MENU_SCAN), text = { Text("Scan device music") }"""
)
content = content.replace(
    """DropdownMenuItem(text = { Text("Add audio files") }""",
    """DropdownMenuItem(modifier = Modifier.tutorialTarget(TutorialStep.MENU_ADD), text = { Text("Add audio files") }"""
)
content = content.replace(
    """DropdownMenuItem(text = { Text("Import Music") }""",
    """DropdownMenuItem(modifier = Modifier.tutorialTarget(TutorialStep.MENU_IMPORT), text = { Text("Import Music") }"""
)
content = content.replace(
    """DropdownMenuItem(
                        text = { Text("Check for updates") }""",
    """DropdownMenuItem(
                        modifier = Modifier.tutorialTarget(TutorialStep.MENU_UPDATE),
                        text = { Text("Check for updates") }"""
)

open("app/src/main/java/com/example/MainPlayerExperience.kt", "w").write(content)
