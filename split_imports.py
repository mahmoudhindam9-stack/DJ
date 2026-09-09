import re

main = "app/src/main/java/com/example/MainActivity.kt"
with open(main, 'r') as f:
    main_content = f.read()
    
imports = re.findall(r'^import\s+.*$', main_content, flags=re.MULTILINE)
imports_str = "\n".join(imports)

files = [
    "app/src/main/java/com/example/MicScreen.kt",
    "app/src/main/java/com/example/FullPlayerScreen.kt",
    "app/src/main/java/com/example/DJMixerScreen.kt",
    "app/src/main/java/com/example/EqualizerScreen.kt"
]

for file in files:
    with open(file, 'r') as f:
        content = f.read()
    # Replace the top imports
    content = re.sub(r'package com\.example\n(?:import .*\n)+', f"package com.example\n\n{imports_str}\n\n", content)
    with open(file, 'w') as f:
        f.write(content)
