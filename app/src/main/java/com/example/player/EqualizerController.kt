package com.example.player

import android.content.Context
import android.media.audiofx.Equalizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Isolated player equalizer.
 * The native Equalizer effect is created only after the user enables it.
 * While disabled, the original player audio path is left untouched.
 */
data class EqBand(
    val id: Int,
    val name: String,
    val minLevelDb: Int = -12,
    val maxLevelDb: Int = 12,
    val currentLevelDb: Int = 0
)

class EqualizerController(private val context: Context) {
    init {
        activeInstance = this
        loadState()
    }

    private var equalizer: Equalizer? = null
    private var audioSessionId: Int = 0
    private var hardwareFrequenciesHz = IntArray(0)

    // Never restored as ON: enabling is always an explicit user action.
    var isEnabled by mutableStateOf(false)
        private set

    val bands = mutableStateListOf(
        EqBand(0, "60 Hz"), EqBand(1, "170 Hz"), EqBand(2, "310 Hz"), EqBand(3, "600 Hz"),
        EqBand(4, "1 kHz"), EqBand(5, "3 kHz"), EqBand(6, "6 kHz"), EqBand(7, "12 kHz"),
        EqBand(8, "14 kHz"), EqBand(9, "16 kHz")
    )

    var selectedPreset by mutableStateOf("Flat")
        private set

    var quickBassDb by mutableStateOf(0)
        private set
    var quickMidDb by mutableStateOf(0)
        private set
    var quickTrebleDb by mutableStateOf(0)
        private set

    // Kept for compatibility with existing callers; these are translated into EQ bands.
    var bassBoostLevel by mutableStateOf(0f)
        private set
    var trebleBoostLevel by mutableStateOf(0f)
        private set

    val presets = listOf(
        "Flat", "GM Booster", "Clean Power", "Bass Boost", "Rock", "Pop",
        "Jazz", "Electronic", "Vocal", "Concert", "Custom"
    )

    /** Store the session but do not create or touch any audio effect while disabled. */
    fun attachToSession(newAudioSessionId: Int) {
        if (newAudioSessionId <= 0) return
        if (audioSessionId == newAudioSessionId && (!isEnabled || equalizer != null)) return
        releaseEffect()
        audioSessionId = newAudioSessionId
        if (isEnabled) ensureEffect()
    }

    fun toggleEnable() {
        if (isEnabled) {
            isEnabled = false
            releaseEffect()
        } else {
            isEnabled = true
            if (!ensureEffect()) isEnabled = false
        }
    }

    private fun ensureEffect(): Boolean {
        if (!isEnabled && equalizer == null) return false
        if (equalizer != null) return true
        if (audioSessionId <= 0) return false
        return try {
            val eq = Equalizer(0, audioSessionId)
            val count = eq.numberOfBands.toInt().coerceAtLeast(0)
            hardwareFrequenciesHz = IntArray(count) { index ->
                eq.getCenterFreq(index.toShort()).toInt() / 1000
            }
            eq.enabled = true
            equalizer = eq
            applyAllToHardware()
            true
        } catch (_: Throwable) {
            try { equalizer?.release() } catch (_: Throwable) { }
            equalizer = null
            hardwareFrequenciesHz = IntArray(0)
            false
        }
    }

    fun updateBandLevel(bandIndex: Int, levelDb: Int) {
        if (bandIndex !in bands.indices) return
        val safe = levelDb.coerceIn(-12, 12)
        bands[bandIndex] = bands[bandIndex].copy(currentLevelDb = safe)
        selectedPreset = "Custom"
        syncQuickFromBands()
        persistState()
        if (isEnabled) applyAllToHardware()
    }

    fun setQuickBass(value: Int) = updateBandLevel(0, value)
    fun setQuickMid(value: Int) = updateBandLevel(4, value)
    fun setQuickTreble(value: Int) = updateBandLevel(9, value)

    fun updateBassBoost(level: Float) {
        bassBoostLevel = level.coerceIn(0f, 1f)
        val target = (-12 + (bassBoostLevel * 24f)).toInt()
        updateBandLevel(0, target)
    }

    fun updateTrebleBoost(level: Float) {
        trebleBoostLevel = level.coerceIn(0f, 1f)
        val target = (-12 + (trebleBoostLevel * 24f)).toInt()
        updateBandLevel(9, target)
    }

