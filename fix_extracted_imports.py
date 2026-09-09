files = [
    "app/src/main/java/com/example/MicScreen.kt",
    "app/src/main/java/com/example/FullPlayerScreen.kt",
    "app/src/main/java/com/example/DJMixerScreen.kt",
    "app/src/main/java/com/example/EqualizerScreen.kt"
]

add_imports = """
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.player.MusicScanner
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.rotate
import android.Manifest
import android.net.Uri
import coil.compose.AsyncImage
"""

for file in files:
    with open(file, 'r') as f:
        content = f.read()
    
    # insert after package com.example
    content = content.replace("package com.example\n", "package com.example\n" + add_imports)
    with open(file, 'w') as f:
        f.write(content)
