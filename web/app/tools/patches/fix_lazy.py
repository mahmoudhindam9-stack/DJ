content = open("app/src/main/java/com/example/radio/RadioScreen.kt").read()
content = content.replace("itemsIndexed(visibleStations) { index, station ->", "items(visibleStations.size, key = { visibleStations[it].id }) { index -> val station = visibleStations[index]")
open("app/src/main/java/com/example/radio/RadioScreen.kt", "w").write(content)
