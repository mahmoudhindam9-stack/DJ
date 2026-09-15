import re
content = open("app/src/main/java/com/example/radio/RadioScreen.kt").read()

import_tutorial = "import com.example.tutorial.*\n"
if "import com.example.tutorial.*" not in content:
    content = content.replace("import com.example.model.AudioItem", import_tutorial + "import com.example.model.AudioItem")

content = content.replace(
    """LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {""",
    """LazyColumn(Modifier.fillMaxSize().tutorialTarget(TutorialStep.RADIO_STATIONS), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {"""
)
content = content.replace(
    """IconButton(onClick = { favorites = if (isFavorite) favorites - station.id else favorites + station.id }) { Icon(if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, "المفضلة") }""",
    """IconButton(modifier = Modifier.run { if (index == 0) tutorialTarget(TutorialStep.RADIO_FAVORITES) else this }, onClick = { favorites = if (isFavorite) favorites - station.id else favorites + station.id }) { Icon(if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, "المفضلة") }"""
)
content = content.replace(
    """FilledIconButton(""",
    """FilledIconButton(modifier = Modifier.run { if (index == 0) tutorialTarget(TutorialStep.RADIO_PLAY) else this },"""
)
# Add index inside items if missing
content = re.sub(r'items\(stations\) \{ station ->', r'itemsIndexed(stations) { index, station ->', content)

open("app/src/main/java/com/example/radio/RadioScreen.kt", "w").write(content)
