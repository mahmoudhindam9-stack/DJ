import re

files = [
    "app/src/main/java/com/example/MicScreen.kt",
    "app/src/main/java/com/example/FullPlayerScreen.kt",
    "app/src/main/java/com/example/DJMixerScreen.kt",
    "app/src/main/java/com/example/EqualizerScreen.kt"
]

for file in files:
    with open(file, 'r') as f:
        content = f.read()
    
    # remove duplicate imports or conflicting ones
    content = content.replace("import coil.compose.AsyncImage\n", "")
    content = content.replace("import com.example.player.MusicScanner\n", "import com.example.utils.MusicScanner\n")
    
    # fix "Conflicting import: imported name 'Uri' is ambiguous."
    # keep only one import android.net.Uri
    uris = content.count("import android.net.Uri\n")
    if uris > 1:
        content = content.replace("import android.net.Uri\n", "", uris - 1)
        
    with open(file, 'w') as f:
        f.write(content)
