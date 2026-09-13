with open("app/src/main/java/com/example/DJMixerScreen.kt", "r") as f:
    text = f.read()

# 1. Add verticalScroll to the main Column
text = text.replace(
    ".padding(top = 16.dp, bottom = 16.dp),",
    ".padding(top = 16.dp, bottom = 16.dp)\n            .verticalScroll(rememberScrollState()),",
    1
)

# 2. Remove weight(1f) from the Decks Row
text = text.replace(
    "Row(\n            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),",
    "Row(\n            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),",
    1
)

# 3. Change fillMaxHeight() to fillMaxWidth() in DJDeck and remove verticalScroll
text = text.replace(
    "modifier = Modifier.fillMaxHeight(),",
    "modifier = Modifier.fillMaxWidth(),",
    1
)
text = text.replace(
    ".padding(12.dp)\n                .verticalScroll(rememberScrollState()),",
    ".padding(12.dp),",
    1
)

with open("app/src/main/java/com/example/DJMixerScreen.kt", "w") as f:
    f.write(text)
