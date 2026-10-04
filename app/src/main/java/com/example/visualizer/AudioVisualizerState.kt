package com.example.visualizer

import androidx.compose.runtime.Immutable

enum class VisualizerMode(val label: String) {
    OFF("Off"),
    SPECTRUM("Spectrum"),
    CIRCULAR("Circular"),
    WAVE("Wave"),
    AURORA("Aurora"),
    GALAXY("Galaxy"),
    TUNNEL("Tunnel")
}

@Immutable
data class AudioVisualizerState(
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val energy: Float = 0f,
    val peak: Float = 0f,
    val beat: Float = 0f,
    val bands: FloatArray = FloatArray(BAND_COUNT),
    val peakBands: FloatArray = FloatArray(BAND_COUNT),
    val wave: FloatArray = FloatArray(WAVE_COUNT),
    val isPlaying: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioVisualizerState

        if (bass != other.bass) return false
        if (mid != other.mid) return false
        if (treble != other.treble) return false
        if (energy != other.energy) return false
        if (peak != other.peak) return false
        if (beat != other.beat) return false
        if (!bands.contentEquals(other.bands)) return false
        if (!peakBands.contentEquals(other.peakBands)) return false
        if (!wave.contentEquals(other.wave)) return false
        if (isPlaying != other.isPlaying) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bass.hashCode()
        result = 31 * result + mid.hashCode()
        result = 31 * result + treble.hashCode()
        result = 31 * result + energy.hashCode()
        result = 31 * result + peak.hashCode()
        result = 31 * result + beat.hashCode()
        result = 31 * result + bands.contentHashCode()
        result = 31 * result + peakBands.contentHashCode()
        result = 31 * result + wave.contentHashCode()
        result = 31 * result + isPlaying.hashCode()
        return result
    }

    companion object {
        const val BAND_COUNT = 32
        const val WAVE_COUNT = 64
        val EMPTY = AudioVisualizerState()
    }
}
