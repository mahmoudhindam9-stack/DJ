with open('app/src/main/java/com/example/player/DJDeckController.kt', 'r') as f:
    text = f.read()

target = """    fun setVolumeLevel(newVolume: Float) {
        volume = newVolume.coerceIn(0f, 1f)
        exoPlayer.volume = volume
    }"""

replacement = """    fun setVolumeLevel(newVolume: Float) {
        volume = newVolume.coerceIn(0f, 1f)
        // Make sure it actually applies to ExoPlayer correctly
        try {
            exoPlayer.volume = volume
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }"""

text = text.replace(target, replacement)
with open('app/src/main/java/com/example/player/DJDeckController.kt', 'w') as f:
    f.write(text)
