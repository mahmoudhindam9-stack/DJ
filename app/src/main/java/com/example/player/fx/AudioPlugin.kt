package com.example.player.fx

/**
 * Clean, modular interface for DJ FX plugins.
 * By using this interface, effects can be updated, added, or removed
 * without changing the core DeckFxAudioProcessor engine.
 */
interface AudioPlugin {
    val id: String
    val name: String
    val version: String
    
    var enabled: Boolean
    var amount: Float // 0.0 to 1.0 (Dry/Wet or intensity)

    /**
     * Called when the sample rate or channel count changes.
     */
    fun init(sampleRate: Int, channels: Int)

    /**
     * Process a single audio frame per channel.
     * Overriding this for multi-channel processing.
     * @param input Sample from -1.0 to 1.0
     * @param channel Current channel index (0 = Left, 1 = Right)
     * @return Processed sample from -1.0 to 1.0
     */
    fun process(input: Float, channel: Int): Float

    /**
     * Reset internal state (delays, LFOs, etc.)
     */
    fun reset()
}
