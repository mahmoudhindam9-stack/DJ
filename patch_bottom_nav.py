import re
with open("app/src/main/java/com/example/ui/components/BottomNavBar.kt", "r") as f:
    content = f.read()

radio_nav = """
        DjNavItem(
            icon = Icons.Filled.Radio,
            label = "Radio",
            selected = currentRoute == "radio",
            onClick = { onNavigate("radio") }
        )
"""

content = content.replace('DjNavItem(\n            icon = Icons.Filled.Cloud', radio_nav + '        DjNavItem(\n            icon = Icons.Filled.Cloud')

if 'import androidx.compose.material.icons.filled.Radio' not in content:
    content = content.replace('import androidx.compose.material.icons.filled.*', 'import androidx.compose.material.icons.filled.*\nimport androidx.compose.material.icons.filled.Radio')

with open("app/src/main/java/com/example/ui/components/BottomNavBar.kt", "w") as f:
    f.write(content)
