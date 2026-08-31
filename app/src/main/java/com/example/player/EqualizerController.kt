package com.example.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * UI band model used by the 10-band equalizer screen.
 * Android devices frequently expose a different physical band count, so the
 * controller interpolates the 10-band curve onto the device's real bands.
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

    /** Physical hardware EQ band frequencies in Hz. */
    private var hardwareFrequenciesHz: IntArray = IntArray(0)

    /** Frequencies shown by the 10-band UI. */
    private val targetFrequenciesHz = intArrayOf(
        60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000
    )

    var isEnabled by mutableStateOf(false)
        private set

    val bands = mutableStateListOf(
        EqBand(0, "60 Hz"),
        EqBand(1, "170 Hz"),
        EqBand(2, "310 Hz"),
        EqBand(3, "600 Hz"),
        EqBand(4, "1 kHz"),
        EqBand(5, "3 kHz"),
        EqBand(6, "6 kHz"),
        EqBand(7, "12 kHz"),
        EqBand(8, "14 kHz"),
        EqBand(9, "16 kHz")
    )

    var bassBoostLevel by mutableStateOf(0f)
        private set

    var selectedPreset by mutableStateOf("Flat")
        private set

    val presets = listOf(
        "Flat", "Bass Boost", "Rock", "Pop", "Jazz", "Electronic", "Vocal", "Custom"
    )

    /**
     * Attach effects to the active Media3 audio session.
     *
     * The equalizer changes frequency content, which can make music sound
     * quieter. A small post-EQ loudness makeup gain is therefore used only
     * when the EQ is active and only enough to avoid the obvious volume drop.
     * Flat stays at 0 dB makeup so the original level is preserved exactly.
     */
    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return

        try {
            releaseEffects()

            val newEqualizer = Equalizer(0, audioSessionId)
            equalizer = newEqualizer

            hardwareFrequenciesHz = IntArray(newEqualizer.numberOfBands.toInt().coerceAtLeast(0)) { index ->
                (newEqualizer.getCenterFreq(index.toShort()) / 1000).coerceAtLeast(1)
            }

            bassBoostFx = BassBoost(0, audioSessionId)
            loudnessEnhancer = LoudnessEnhancer(audioSessionId)

            equalizer?.enabled = isEnabled
            bassBoostFx?.enabled = isEnabled
            loudnessEnhancer?.enabled = isEnabled

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

        val clamped = levelDb.coerceIn(
            bands[bandIndex].minLevelDb,
            bands[bandIndex].maxLevelDb
        )
        bands[bandIndex] = bands[bandIndex].copy(currentLevelDb = clamped)
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

        // Conservative musical curves. They intentionally avoid excessive cuts
        // and boosts so the EQ improves clarity without making the master sound
        // noticeably quieter or overly compressed.
        val presetValues = when (presetName) {
            "Bass Boost" -> listOf(5, 4, 2, 1, 0, 0, 0, 0, -1, -1)
            "Rock" -> listOf(4, 3, 1, 2, 3, 4, 4, 3, 2, 1)
            "Pop" -> listOf(-1, 1, 3, 3, 2, 2, 1, 2, 3, 3)
            "Jazz" -> listOf(3, 2, 1, 2, 2, 2, 1, 2, 3, 3)
            "Electronic" -> listOf(5, 4, 2, 1, 2, 3, 4, 5, 4, 3)
            "Vocal" -> listOf(-2, -1, 2, 4, 4, 3, 2, 1, 0, -1)
            else -> List(10) { 0 }
        }

        bands.indices.forEach { index ->
            bands[index] = bands[index].copy(currentLevelDb = presetValues[index])
        }

        applyAllToHardware()
        applyMakeupGain()
    }

    /**
     * Interpolate the 10-band UI curve onto the actual hardware bands.
     * Log-frequency interpolation is used because audio pitch perception is
     * approximately logarithmic, producing a much smoother curve than mapping
     * every UI band to the nearest physical band.
     */
    private fun applyAllToHardware() {
        val eq = equalizer ?: return
        val count = eq.numberOfBands.toInt()
        if (count <= 0 || hardwareFrequenciesHz.size != count) return

        try {
            val range = eq.bandLevelRange
            for (hardwareIndex in 0 until count) {
                val frequency = hardwareFrequenciesHz[hardwareIndex]
                val desiredDb = interpolateUiGain(frequency)
                val milliBels = (desiredDb * 100.0)
                    .roundToInt()
                    .coerceIn(range[0].toInt(), range[1].toInt())
                    .toShort()
                eq.setBandLevel(hardwareIndex.toShort(), milliBels)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun interpolateUiGain(frequencyHz: Int): Double {
        val f = frequencyHz.coerceAtLeast(targetFrequenciesHz.first())
        if (f <= targetFrequenciesHz.first()) return bands.first().currentLevelDb.toDouble()
        if (f >= targetFrequenciesHz.last()) return bands.last().currentLevelDb.toDouble()

        for (index in 0 until targetFrequenciesHz.lastIndex) {
            val leftF = targetFrequenciesHz[index]
            val rightF = targetFrequenciesHz[index + 1]
            if (f in leftF..rightF) {
                val leftGain = bands[index].currentLevelDb.toDouble()
                val rightGain = bands[index + 1].currentLevelDb.toDouble()
                val leftLog = ln(leftF.toDouble())
                val rightLog = ln(rightF.toDouble())
                val x = (ln(f.toDouble()) - leftLog) / (rightLog - leftLog)
                return leftGain + (rightGain - leftGain) * x
            }
        }
        return 0.0
    }

    /** Apply bass boost without letting it silently attenuate the master. */
    private fun applyBassBoostToHardware() {
        try {
            // A small boost is easier to keep clean than driving the platform
            // effect to full strength. Bass Boost remains independently enabled.
            val strength = (bassBoostLevel * 1000f)
                .roundToInt()
                .coerceIn(0, 1000)
                .toShort()
            bassBoostFx?.setStrength(strength)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    /**
     * Makeup gain is never negative, so enabling the EQ cannot lower the
     * original master volume. Non-flat curves get a modest 0..4 dB lift,
     * while Flat remains exactly 0 dB.
     */
    private fun applyMakeupGain() {
        val enhancer = loudnessEnhancer ?: return
        try {
            if (!isEnabled) {
                enhancer.setTargetGain(0)
                return
            }

            val nonZeroBandCount = bands.count { it.currentLevelDb != 0 }
            if (nonZeroBandCount == 0 && bassBoostLevel <= 0f) {
                enhancer.setTargetGain(0)
                return
            }

            val averageGain = bands.map { it.currentLevelDb.toDouble() }.average()
            val minimumBandGain = bands.minOf { it.currentLevelDb }
            val bassComponent = bassBoostLevel * 1.5

            // Compensate for attenuation while keeping a small presentation
            // lift so presets do not sound quieter than the unprocessed signal.
            val attenuationCompensation = (-averageGain).coerceAtLeast(0.0)
            val lowBandCompensation = (-minimumBandGain * 0.25).coerceAtLeast(0.0)
            val desiredDb = (0.75 + attenuationCompensation + bassComponent - lowBandCompensation)
                .coerceIn(0.0, 4.0)

            enhancer.setTargetGain((desiredDb * 1000.0).roundToInt())
            enhancer.enabled = true
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
