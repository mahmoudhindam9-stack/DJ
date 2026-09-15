import re
content = open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt").read()

pattern = r"        Canvas\(modifier = Modifier\.fillMaxSize\(\)\) \{.*?\n        \}\n        // Draw callout"
match = re.search(pattern, content, flags=re.DOTALL)
if match:
    print("MATCH FOUND!")
else:
    print("NO MATCH!")
