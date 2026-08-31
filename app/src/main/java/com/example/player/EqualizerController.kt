package com.example.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/**
 * UI band model used by the 10-band equalizer screen.
 * Android's platform Equalizer may expose fewer physical bands, so the
 * controller maps these UI frequencies to the closest hardware bands.
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

    /** Hardware EQ band used by each UI band. Rebuilt for every audio session. */
    private var uiToHardwareBand: IntArray = IntArray(10) { it }

    /** Frequency targets match the labels shown in the UI. */
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
     * Attach effects to the active Media3/AudioTrack session.
     * The platform EQ commonly has 5 bands, not 10, so we map the UI to the
     * closest available hardware frequencies instead of blindly addressing
     * hardware bands 0..9.
     */
    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return

        try {
            equalizer?.release()
            bassBoostFx?.release()

            val newEqualizer = Equalizer(0, audioSessionId)
            val newBassBoost = BassBoost(0, audioSessionId)

            equalizer = newEqualizer
            bassBoostFx = newBassBoost
            buildHardwareMapping(newEqualizer)

            newEqualizer.enabled = isEnabled
            newBassBoost.enabled = isEnabled
            applyAllToHardware()
            applyBassBoostToHardware()
        } catch (t: Throwable) {
            // Some devices/ROMs do not expose the platform effect API.
            // Keep the UI fully usable and simply bypass hardware effects.
            equalizer = null
            bassBoostFx = null
            t.printStackTrace()
        }
    }

    private fun buildHardwareMapping(eq: Equalizer) {
        val count = eq.numberOfBands.toInt().coerceAtLeast(0)
        if (count == 0) {
            uiToHardwareBand = IntArray(10) { -1 }
            return
        }

        val hardwareHz = IntArray(count) { index ->
            // getCenterFreq returns milli-Hertz.
            eq.getCenterFreq(index.toShort()) / 1000
        }

        uiToHardwareBand = IntArray(targetFrequenciesHz.size) { uiIndex ->
            hardwareHz.indices.minByOrNull { bandIndex ->
                abs(hardwareHz[bandIndex] - targetFrequenciesHz[uiIndex])
            } ?: -1
        }
    }

    fun toggleEnable() {
        isEnabled = !isEnabled
        try {
            equalizer?.enabled = isEnabled
            bassBoostFx?.enabled = isEnabled
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

        // Re-apply the complete UI curve because multiple UI frequencies may
        // legitimately map to the same physical hardware band.
        applyAllToHardware()
    }

    fun updateBassBoost(level: Float) {
        bassBoostLevel = level.coerceIn(0f, 1f)
        applyBassBoostToHardware()
    }

    fun applyPreset(presetName: String) {
        selectedPreset = if (presetName in presets) presetName else "Custom"

        // Always provide all 10 values. The old implementation only provided
        // five values, leaving the upper half of the 10-band UI unchanged.
        val presetValues = when (presetName) {
            "Bass Boost" -> listOf(8, 6, 4, 2, 1, 0, 0, 0, -1, -2)
            "Rock" -> listOf(5, 4, 1, 3, 4, 4, 5, 4, 3, 2)
            "Pop" -> listOf(-1, 2, 4, 4, 3, 2, 0, 2, 3, 4)
            "Jazz" -> listOf(4, 3, 1, 2, 3, 2, 1, 3, 4, 4)
            "Electronic" -> listOf(7, 6, 3, 1, 2, 4, 5, 6, 5, 4)
            "Vocal" -> listOf(-2, 0, 3, 5, 5, 4, 2, 1, 0, -1)
            else -> List(10) { 0 }
        }

        for (i in bands.indices) {
            bands[i] = bands[i].copy(currentLevelDb = presetValues[i])
        }

        applyAllToHardware()
    }

    /** Apply the 10-band UI curve to the available physical EQ bands. */
    private fun applyAllToHardware() {
        val eq = equalizer ?: return

        try {
            val count = eq.numberOfBands.toInt()
            if (count <= 0) return

            val groups = Array(count) { mutableListOf<Int>() }
            bands.indices.forEach { uiIndex ->
                val hwIndex = uiToHardwareBand.getOrNull(uiIndex) ?: -1
                if (hwIndex in 0 until count) {
                    groups[hwIndex].add(bands[uiIndex].currentLevelDb)
                }
            }

            val range = eq.bandLevelRange
            for (hwIndex in 0 until count) {
                val values = groups[hwIndex]
                val desiredDb = if (values.isEmpty()) 0 else values.average()
                val millibels = (desiredDb * 100.0).toInt()
                    .coerceIn(range[0].toInt(), range[1].toInt())
                    .toShort()
                eq.setBandLevel(hwIndex.toShort(), millibels)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun applyBassBoostToHardware() {
        try {
            bassBoostFx?.setStrength((bassBoostLevel * 1000f).toInt().coerceIn(0, 1000).toShort())
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoostFx?.release()
        } catch (t: Throwable) {
            t.printStackTrace()
        } finally {
            equalizer = null
            bassBoostFx = null
        }
    }
}
