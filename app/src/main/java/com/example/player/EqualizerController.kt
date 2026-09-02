package com.example.player

import android.content.Context
import android.media.audiofx.Equalizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

class EqBand(
    val id: Int,
    val name: String,
    val minLevelDb: Int = -12,
    val maxLevelDb: Int = 12,
    initialLevelDb: Int = 0
) {
    var currentLevelDb by mutableStateOf(initialLevelDb.coerceIn(minLevelDb, maxLevelDb))
}

class EqualizerController(private val context: Context, private val onUpdate: () -> Unit = {}) {
    private var nativeEqualizer: Equalizer? = null
    private var attachedSessionId: Int = -1

    val bands = mutableStateListOf(
        EqBand(0, "60 Hz"), EqBand(1, "170 Hz"), EqBand(2, "310 Hz"), EqBand(3, "600 Hz"),
        EqBand(4, "1 kHz"), EqBand(5, "3 kHz"), EqBand(6, "6 kHz"), EqBand(7, "12 kHz"),
        EqBand(8, "14 kHz"), EqBand(9, "16 kHz")
    )

    init { loadState() }

    var isEnabled by mutableStateOf(false)
        private set
    var selectedPreset by mutableStateOf("Flat")
        private set
    var quickBassDb by mutableStateOf(0)
        private set
    var quickMidDb by mutableStateOf(0)
        private set
    var quickTrebleDb by mutableStateOf(0)
        private set
    var isDolbyAtmosEnabled by mutableStateOf(false)
        private set
    var bassBoostLevel by mutableStateOf(0f)
        private set
    var trebleBoostLevel by mutableStateOf(0f)
        private set

    val presets = listOf(
        "Flat", "Dolby Music", "Dolby Cinema", "Dolby Dynamic", "Dolby Voice", "Dolby Game",
        "Bass Boost", "Rock", "Pop", "Jazz", "Electronic", "Vocal", "Concert", "Custom"
    )

    fun toggleEnable() {
        isEnabled = !isEnabled
        applyToNative()
        persistState()
        onUpdate()
    }

    fun updateBandLevel(bandIndex: Int, levelDb: Int) {
        if (bandIndex !in bands.indices) return
        bands[bandIndex].currentLevelDb = levelDb.coerceIn(-12, 12)
        selectedPreset = "Custom"
        syncQuickFromBands()
        applyToNative()
        persistState()
        onUpdate()
    }

    fun setQuickBass(value: Int) = updateBandLevel(0, value)
    fun setQuickMid(value: Int) = updateBandLevel(4, value)
    fun setQuickTreble(value: Int) = updateBandLevel(9, value)

    fun updateBassBoost(level: Float) {
        bassBoostLevel = level.coerceIn(0f, 1f)
        updateBandLevel(0, (-6 + bassBoostLevel * 12f).toInt())
    }

    fun updateTrebleBoost(level: Float) {
        trebleBoostLevel = level.coerceIn(0f, 1f)
        updateBandLevel(9, (-6 + trebleBoostLevel * 12f).toInt())
    }

    fun applyPreset(presetName: String) {
        selectedPreset = if (presetName in presets) presetName else "Custom"
        val values = when (presetName) {
            "Dolby Music" -> listOf(5, 4, 3, 1, 2, 3, 4, 5, 4, 3)
            "Dolby Cinema" -> listOf(3, 2, 1, 0, 1, 3, 4, 5, 4, 3)
            "Dolby Dynamic" -> listOf(4, 3, 2, 1, 2, 3, 5, 5, 4, 4)
            "Dolby Voice" -> listOf(-3, -2, -1, 2, 5, 6, 4, 2, 1, 0)
            "Dolby Game" -> listOf(4, 3, 1, 0, 2, 4, 5, 4, 3, 2)
            "Bass Boost" -> listOf(9, 7, 5, 3, 1, 0, -1, -1, -2, -2)
            "Rock" -> listOf(7, 6, 3, 4, 6, 6, 7, 5, 4, 3)
            "Pop" -> listOf(-2, 2, 5, 5, 3, 2, 0, 2, 4, 5)
            "Jazz" -> listOf(4, 3, 2, 3, 4, 3, 2, 3, 4, 4)
            "Electronic" -> listOf(8, 6, 3, 1, 1, 3, 6, 8, 7, 5)
            "Vocal" -> listOf(-4, -2, 1, 5, 7, 6, 4, 2, 0, -2)
            "Concert" -> listOf(5, 3, 2, 2, 1, 2, 4, 5, 5, 4)
            else -> List(10) { 0 }
        }
        for (i in bands.indices) bands[i].currentLevelDb = values[i].coerceIn(-12, 12)
        syncQuickFromBands()
        isEnabled = true
        applyToNative()
        persistState()
        onUpdate()
    }

