import re
content = open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt").read()

content = content.replace(
    "colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))",
    "colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))"
)

# And make sure the text styles are right
open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt", "w").write(content)
