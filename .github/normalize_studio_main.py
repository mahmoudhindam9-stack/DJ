from pathlib import Path

MAIN = Path(__file__).resolve().parents[1] / 'app/src/main/java/com/example/MainActivity.kt'
MARKER = '// STUDIO_CONTROLS_V1'

def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'{label}: source block not found')
    return text.replace(old, new, 1)

def main():
    text = MAIN.read_text(encoding='utf-8')
    if MARKER in text:
        print('Studio navigation already normalized')
        return

    text = replace_once(
        text,
        'import com.example.org.OrgScreen\n',
        'import com.example.studio.MusicStudioController\nimport com.example.studio.MusicStudioScreen\n' + MARKER + '\n',
        'studio import'
    )
    text = replace_once(
        text,
        '    val eqController = remember { EqualizerController() }\n',
        '    val eqController = remember { EqualizerController(context) }\n',
        'equalizer context'
    )
    text = replace_once(
        text,
        '    val micController = remember { MicController(context) }\n',
        '    val micController = remember { MicController(context) }\n    val musicStudioController = remember { MusicStudioController(context) }\n',
        'studio controller'
    )
    text = replace_once(
        text,
        '            eqController.release()\n',
        '            eqController.release()\n            musicStudioController.close()\n',
        'studio dispose'
    )
    old_nav = '''                NavigationBarItem(\n                    icon = { Icon(Icons.Filled.MusicNote, contentDescription = "ORG") },\n                    label = { Text("ORG") },\n                    selected = currentDestination?.route == "org",\n                    onClick = {\n                        navController.navigate("org") {\n                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }\n                            launchSingleTop = true\n                            restoreState = true\n                        }\n                    }\n                )'''
    new_nav = '''                NavigationBarItem(\n                    icon = { Icon(Icons.Filled.MusicNote, contentDescription = "Studio") },\n                    label = { Text("Studio") },\n                    selected = currentDestination?.route == "studio",\n                    onClick = {\n                        navController.navigate("studio") {\n                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }\n                            launchSingleTop = true\n                            restoreState = true\n                        }\n                    }\n                )'''
    text = replace_once(text, old_nav, new_nav, 'studio navigation')
    text = replace_once(
        text,
        '            composable("org") {\n                OrgScreen()\n            }\n',
        '            composable("studio") {\n                MusicStudioScreen(musicStudioController)\n            }\n',
        'studio route'
    )
    MAIN.write_text(text, encoding='utf-8')
    print('Studio navigation and route normalized')

if __name__ == '__main__':
    main()
