with open("/app/applet/app/src/main/java/com/example/djfx/FactoryFxCatalog.kt", "r") as f:
    lines = f.readlines()

# Remove lines 11 through 18
del lines[10:18]
# Now we need to add the missing `)` to data class Entry
lines.insert(10, "    )\n")

with open("/app/applet/app/src/main/java/com/example/djfx/FactoryFxCatalog.kt", "w") as f:
    f.writelines(lines)
