import re
content = open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt").read()

content = content.replace(
    "colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))",
    "colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))"
)

content = content.replace(
    "color = MaterialTheme.colorScheme.onSurfaceVariant",
    "color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium"
)

open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt", "w").write(content)
