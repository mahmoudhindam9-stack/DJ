with open("/app/applet/app/src/main/java/com/example/djfx/FactoryFxCatalog.kt", "r") as f:
    lines = f.readlines()

# truncate after line 151
lines = lines[:151]

# add the closing brackets
lines.append("    )\n}\n")

# Make sure line 151 doesn't have a trailing comma
lines[150] = lines[150].replace('"),\n', '")\n')

with open("/app/applet/app/src/main/java/com/example/djfx/FactoryFxCatalog.kt", "w") as f:
    f.writelines(lines)
