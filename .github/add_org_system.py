from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"

text = MAIN.read_text(encoding="utf-8")

if "import com.example.org.OrgScreen" not in text:
    anchor = "import com.example.player.*\n"
    if anchor not in text:
        raise SystemExit("player import anchor not found")
    text = text.replace(anchor, anchor + "import com.example.org.OrgScreen\n", 1)

nav_anchor = '''                NavigationBarItem(\n                    icon = { Icon(Icons.Filled.NotificationsActive, contentDescription = "Controls") }\n                    label = { Text("Controls") },'''
nav_block = '''                NavigationBarItem(\n                    icon = { Icon(Icons.Filled.MusicNote, contentDescription = "ORG") },\n                    label = { Text("ORG") },\n                    selected = currentDestination?.route == "org",\n                    onClick = {\n                        navController.navigate("org") {\n                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }\n                            launchSingleTop = true\n                            restoreState = true\n                        }\n                    }\n                )\n                NavigationBarItem(\n                    icon = { Icon(Icons.Filled.NotificationsActive, contentDescription = "Controls") },\n                    label = { Text("Controls") },'''
if 'currentDestination?.route == "org"' not in text:
    if nav_anchor not in text:
        raise SystemExit("controls navigation anchor not found")
    text = text.replace(nav_anchor, nav_block, 1)

route_anchor = '''            composable("controls") {\n                NotificationControlScreen(context = context)\n            }'''
route_block = '''            composable("org") {\n                OrgScreen()\n            }\n            composable("controls") {\n                NotificationControlScreen(context = context)\n            }'''
if 'composable("org")' not in text:
    if route_anchor not in text:
        raise SystemExit("controls route anchor not found")
    text = text.replace(route_anchor, route_block, 1)

MAIN.write_text(text, encoding="utf-8")
print("ORG workstation navigation wired")
