import re

content = open("app/src/main/java/com/example/ui/theme/Theme.kt").read()

# Fix the signature of MyApplicationTheme and move the light schemes outside
# First extract the schemes
schemes_match = re.search(r'private val LightDjBlueColorScheme.*?@Composable', content, re.DOTALL)
schemes = schemes_match.group(0)

# Replace the messy signature
content = content.replace(
    """fun MyApplicationTheme(
    appTheme: AppThemeOption = AppThemeOption.SYSTEM,
    // We disable dynamic color to maintain the strict DJ aesthetic (neon/dark)
    dynamicColor: Boolean = false,
    content: """ + schemes + """ () -> Unit
) {""",
    schemes.replace("@Composable", "") + """
@Composable
fun MyApplicationTheme(
    appTheme: AppThemeOption = AppThemeOption.SYSTEM,
    // We disable dynamic color to maintain the strict DJ aesthetic (neon/dark)
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {"""
)

open("app/src/main/java/com/example/ui/theme/Theme.kt", "w").write(content)
