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
    
    # Prepend @Composable to any 'fun ' that doesn't have it
    def repl(m):
        return "@Composable\nfun "
    
    content = re.sub(r'(?<!@Composable\n)(?<!@Composable\r\n)(?<!@Composable\s)fun\s', repl, content)
    
    with open(file, 'w') as f:
        f.write(content)
