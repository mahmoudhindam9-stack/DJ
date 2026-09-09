import re
import os

files = [
    "app/src/main/java/com/example/player/MicController.kt",
    "app/src/main/java/com/example/studio/NotificationControlScreen.kt",
    "app/src/main/java/com/example/studio/MusicStudioController.kt",
    "app/src/main/java/com/example/MainActivity.kt",
    "app/src/main/java/com/example/djfx/DjFxBoard.kt",
    "app/src/main/java/com/example/onlinemusic/OnlineMusicViewModel.kt",
    "app/src/main/java/com/example/onlinemusic/OnlineDjBridge.kt",
    "app/src/main/java/com/example/player/EqualizerController.kt",
    "app/src/main/java/com/example/player/DJDeckController.kt",
    "app/src/main/java/com/example/widget/MusicWidgetProvider.kt",
    "app/src/main/java/com/example/player/MusicService.kt"
]

for filepath in files:
    if not os.path.exists(filepath): continue
    with open(filepath, 'r') as f:
        content = f.read()

    lines = content.split('\n')
    new_lines = []
    
    # Strip out imports block to check the body
    imports = []
    body_lines = []
    for line in lines:
        if line.startswith("import "):
            imports.append(line)
        else:
            body_lines.append(line)
            
    body_text = "\n".join(body_lines)
    
    for imp in imports:
        # Ignore wildcards
        if imp.endswith(".*"):
            new_lines.append(imp)
            continue
            
        # Ignore getValue/setValue explicitly
        if "getValue" in imp or "setValue" in imp:
            new_lines.append(imp)
            continue
            
        # Get the class name
        # import com.example.ClassName
        # import com.example.ClassName as Something
        match = re.search(r'import\s+[\w\.]+\.(\w+)(?:\s+as\s+(\w+))?', imp)
        if match:
            target = match.group(2) if match.group(2) else match.group(1)
            
            # check if target is used in body
            # use regex word boundary
            if re.search(r'\b' + re.escape(target) + r'\b', body_text):
                new_lines.append(imp)
            else:
                print(f"Removed unused import: {imp} from {filepath}")
        else:
            new_lines.append(imp)
            
    final_content = ""
    for line in lines:
        if line.startswith("import "):
            if line in new_lines:
                final_content += line + "\n"
        else:
            final_content += line + "\n"
            
    # Fix trailing newlines if we messed them up, but easiest is just:
    with open(filepath, 'w') as f:
        f.write(final_content.strip() + "\n")

