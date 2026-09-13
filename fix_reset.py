with open('app/src/main/java/com/example/player/DeckFxAudioProcessor.kt', 'r') as f:
    content = f.read()

content = content.replace(
"""    override fun reset() {
        flush()
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
        sampleRate = 44_100
        channelCount = 2
        activeEffects = emptySet()
        eqEnabled = false
        appliedEqVersion = -1L
    }""",
"""    override fun reset() {
        flush()
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
        sampleRate = 44_100
        channelCount = 2
        updateActiveEffects(emptySet())
        eqEnabled = false
        appliedEqVersion = -1L
    }""")

with open('app/src/main/java/com/example/player/DeckFxAudioProcessor.kt', 'w') as f:
    f.write(content)

