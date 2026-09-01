package com.example.player

import android.content.Context
import android.media.audiofx.Equalizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Safe, session-scoped equalizer.
 * Keeps the UI at +/- 6 dB and avoids stacking BassBoost/LoudnessEnhancer
 * on top of the device/vendor processing chain (for example Dolby).
 */
data class EqBand(
    val id: Int,
    val name: String,
    val minLevelDb: Int = -6,
    val maxLevelDb: Int = 6,
    var currentLevelDb: Int = 0
)

class EqualizerController(private val context: Context) {
    init {
        activeInstance = this
        loadQuickState()
    }

    private var equalizer: Equalizer? = null
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
    var trebleBoostLevel by mutableStateOf(0f)
        private set
    var selectedPreset by mutableStateOf("Flat")
        private set

    val presets = listOf("Flat", "Bass Boost", "Rock", "Pop", "Jazz", "Electronic", "Vocal", "Custom")

    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        try {
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId)
            val eq = equalizer ?: return
            val count = eq.numberOfBands.toInt().coerceAtLeast(0)
            hardwareFrequenciesHz = IntArray(count) { index ->
                eq.getCenterFreq(index.toShort()).toInt() / 1000
            }
            eq.enabled = isEnabled
            applyAllToHardware()
        } catch (t: Throwable) {
            equalizer = null
            hardwareFrequenciesHz = IntArray(0)
            t.printStackTrace()
        }
    }

    fun toggleEnable() {
        isEnabled = !isEnabled
        try {
            equalizer?.enabled = isEnabled
            applyAllToHardware()
        } catch (t: Throwable) { t.printStackTrace() }
    }

    fun updateBandLevel(bandIndex: Int, levelDb: Int) {
        if (bandIndex !in bands.indices) return
        val safe = levelDb.coerceIn(-6, 6)
        bands[bandIndex] = bands[bandIndex].copy(currentLevelDb = safe)
        selectedPreset = "Custom"
        persistQuickState()
        applyAllToHardware()
    }

    fun updateBassBoost(level: Float) {
        bassBoostLevel = level.coerceIn(0f, 1f)
        selectedPreset = if (selectedPreset == "Flat") "Custom" else selectedPreset
        applyAllToHardware()
    }

    fun updateTrebleBoost(level: Float) {
        trebleBoostLevel = level.coerceIn(0f, 1f)
        selectedPreset = if (selectedPreset == "Flat") "Custom" else selectedPreset
        applyAllToHardware()
    }

    fun applyPreset(presetName: String) {
        selectedPreset = if (presetName in presets) presetName else "Custom"
        bassBoostLevel = 0f
        trebleBoostLevel = 0f
        val values = when (presetName) {
            "Bass Boost" -> listOf(5, 4, 3, 2, 1, 0, -1, -1, -2, -2)
            "Rock" -> listOf(4, 3, 1, 2, 3, 3, 4, 3, 2, 1)
            "Pop" -> listOf(-1, 1, 3, 3, 2, 1, 0, 1, 2, 3)
            "Jazz" -> listOf(3, 2, 1, 2, 3, 2, 1, 2, 3, 3)
            "Electronic" -> listOf(5, 4, 2, 1, 1, 3, 4, 5, 4, 3)
            "Vocal" -> listOf(-2, -1, 1, 3, 4, 3, 2, 1, 0, -1)
            else -> List(10) { 0 }
        }
        for (i in bands.indices) bands[i] = bands[i].copy(currentLevelDb = values[i].coerceIn(-6, 6))
        persistQuickState()
        applyAllToHardware()
    }

    private fun applyAllToHardware() {
        val eq = equalizer ?: return
        if (hardwareFrequenciesHz.isEmpty()) return
        try {
            val range = eq.bandLevelRange
            val rangeMinDb = range[0].toInt() / 100
            val rangeMaxDb = range[1].toInt() / 100
            for (hardwareIndex in hardwareFrequenciesHz.indices) {
                val frequencyHz = hardwareFrequenciesHz[hardwareIndex]
                val base = interpolateUiGain(frequencyHz)
                val bassAdd = if (frequencyHz <= 310) bassBoostLevel * 2.0 else 0.0
                val trebleAdd = if (frequencyHz >= 6000) trebleBoostLevel * 2.0 else 0.0
                val totalDb = (base + bassAdd + trebleAdd).coerceIn(-6.0, 6.0)
                val safeDb = totalDb.coerceIn(rangeMinDb.toDouble(), rangeMaxDb.toDouble())
                eq.setBandLevel(hardwareIndex.toShort(), (safeDb * 100.0).toInt().toShort())
            }
        } catch (t: Throwable) { t.printStackTrace() }
    }

    private fun interpolateUiGain(frequencyHz: Int): Double {
        if (frequencyHz <= 60) return bands.first().currentLevelDb.toDouble()
        if (frequencyHz >= 16000) return bands.last().currentLevelDb.toDouble()
        val f = intArrayOf(60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000)
        for (i in 0 until f.lastIndex) {
            if (frequencyHz <= f[i + 1]) {
                val x = (frequencyHz - f[i]).toDouble() / (f[i + 1] - f[i]).toDouble()
                val a = bands[i].currentLevelDb.toDouble()
                val b = bands[i + 1].currentLevelDb.toDouble()
                return a + (b - a) * x
            }
        }
        return 0.0
    }

    private fun persistQuickState() {
        try {
            context.getSharedPreferences("quick_eq", Context.MODE_PRIVATE).edit()
                .putInt("bass", bands[0].currentLevelDb)
                .putInt("mid", bands[4].currentLevelDb)
                .putInt("treble", bands[9].currentLevelDb)
                .apply()
        } catch (_: Throwable) { }
    }

    private fun loadQuickState() {
        try {
            val prefs = context.getSharedPreferences("quick_eq", Context.MODE_PRIVATE)
            bands[0] = bands[0].copy(currentLevelDb = prefs.getInt("bass", 0).coerceIn(-6, 6))
            bands[4] = bands[4].copy(currentLevelDb = prefs.getInt("mid", 0).coerceIn(-6, 6))
            bands[9] = bands[9].copy(currentLevelDb = prefs.getInt("treble", 0).coerceIn(-6, 6))
        } catch (_: Throwable) { }
    }

    fun release() {
        persistQuickState()
        if (activeInstance === this) activeInstance = null
        try { equalizer?.release() } catch (_: Throwable) { }
        equalizer = null
        hardwareFrequenciesHz = IntArray(0)
    }

    companion object {
        @Volatile var activeInstance: EqualizerController? = null

        fun adjustQuickBand(context: Context, band: Int) {
            val instance = activeInstance
            if (instance != null) {
                val index = when (band) { 0 -> 0; 1 -> 4; else -> 9 }
                val current = instance.bands[index].currentLevelDb
                instance.updateBandLevel(index, if (current >= 6) -6 else current + 1)
            } else {
                val prefs = context.getSharedPreferences("quick_eq", Context.MODE_PRIVATE)
                val key = when (band) { 0 -> "bass"; 1 -> "mid"; else -> "treble" }
                val current = prefs.getInt(key, 0)
                prefs.edit().putInt(key, if (current >= 6) -6 else current + 1).apply()
            }
        }
    }
}
