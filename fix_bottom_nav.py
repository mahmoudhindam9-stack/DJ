import re
with open('app/src/main/java/com/example/ui/components/BottomNavBar.kt', 'r') as f:
    content = f.read()

# Match the studio nav item block
new_content = re.sub(r'\s*DjNavItem\(\s*icon = Icons\.Filled\.MusicNote,\s*label = "Studio",\s*selected = currentRoute == "studio",\s*onClick = \{ onNavigate\("studio"\) \}\s*\)', '', content)

with open('app/src/main/java/com/example/ui/components/BottomNavBar.kt', 'w') as f:
    f.write(new_content)
