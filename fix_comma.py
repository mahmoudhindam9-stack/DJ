import re

with open("/app/applet/app/src/main/java/com/example/djfx/FactoryFxCatalog.kt", "r") as f:
    content = f.read()

content = content.replace('trombone.mp3")        // NEW BANK', 'trombone.mp3"),\n        // NEW BANK')

with open("/app/applet/app/src/main/java/com/example/djfx/FactoryFxCatalog.kt", "w") as f:
    f.write(content)
