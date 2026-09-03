package com.example.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.CopyOnWriteArraySet

class EqBand(
    val id: Int,
    val name: String,
    val minLevelDb: Int = -12,
    val maxLevelDb: Int = 12,
    initialLevelDb: Int = 0
) {
    var currentLevelDb by mutableStateOf(initialLevelDb.coerceIn(minLevelDb, maxLevelDb))
}

/**
 * UI/state controller for the app EQ.
 *
 * Android's native Equalizer is intentionally never attached. All EQ work is
 * performed by DeckFxAudioProcessor in the PCM path. Multiple controller
 * instances exist because each deck owns one, so changes are broadcast to all
 * instances to keep the visible EQ and the real deck DSP synchronized.
 */
class EqualizerController(private val context: Context, private val onUpdate: () -> Unit = {}) {
    private val instanceRegistry = companionObjectRegistry

    val bands = mutableStateListOf(
        EqBand(0, "60 Hz"), EqBand(1, "170 Hz"), EqBand(2, "310 Hz"), EqBand(3, "600 Hz"),
        EqBand(4, "1 kHz"), EqBand(5, "3 kHz"), EqBand(6, "6 kHz"), EqBand(7, "12 kHz"),
        EqBand(8, "14 kHz"), EqBand(9, "16 kHz")
    )

    init {
        instanceRegistry.add(this)
        loadState()
    }

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

    /** Independent digital make-up gain applied inside the PCM EQ path. */
    var preampDb by mutableStateOf(0f)
        private set

    val presets = listOf(
        "Flat", "Dolby Music", "Dolby Cinema", "Dolby Dynamic", "Dolby Voice", "Dolby Game",
        "Bass Boost", "Rock", "Pop", "Jazz", "Electronic", "Vocal", "Concert", "Custom"
    )

    fun toggleEnable() {
        val nextEnabled = !isEnabled
        isEnabled = nextEnabled
        if (!nextEnabled) DeckFxAudioProcessor.setGlobalPreampDb(0f)
        persistState()
        broadcastState()
    }

    fun updateBandLevel(bandIndex: Int, levelDb: Int) {
        if (bandIndex !in bands.indices) return
        bands[bandIndex].currentLevelDb = levelDb.coerceIn(-12, 12)
        selectedPreset = "Custom"
        syncQuickFromBands()
        persistState()
        broadcastState()
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

    fun updatePreampDb(value: Float) {
        preampDb = value.coerceIn(0f, 12f)
        persistState()
        broadcastState()
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
        persistState()
        broadcastState()
    }

    private fun syncQuickFromBands() {
        quickBassDb = bands[0].currentLevelDb
        quickMidDb = bands[4].currentLevelDb
        quickTrebleDb = bands[9].currentLevelDb
    }

    private fun applySharedSnapshot(
        levels: FloatArray,
        enabled: Boolean,
        preset: String,
        sharedPreampDb: Float
    ) {
        for (i in bands.indices) {
            bands[i].currentLevelDb = levels.getOrElse(i) { 0f }.toInt().coerceIn(-12, 12)
        }
        isEnabled = enabled
        selectedPreset = preset
        preampDb = sharedPreampDb.coerceIn(0f, 12f)
        syncQuickFromBands()
        onUpdate()
    }

    private fun broadcastState() {
        val levels = bands.map { it.currentLevelDb.toFloat() }.toFloatArray()
        val enabled = isEnabled
        val preset = selectedPreset
        val sharedPreamp = if (enabled) preampDb else 0f
        DeckFxAudioProcessor.setGlobalPreampDb(sharedPreamp)
        for (controller in instanceRegistry) {
            controller.applySharedSnapshot(levels, enabled, preset, sharedPreamp)
        }
    }

    private fun persistState() {
        try {
            context.getSharedPreferences("quick_eq", Context.MODE_PRIVATE).edit()
                .putInt("bass", quickBassDb)
                .putInt("mid", quickMidDb)
                .putInt("treble", quickTrebleDb)
                .putString("preset", selectedPreset)
                .putBoolean("enabled", isEnabled)
                .putFloat("preamp", preampDb)
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
            preampDb = prefs.getFloat("preamp", 0f).coerceIn(0f, 12f)
            if (isEnabled) DeckFxAudioProcessor.setGlobalPreampDb(preampDb) else DeckFxAudioProcessor.setGlobalPreampDb(0f)
            syncQuickFromBands()
        } catch (_: Throwable) { }
    }

    fun release() {
        persistState()
        instanceRegistry.remove(this)
    }

    fun attachToSession(@Suppress("UNUSED_PARAMETER") newAudioSessionId: Int) {
        onUpdate()
    }

    companion object {
        private val companionObjectRegistry = CopyOnWriteArraySet<EqualizerController>()

        fun adjustQuickBand(context: Context, band: Int) { }
    }
}
