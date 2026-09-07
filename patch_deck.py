with open('app/src/main/java/com/example/player/DJDeckController.kt', 'r') as f:
    text = f.read()

target = """    fun toggleEffect(fxId: String) {
        val currentlyActive = activeEffects[fxId] ?: false
        activeEffects[fxId] = !currentlyActive
        updateProcessorEffects()
    }"""

replacement = """    fun toggleEffect(fxId: String) {
        val currentlyActive = activeEffects[fxId] ?: false
        
        if (!currentlyActive && fxId.startsWith("voice_")) {
            activeEffects.keys.filter { it.startsWith("voice_") }.forEach {
                activeEffects[it] = false
            }
        }
        
        activeEffects[fxId] = !currentlyActive
        
        var newPitch = 1.0f
        if (activeEffects["voice_woman"] == true) newPitch = 1.4f
        else if (activeEffects["voice_kid"] == true) newPitch = 1.6f
        else if (activeEffects["voice_chipmunk"] == true) newPitch = 2.0f
        else if (activeEffects["voice_monster"] == true) newPitch = 0.7f
        else if (activeEffects["voice_demon"] == true) newPitch = 0.5f
        else if (activeEffects["voice_giant"] == true) newPitch = 0.6f
        
        setPlaybackPitch(newPitch)
        updateProcessorEffects()
    }"""

text = text.replace(target, replacement)
with open('app/src/main/java/com/example/player/DJDeckController.kt', 'w') as f:
    f.write(text)