    fun applyPreset(presetName: String) {
        selectedPreset = if (presetName in presets) presetName else "Custom"
        val values = when (presetName) {
            "GM Booster" -> listOf(8, 7, 5, 3, 2, 1, 3, 5, 7, 8)
            "Clean Power" -> listOf(5, 4, 3, 1, 0, 1, 2, 3, 4, 5)
            "Bass Boost" -> listOf(9, 7, 5, 3, 1, 0, -1, -1, -2, -2)
            "Rock" -> listOf(7, 6, 3, 4, 6, 6, 7, 5, 4, 3)
            "Pop" -> listOf(-2, 2, 5, 5, 3, 2, 0, 2, 4, 5)
            "Jazz" -> listOf(4, 3, 2, 3, 4, 3, 2, 3, 4, 4)
            "Electronic" -> listOf(8, 6, 3, 1, 1, 3, 6, 8, 7, 5)
            "Vocal" -> listOf(-4, -2, 1, 5, 7, 6, 4, 2, 0, -2)
            "Concert" -> listOf(5, 3, 2, 2, 1, 2, 4, 5, 5, 4)
            else -> List(10) { 0 }
        }
        for (i in bands.indices) bands[i] = bands[i].copy(currentLevelDb = values[i].coerceIn(-12, 12))
        syncQuickFromBands()
        bassBoostLevel = 0f
        trebleBoostLevel = 0f
        persistState()
        if (isEnabled) applyAllToHardware()
    }

    private fun applyAllToHardware() {
        if (!isEnabled) return
        val eq = equalizer ?: return
        if (hardwareFrequenciesHz.isEmpty()) return
        try {
            val range = eq.bandLevelRange
            val rangeMinDb = range[0].toInt() / 100
            val rangeMaxDb = range[1].toInt() / 100
            for (i in hardwareFrequenciesHz.indices) {
                val hz = hardwareFrequenciesHz[i]
                val gainDb = interpolateUiGain(hz).coerceIn(rangeMinDb.toDouble(), rangeMaxDb.toDouble())
                eq.setBandLevel(i.toShort(), (gainDb * 100.0).toInt().toShort())
            }
        } catch (_: Throwable) { }
    }

    private fun interpolateUiGain(hz: Int): Double {
        val f = intArrayOf(60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000)
        if (hz <= f.first()) return bands.first().currentLevelDb.toDouble()
        if (hz >= f.last()) return bands.last().currentLevelDb.toDouble()
        for (i in 0 until f.lastIndex) {
            if (hz <= f[i + 1]) {
                val x = (hz - f[i]).toDouble() / (f[i + 1] - f[i]).toDouble()
                val a = bands[i].currentLevelDb.toDouble()
                val b = bands[i + 1].currentLevelDb.toDouble()
                return a + (b - a) * x
            }
        }
        return 0.0
    }

    private fun syncQuickFromBands() {
        quickBassDb = bands[0].currentLevelDb
        quickMidDb = bands[4].currentLevelDb
        quickTrebleDb = bands[9].currentLevelDb
    }

    private fun persistState() {
        try {
            context.getSharedPreferences("quick_eq", Context.MODE_PRIVATE).edit()
                .putInt("bass", quickBassDb)
                .putInt("mid", quickMidDb)
                .putInt("treble", quickTrebleDb)
                .putString("preset", selectedPreset)
                .apply()
        } catch (_: Throwable) { }
    }

    private fun loadState() {
        try {
            val prefs = context.getSharedPreferences("quick_eq", Context.MODE_PRIVATE)
            bands[0] = bands[0].copy(currentLevelDb = prefs.getInt("bass", 0).coerceIn(-12, 12))
            bands[4] = bands[4].copy(currentLevelDb = prefs.getInt("mid", 0).coerceIn(-12, 12))
            bands[9] = bands[9].copy(currentLevelDb = prefs.getInt("treble", 0).coerceIn(-12, 12))
            selectedPreset = prefs.getString("preset", "Flat") ?: "Flat"
            syncQuickFromBands()
        } catch (_: Throwable) { }
    }

    private fun releaseEffect() {
        try { equalizer?.enabled = false } catch (_: Throwable) { }
        try { equalizer?.release() } catch (_: Throwable) { }
        equalizer = null
        hardwareFrequenciesHz = IntArray(0)
    }

    fun release() {
        persistState()
        releaseEffect()
        if (activeInstance === this) activeInstance = null
        audioSessionId = 0
    }

    companion object {
        @Volatile var activeInstance: EqualizerController? = null

        fun adjustQuickBand(context: Context, band: Int) {
            val instance = activeInstance
            if (instance != null) {
                when (band) {
                    0 -> instance.setQuickBass(if (instance.quickBassDb >= 12) -12 else instance.quickBassDb + 1)
                    1 -> instance.setQuickMid(if (instance.quickMidDb >= 12) -12 else instance.quickMidDb + 1)
                    else -> instance.setQuickTreble(if (instance.quickTrebleDb >= 12) -12 else instance.quickTrebleDb + 1)
                }
            } else {
                val prefs = context.getSharedPreferences("quick_eq", Context.MODE_PRIVATE)
                val key = when (band) { 0 -> "bass"; 1 -> "mid"; else -> "treble" }
                val current = prefs.getInt(key, 0)
                prefs.edit().putInt(key, if (current >= 12) -12 else current + 1).apply()
            }
        }
    }
}
