package com.example.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * 10-band UI equalizer backed by Android's platform Equalizer.
 * Hardware EQ implementations may expose fewer bands, so the UI curve is
 * interpolated onto the real hardware center frequencies.
 */
data class EqBand(
    val id: Int,
    val name: String,
    val minLevelDb: Int = -12,
    val maxLevelDb: Int = 12,
    var currentLevelDb: Int = 0
)

class EqualizerController {
    private var equalizer: Equalizer? = null
    private var bassBoostFx: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val targetFrequenciesHz = intArrayOf(60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000)
    private var hardwareFrequenciesHz = IntArray(0)

    var isEnabled by mutableStateOf(false)
        private set

    val bands = mutableStateListOf(
        EqBand(0, "60 Hz"), EqBand(1, "170 Hz"), EqBand(2, "310 Hz"), EqBand(3, "600 Hz"),
        EqBand(4, "1 kHz"), EqBand(5, "3 kHz"), EqBand(6, "6 kHz"), EqBand(7, "12 kHz"),
        EqBand(8, "14 kHz"), EqBand(9, "16 kHz")
    )

    var bassBoostLevel by mutableStateOf(0f)
        private set

    var selectedPreset by mutableStateOf("Flat")
        private set

    val presets = listOf("Flat", "Bass Boost", "Rock", "Pop", "Jazz", "Electronic", "Vocal", "Custom")

    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        try {
            releaseEffects()

            val eq = Equalizer(0, audioSessionId)
            val bass = BassBoost(0, audioSessionId)
            val loudness = LoudnessEnhancer(audioSessionId)

            equalizer = eq
            bassBoostFx = bass
            loudnessEnhancer = loudness

            val count = eq.numberOfBands.toInt().coerceAtLeast(0)
            hardwareFrequenciesHz = IntArray(count) { index ->
                eq.getCenterFreq(index.toShort()).toInt() / 1000
            }

            eq.enabled = isEnabled
            bass.enabled = isEnabled
            loudness.enabled = isEnabled
            applyAllToHardware()
            applyBassBoostToHardware()
            applyMakeupGain()
        } catch (t: Throwable) {
            equalizer = null
            bassBoostFx = null
            loudnessEnhancer = null
            hardwareFrequenciesHz = IntArray(0)
            t.printStackTrace()
        }
    }

    fun toggleEnable() {
        isEnabled = !isEnabled
        try {
            equalizer?.enabled = isEnabled
            bassBoostFx?.enabled = isEnabled
            loudnessEnhancer?.enabled = isEnabled
            applyMakeupGain()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun updateBandLevel(bandIndex: Int, levelDb: Int) {
        if (bandIndex !in bands.indices) return
        val safe = levelDb.coerceIn(bands[bandIndex].minLevelDb, bands[bandIndex].maxLevelDb)
        bands[bandIndex] = bands[bandIndex].copy(currentLevelDb = safe)
        selectedPreset = "Custom"
        applyAllToHardware()
        applyMakeupGain()
    }

    fun updateBassBoost(level: Float) {
        bassBoostLevel = level.coerceIn(0f, 1f)
        applyBassBoostToHardware()
        applyMakeupGain()
    }

    fun applyPreset(presetName: String) {
        selectedPreset = if (presetName in presets) presetName else "Custom"
        val values = when (presetName) {
            "Bass Boost" -> listOf(8, 6, 4, 2, 1, 0, 0, 0, -1, -2)
            "Rock" -> listOf(5, 4, 1, 3, 4, 4, 5, 4, 3, 2)
            "Pop" -> listOf(-1, 2, 4, 4, 3, 2, 0, 2, 3, 4)
            "Jazz" -> listOf(4, 3, 1, 2, 3, 2, 1, 3, 4, 4)
            "Electronic" -> listOf(7, 6, 3, 1, 2, 4, 5, 6, 5, 4)
            "Vocal" -> listOf(-2, 0, 3, 5, 5, 4, 2, 1, 0, -1)
            else -> List(10) { 0 }
        }
        for (i in bands.indices) bands[i] = bands[i].copy(currentLevelDb = values[i])
        applyAllToHardware()
        applyBassBoostToHardware()
        applyMakeupGain()
    }

    /** Apply each real hardware band using a linear interpolation of the 10-band UI curve. */
    private fun applyAllToHardware() {
        val eq = equalizer ?: return
        if (hardwareFrequenciesHz.isEmpty()) return
        try {
            val range = eq.bandLevelRange
            for (hardwareIndex in hardwareFrequenciesHz.indices) {
                val desiredDb = interpolateUiGain(hardwareFrequenciesHz[hardwareIndex])
                val milliBels = (desiredDb * 100.0).roundToInt()
                    .coerceIn(range[0].toInt(), range[1].toInt()).toShort()
                eq.setBandLevel(hardwareIndex.toShort(), milliBels)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun interpolateUiGain(frequencyHz: Int): Double {
        if (frequencyHz <= targetFrequenciesHz.first()) return bands.first().currentLevelDb.toDouble()
        if (frequencyHz >= targetFrequenciesHz.last()) return bands.last().currentLevelDb.toDouble()

        for (i in 0 until targetFrequenciesHz.lastIndex) {
            val leftF = targetFrequenciesHz[i]
            val rightF = targetFrequenciesHz[i + 1]
            if (frequencyHz <= rightF) {
                val fraction = (frequencyHz - leftF).toDouble() / (rightF - leftF).toDouble()
                val leftGain = bands[i].currentLevelDb.toDouble()
                val rightGain = bands[i + 1].currentLevelDb.toDouble()
                return leftGain + ((rightGain - leftGain) * fraction)
            }
        }
        return 0.0
    }

    /**
     * Makeup gain is positive-only and intentionally modest so enabling EQ does
     * not reduce the user's original master level.
     */
    private fun applyMakeupGain() {
        val enhancer = loudnessEnhancer ?: return
        try {
            val nonZero = bands.any { it.currentLevelDb != 0 } || bassBoostLevel > 0f
            val gainDb: Double = if (!isEnabled || !nonZero) {
                0.0
            } else {
                val maxCutDb = bands.minOf { it.currentLevelDb }.coerceAtMost(0).let { -it }
                val boostHeadroomDb = bands.maxOf { it.currentLevelDb }.coerceAtLeast(0)
                val requested = 0.5 + (maxCutDb * 0.75) + (bassBoostLevel.toDouble() * 1.0) - (boostHeadroomDb * 0.15)
                requested.coerceIn(0.0, 4.0)
            }
            enhancer.setTargetGain((gainDb * 1000.0).roundToInt())
            enhancer.enabled = isEnabled
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun applyBassBoostToHardware() {
        try {
            val strength = (bassBoostLevel * 700f).roundToInt().coerceIn(0, 700).toShort()
            bassBoostFx?.setStrength(strength)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun releaseEffects() {
        try {
            equalizer?.release()
            bassBoostFx?.release()
            loudnessEnhancer?.release()
        } catch (t: Throwable) {
            t.printStackTrace()
        } finally {
            equalizer = null
            bassBoostFx = null
            loudnessEnhancer = null
        }
    }

    fun release() {
        releaseEffects()
        hardwareFrequenciesHz = IntArray(0)
    }
}
