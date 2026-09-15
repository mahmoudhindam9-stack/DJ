content = open("app/src/main/java/com/example/radio/RadioScreen.kt").read()
content = content.replace("items(visibleStations, key = { it.id }) { station ->", "itemsIndexed(visibleStations, key = { _, it -> it.id }) { index, station ->")
open("app/src/main/java/com/example/radio/RadioScreen.kt", "w").write(content)
