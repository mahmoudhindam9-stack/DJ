import re
content = open("app/src/main/java/com/example/ui/theme/Theme.kt").read()

# First, remove duplicate imports
content = re.sub(r'import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.Composable', 'import androidx.compose.runtime.Composable', content)

# There are duplicate light schemes, let's remove everything from @Composable private val LightDjBlueColorScheme to the end, and then reconstruct it properly.
match = re.search(r'private val LightDjBlueColorScheme', content)
if match:
    # Just take the content before the first LightDjBlueColorScheme
    top_part = content[:match.start()]
    # actually, where are the schemes defined? Let's check lines.
