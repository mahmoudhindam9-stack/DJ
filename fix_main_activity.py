import re
with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

content = re.sub(r'import com.example.studio.MusicStudioController\n', '', content)
content = re.sub(r'import com.example.studio.MusicStudioScreen\n', '', content)
content = re.sub(r'// STUDIO_CONTROLS_V1\n', '', content)

# Remove the radio bottom nav item if it's there
content = re.sub(r'\s*NavigationBarItem\(\s*icon = \{ Icon\(Icons\.Filled\.MusicNote, contentDescription = "Radio"\) \},\s*label = \{ Text\("Radio"\) \},\s*selected = currentDestination\?.route == "studio",\s*onClick = \{\s*navController\.navigate\("studio"\) \{\s*popUpTo\(navController\.graph\.findStartDestination\(\)\.id\) \{ saveState = true \}\s*launchSingleTop = true\s*restoreState = true\s*\}\s*\}\s*\)', '', content)

# Remove composable("studio")
content = re.sub(r'\s*composable\("studio"\) \{\s*MusicStudioScreen\(musicStudioController\)\s*\}', '', content)

# Remove musicStudioController definition
content = re.sub(r'\s*val musicStudioController = remember \{\s*MusicStudioController\(this@MainActivity\)\s*\}\s*DisposableEffect\(musicStudioController\) \{\s*onDispose \{\s*musicStudioController\.release\(\)\s*\}\s*\}', '', content)
# Sometimes it's just val musicStudioController = remember { MusicStudioController(...) } without DisposableEffect
content = re.sub(r'\s*val musicStudioController = remember \{ MusicStudioController\(.*?\) \}', '', content)


with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
