from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
ORG_PACKAGE = "com.example.org"

text = MAIN.read_text(encoding="utf-8")

if f"import {ORG_PACKAGE}.OrgScreen" not in text:
    player_import = re.search(r"^import com\.example\.player\.\*\s*$", text, re.MULTILINE)
    if player_import:
        insert_at = player_import.end()
        text = text[:insert_at] + f"\nimport {ORG_PACKAGE}.OrgScreen" + text[insert_at:]
    else:
        package_line = re.search(r"^package .*?$", text, re.MULTILINE)
        if not package_line:
            raise SystemExit("package declaration not found")
        insert_at = package_line.end()
        text = text[:insert_at] + f"\n\nimport {ORG_PACKAGE}.OrgScreen" + text[insert_at:]

if 'currentDestination?.route == "org"' not in text:
    org_nav = '''                NavigationBarItem(
                    icon = { Icon(Icons.Filled.MusicNote, contentDescription = "ORG") },
                    label = { Text("ORG") },
                    selected = currentDestination?.route == "org",
                    onClick = {
                        navController.navigate("org") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
'''
    idx = text.find('                NavigationBarItem(\n                    icon = { Icon(Icons.Filled.NotificationsActive, contentDescription = "Controls") },')
    if idx < 0:
        raise SystemExit("Controls navigation item not found")
    text = text[:idx] + org_nav + text[idx:]

if 'composable("org")' not in text:
    org_route = '''            composable("org") {
                OrgScreen()
            }
'''
    controls_route = '            composable("controls") {\n                NotificationControlScreen(context = context)\n            }'
    idx = text.find(controls_route)
    if idx < 0:
        raise SystemExit("Controls route not found")
    text = text[:idx] + org_route + text[idx:]

MAIN.write_text(text, encoding="utf-8")
print("ORG workstation navigation wired")
