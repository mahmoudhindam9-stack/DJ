import re
content = open("app/src/main/java/com/example/radio/RadioScreen.kt").read()
content = content.replace("itemsIndexed(visibleStations, key = { _, it -> it.id }) { index, station ->", "itemsIndexed(visibleStations) { index, station ->")
open("app/src/main/java/com/example/radio/RadioScreen.kt", "w").write(content)
