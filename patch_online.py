import re
content = open("app/src/main/java/com/example/onlinemusic/OnlineMusicScreen.kt").read()

import_tutorial = "import com.example.tutorial.*\n"
if "import com.example.tutorial.*" not in content:
    content = content.replace("import com.example.model.AudioItem", import_tutorial + "import com.example.model.AudioItem")

# Add targets to AudiusSongCard (assuming it is visible on screen or we can add it to the search bar)
# Since search is always visible, let's add it there
content = content.replace(
    """OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp)""",
    """OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp).tutorialTarget(TutorialStep.ONLINE_SEARCH)"""
)
content = content.replace(
    """IconButton(onClick = { onPlay(link) }) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play") }""",
    """IconButton(modifier = Modifier.tutorialTarget(TutorialStep.ONLINE_SELECT), onClick = { onPlay(link) }) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play") }"""
)
content = content.replace(
    """IconButton(onClick = { onQueue(link) }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist") }""",
    """IconButton(modifier = Modifier.tutorialTarget(TutorialStep.ONLINE_QUEUE), onClick = { onQueue(link) }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist") }"""
)
content = content.replace(
    """IconButton(onClick = { onDownload(link) }) { Icon(Icons.Filled.Download, "Download") }""",
    """IconButton(modifier = Modifier.tutorialTarget(TutorialStep.ONLINE_DOWNLOAD), onClick = { onDownload(link) }) { Icon(Icons.Filled.Download, "Download") }"""
)

# And similarly for Audius track list
content = content.replace(
    """IconButton(onClick = { onPlay(track) }) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play") }""",
    """IconButton(modifier = Modifier.tutorialTarget(TutorialStep.ONLINE_SELECT), onClick = { onPlay(track) }) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play") }"""
)
content = content.replace(
    """IconButton(onClick = { onQueue(track) }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist") }""",
    """IconButton(modifier = Modifier.tutorialTarget(TutorialStep.ONLINE_QUEUE), onClick = { onQueue(track) }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist") }"""
)
content = content.replace(
    """IconButton(onClick = { onDownload(track) }) { Icon(Icons.Filled.Download, "Download") }""",
    """IconButton(modifier = Modifier.tutorialTarget(TutorialStep.ONLINE_DOWNLOAD), onClick = { onDownload(track) }) { Icon(Icons.Filled.Download, "Download") }"""
)

open("app/src/main/java/com/example/onlinemusic/OnlineMusicScreen.kt", "w").write(content)
