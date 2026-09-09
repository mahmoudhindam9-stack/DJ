import re
import os

path = "app/src/main/java/com/example/MainActivity.kt"
with open(path, 'r') as f:
    content = f.read()

# We need a robust way to extract functions using bracket counting.
def extract_function(name):
    # Find @Composable fun Name(
    match = re.search(r'@Composable\s*fun\s+' + name + r'\s*\(', content)
    if not match:
        # Check without @Composable
        match = re.search(r'fun\s+' + name + r'\s*\(', content)
        if not match: return None, None
        
    start_index = match.start()
    if "@Composable" in content[start_index - 20:start_index]:
        start_index = content.rfind("@Composable", max(0, start_index - 20), start_index)
        
    # Find the opening brace of the function
    brace_match = re.search(r'\{', content[start_index:])
    if not brace_match: return None, None
    
    brace_index = start_index + brace_match.start()
    
    count = 1
    end_index = brace_index + 1
    while count > 0 and end_index < len(content):
        if content[end_index] == '{':
            count += 1
        elif content[end_index] == '}':
            count -= 1
        end_index += 1
        
    func_body = content[start_index:end_index]
    return func_body, (start_index, end_index)

def remove_and_save(names, out_file):
    global content
    funcs = []
    for name in names:
        body, bounds = extract_function(name)
        if body:
            funcs.append(body)
            content = content[:bounds[0]] + "\n" + content[bounds[1]:]
            
    if funcs:
        out_path = f"app/src/main/java/com/example/{out_file}"
        with open(out_path, 'w') as f:
            f.write("package com.example\n\nimport androidx.compose.runtime.*\nimport androidx.compose.ui.*\nimport androidx.compose.foundation.*\nimport androidx.compose.material3.*\nimport androidx.compose.foundation.layout.*\nimport androidx.compose.material.icons.*\nimport androidx.compose.material.icons.filled.*\nimport androidx.compose.ui.unit.*\nimport androidx.compose.ui.text.font.*\nimport androidx.compose.ui.text.style.*\nimport androidx.compose.ui.Alignment\nimport androidx.compose.ui.Modifier\nimport androidx.compose.ui.draw.clip\nimport androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.vector.ImageVector\nimport androidx.compose.foundation.lazy.*\nimport androidx.compose.foundation.shape.*\nimport android.widget.Toast\nimport android.content.Intent\nimport android.net.Uri\nimport androidx.compose.ui.layout.ContentScale\nimport coil.compose.AsyncImage\nimport com.example.model.*\nimport com.example.player.*\nimport kotlinx.coroutines.*\n\n")
            f.write("\n\n".join(funcs))
            
remove_and_save(["MicScreen"], "MicScreen.kt")
remove_and_save(["FullPlayerScreen", "NowPlayingCard"], "FullPlayerScreen.kt")
remove_and_save(["DJMixerScreen", "DJDeckItem", "DJPadButton"], "DJMixerScreen.kt")
remove_and_save(["EqualizerScreen", "VerticalFader"], "EqualizerScreen.kt")

with open(path, 'w') as f:
    f.write(content)
