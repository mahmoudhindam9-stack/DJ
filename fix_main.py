with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# Replace dangling @Composable with just a newline
import re
content = re.sub(r'// KARAOKE_MIC_PAGE_V5\n@Composable\n// MIC_RECORDING_FORMAT_V1', '// KARAOKE_MIC_PAGE_V5\n// MIC_RECORDING_FORMAT_V1', content)

# Check for other dangling @Composables
content = re.sub(r'@Composable\s*\n\s*\n', '\n\n', content)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
