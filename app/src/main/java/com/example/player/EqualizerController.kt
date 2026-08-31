package com.example.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.compose.runtime.*

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

    var isEnabled by mutableStateOf(false)
        private set

    val bands = mutableStateListOf(
        EqBand(0, "60 Hz", currentLevelDb = 0),
        EqBand(1, "170 Hz", currentLevelDb = 0),
        EqBand(2, "310 Hz", currentLevelDb = 0),
        EqBand(3, "600 Hz", currentLevelDb = 0),
        EqBand(4, "1 kHz", currentLevelDb = 0),
        EqBand(5, "3 kHz", currentLevelDb = 0),
        EqBand(6, "6 kHz", currentLevelDb = 0),
        EqBand(7, "12 kHz", currentLevelDb = 0),
        EqBand(8, "14 kHz", currentLevelDb = 0),
        EqBand(9, "16 kHz", currentLevelDb = 0)
    )

    var bassBoostLevel by mutableStateOf(0f) // 0.0 to 1.0
        private set

    var selectedPreset by mutableStateOf("Flat")
        private set

    val presets = listOf("Flat", "Bass Boost", "Rock", "Pop", "Jazz", "Electronic", "Vocal", "Custom")

    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        try {
            equalizer?.release()
            bassBoostFx?.release()

            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = isEnabled
            }
            bassBoostFx = BassBoost(0, audioSessionId).apply {
                enabled = isEnabled
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleEnable() {
        isEnabled = !isEnabled
        try {
            equalizer?.enabled = isEnabled
            bassBoostFx?.enabled = isEnabled
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateBandLevel(bandIndex: Int, levelDb: Int) {
        if (bandIndex in bands.indices) {
            bands[bandIndex] = bands[bandIndex].copy(currentLevelDb = levelDb)
            selectedPreset = "Custom"
            applyToHardware(bandIndex, levelDb)
        }
    }

    fun updateBassBoost(level: Float) {
        bassBoostLevel = level.coerceIn(0f, 1f)
        try {
            bassBoostFx?.apply {
                setStrength((bassBoostLevel * 1000).toInt().toShort())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun applyPreset(presetName: String) {
        selectedPreset = presetName
        val presetValues = when (presetName) {
            "Bass Boost" -> listOf(6, 4, 0, 1, 2)
            "Rock" -> listOf(5, 3, -1, 3, 5)
            "Pop" -> listOf(-1, 2, 5, 3, -2)
            "Jazz" -> listOf(4, 2, 0, 2, 4)
            "Electronic" -> listOf(6, 5, 0, 4, 5)
            "Vocal" -> listOf(-2, 1, 5, 3, 0)
            else -> listOf(0, 0, 0, 0, 0) // Flat
        }

        for (i in bands.indices) {
            val valDb = presetValues.getOrElse(i) { 0 }
            bands[i] = bands[i].copy(currentLevelDb = valDb)
            applyToHardware(i, valDb)
        }
    }

    private fun applyToHardware(bandIndex: Int, levelDb: Int) {
        try {
            equalizer?.let { eq ->
                val numBands = eq.numberOfBands.toInt()
                if (bandIndex < numBands) {
                    val millibels = (levelDb * 100).toShort()
                    val range = eq.bandLevelRange
                    val clamped = millibels.coerceIn(range[0], range[1])
                    eq.setBandLevel(bandIndex.toShort(), clamped)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoostFx?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
