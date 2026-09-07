with open('app/src/main/java/com/example/player/DJDeckController.kt', 'r') as f:
    text = f.read()

target = """    fun updateCrossfader(value: Float) {
        crossfader = value.coerceIn(0f, 1f)
        deckA.setVolumeLevel(if (crossfader <= 0.5f) 1f else 1f - (crossfader - 0.5f) * 2f)
        deckB.setVolumeLevel(if (crossfader >= 0.5f) 1f else crossfader * 2f)
    }"""

replacement = """    fun updateCrossfader(value: Float) {
        crossfader = value.coerceIn(0f, 1f)
        // Logarithmic volume curve for DJ mixers instead of linear
        val volA = kotlin.math.cos(crossfader * (kotlin.math.PI / 2)).toFloat().coerceIn(0f, 1f)
        val volB = kotlin.math.sin(crossfader * (kotlin.math.PI / 2)).toFloat().coerceIn(0f, 1f)
        deckA.setVolumeLevel(volA)
        deckB.setVolumeLevel(volB)
    }"""

text = text.replace(target, replacement)

with open('app/src/main/java/com/example/player/DJDeckController.kt', 'w') as f:
    f.write(text)
