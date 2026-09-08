package com.example.player

/**
 * Authoritative EQ state shared by every audio output path in the app.
 * DJ decks, the normal player, online music, microphone monitor, sampler and
 * studio previews all read the same snapshot.
 */
object GlobalEqualizerState {
    private const val BAND_COUNT = 10

    @Volatile var enabled: Boolean = false
        private set
    @Volatile var levelsDb: FloatArray = FloatArray(BAND_COUNT)
        private set
    @Volatile var preampDb: Float = 0f
        private set
    @Volatile var version: Long = 0L
        private set

    @Synchronized
    fun update(levels: FloatArray, enabled: Boolean, preampDb: Float) {
        val next = FloatArray(BAND_COUNT)
        for (i in 0 until minOf(BAND_COUNT, levels.size)) {
            next[i] = levels[i].coerceIn(-12f, 12f)
        }
        levelsDb = next
        this.enabled = enabled
        this.preampDb = if (enabled) preampDb.coerceIn(0f, 12f) else 0f
        version++
    }
}
