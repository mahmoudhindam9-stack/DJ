import re
content = open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt").read()

content = content.replace(
    'Text("ESCAPE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)',
    'Text("ESCAPE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)'
)

open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt", "w").write(content)