    private fun syncQuickFromBands() {
        quickBassDb = bands[0].currentLevelDb
        quickMidDb = bands[4].currentLevelDb
        quickTrebleDb = bands[9].currentLevelDb
    }

    private fun desiredFrequenciesHz(): IntArray = intArrayOf(60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000)

    private fun levelForNativeBand(nativeIndex: Int): Short {
        val eq = nativeEqualizer ?: return 0
        val centerHz = eq.getCenterFreq(nativeIndex.toShort()) / 1000
        val sourceIndex = desiredFrequenciesHz().indices.minByOrNull { index ->
            abs(desiredFrequenciesHz()[index] - centerHz)
        } ?: 0
        val range = eq.bandLevelRange
        val milliDb = bands[sourceIndex].currentLevelDb * 100
        return milliDb.coerceIn(range[0].toInt(), range[1].toInt()).toShort()
    }

    private fun applyToNative() {
        val eq = nativeEqualizer ?: return
        try {
            eq.enabled = isEnabled
            for (i in 0 until eq.numberOfBands.toInt()) {
                eq.setBandLevel(i.toShort(), levelForNativeBand(i))
            }
        } catch (_: Throwable) { }
    }

    private fun recreateNativeEqualizer(sessionId: Int) {
        if (sessionId <= 0 || sessionId == attachedSessionId) return
        try { nativeEqualizer?.release() } catch (_: Throwable) { }
        nativeEqualizer = try {
            Equalizer(1000, sessionId).also { it.enabled = false }
        } catch (_: Throwable) {
            null
        }
        attachedSessionId = sessionId
        applyToNative()
    }

    private fun persistState() {
        try {
            context.getSharedPreferences("quick_eq", Context.MODE_PRIVATE).edit()
                .putInt("bass", quickBassDb)
                .putInt("mid", quickMidDb)
                .putInt("treble", quickTrebleDb)
                .putString("preset", selectedPreset)
                .putBoolean("enabled", isEnabled)
                .apply()
        } catch (_: Throwable) { }
    }

    private fun loadState() {
        try {
            val prefs = context.getSharedPreferences("quick_eq", Context.MODE_PRIVATE)
            bands[0].currentLevelDb = prefs.getInt("bass", 0).coerceIn(-12, 12)
            bands[4].currentLevelDb = prefs.getInt("mid", 0).coerceIn(-12, 12)
            bands[9].currentLevelDb = prefs.getInt("treble", 0).coerceIn(-12, 12)
            selectedPreset = prefs.getString("preset", "Flat") ?: "Flat"
            isEnabled = prefs.getBoolean("enabled", false)
            syncQuickFromBands()
        } catch (_: Throwable) { }
    }

    fun release() {
        persistState()
        try { nativeEqualizer?.release() } catch (_: Throwable) { }
        nativeEqualizer = null
        attachedSessionId = -1
    }

    fun attachToSession(newAudioSessionId: Int) {
        recreateNativeEqualizer(newAudioSessionId)
        onUpdate()
    }

    companion object {
        fun adjustQuickBand(context: Context, band: Int) { }
    }
}
