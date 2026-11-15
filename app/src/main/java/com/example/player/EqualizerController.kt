package com.example.player

import com.example.diagnostics.RuntimeDiagnostics

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArraySet

data class CustomEqPreset(
    val name: String,
    val bandLevelsDb: List<Int>,
    val preampDb: Float,
    val bassBoostLevel: Float,
    val trebleBoostLevel: Float
)

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
 * performed by DeckFxAudioProcessor in the PCM path.
 */
class EqualizerController(private val context: Context, private val onUpdate: () -> Unit = {}) {
    private val instanceRegistry = companionObjectRegistry

    val bands = mutableStateListOf(
        EqBand(0, "60 Hz"), EqBand(1, "170 Hz"), EqBand(2, "310 Hz"), EqBand(3, "600 Hz"),
        EqBand(4, "1 kHz"), EqBand(5, "3 kHz"), EqBand(6, "6 kHz"), EqBand(7, "12 kHz"),
        EqBand(8, "14 kHz"), EqBand(9, "16 kHz")
    )

    val customBands = IntArray(10) { 0 }
    var customPreampDb = 0f
        private set
    var customBassBoostLevel = 0f
        private set
    var customTrebleBoostLevel = 0f
        private set

    var isEnabled by mutableStateOf(true)
        private set
    var selectedPreset by mutableStateOf("Custom")
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

    val builtInPresets = listOf(
        "Custom", "Flat", "Dolby Music", "Dolby Cinema", "Dolby Dynamic", "Dolby Voice", "Dolby Game",
        "Bass Boost", "Rock", "Pop", "Jazz", "Electronic", "Vocal", "Concert"
    )

    var customPresets by mutableStateOf<List<CustomEqPreset>>(emptyList())
        private set

    /** Tracks which custom preset was active prior to slider edits, so users can 1-tap overwrite it. */
    var editingCustomPresetName by mutableStateOf<String?>(null)
        private set

    val presets: List<String>
        get() = builtInPresets + customPresets.map { it.name }

    init {
        instanceRegistry.add(this)
        loadState()
    }

    fun isBuiltInPreset(name: String): Boolean =
        builtInPresets.any { it.equals(name.trim(), ignoreCase = true) }

    fun isCustomPreset(name: String): Boolean =
        customPresets.any { it.name.equals(name.trim(), ignoreCase = true) }

    fun toggleEnable() {
        val nextEnabled = !isEnabled
        isEnabled = nextEnabled
        persistState()
        broadcastState()
        recordDiagnosticsState()
    }

    fun updateBandLevel(bandIndex: Int, levelDb: Int) {
        if (bandIndex !in bands.indices) return
        val clamped = levelDb.coerceIn(-12, 12)
        bands[bandIndex].currentLevelDb = clamped
        customBands[bandIndex] = clamped
        if (isBuiltInPreset(selectedPreset) && selectedPreset != "Custom") {
            editingCustomPresetName = null
        }
        selectedPreset = "Custom"
        customPreampDb = preampDb
        customBassBoostLevel = bassBoostLevel
        customTrebleBoostLevel = trebleBoostLevel
        syncQuickFromBands()
        persistState()
        broadcastState()
        recordDiagnosticsState()
    }

    fun setQuickBass(value: Int) = updateBandLevel(0, value)
    fun setQuickMid(value: Int) = updateBandLevel(4, value)
    fun setQuickTreble(value: Int) = updateBandLevel(9, value)

    fun updateBassBoost(level: Float) {
        bassBoostLevel = level.coerceIn(0f, 1f)
        if (selectedPreset == "Custom") {
            customBassBoostLevel = bassBoostLevel
        }
        persistState()
        broadcastState()
        recordDiagnosticsState()
    }

    fun updateTrebleBoost(level: Float) {
        trebleBoostLevel = level.coerceIn(0f, 1f)
        if (selectedPreset == "Custom") {
            customTrebleBoostLevel = trebleBoostLevel
        }
        persistState()
        broadcastState()
        recordDiagnosticsState()
    }

    // Named updatePreampDb to avoid the JVM setter clash with the preampDb property.
    fun updatePreampDb(value: Float) {
        preampDb = value.coerceIn(0f, 12f)
        if (selectedPreset == "Custom") {
            customPreampDb = preampDb
        }
        persistState()
        broadcastState()
        recordDiagnosticsState()
    }

    fun applyPreset(presetName: String) {
        val customMatch = customPresets.firstOrNull { it.name.equals(presetName, ignoreCase = true) }
        if (customMatch != null) {
            selectedPreset = customMatch.name
            editingCustomPresetName = customMatch.name
            for (i in bands.indices) {
                val lvl = customMatch.bandLevelsDb.getOrElse(i) { 0 }.coerceIn(-12, 12)
                bands[i].currentLevelDb = lvl
                customBands[i] = lvl
            }
            preampDb = customMatch.preampDb.coerceIn(0f, 12f)
            bassBoostLevel = customMatch.bassBoostLevel.coerceIn(0f, 1f)
            trebleBoostLevel = customMatch.trebleBoostLevel.coerceIn(0f, 1f)
            customPreampDb = preampDb
            customBassBoostLevel = bassBoostLevel
            customTrebleBoostLevel = trebleBoostLevel
            syncQuickFromBands()
            isEnabled = true
            persistState()
            broadcastState()
            recordDiagnosticsState()
            return
        }

        if (presetName == "Custom") {
            selectedPreset = "Custom"
            for (i in bands.indices) {
                bands[i].currentLevelDb = customBands[i].coerceIn(-12, 12)
            }
            preampDb = customPreampDb.coerceIn(0f, 12f)
            bassBoostLevel = customBassBoostLevel.coerceIn(0f, 1f)
            trebleBoostLevel = customTrebleBoostLevel.coerceIn(0f, 1f)
            syncQuickFromBands()
            isEnabled = true
            persistState()
            broadcastState()
            recordDiagnosticsState()
            return
        }

        editingCustomPresetName = null
        selectedPreset = if (presetName in builtInPresets) presetName else "Custom"
        val values = when (presetName) {
            "Flat" -> List(10) { 0 }
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
        recordDiagnosticsState()
    }

    fun saveCustomPreset(rawName: String): Result<CustomEqPreset> {
        val cleanName = rawName.trim()
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Preset name cannot be empty"))
        }
        if (isBuiltInPreset(cleanName)) {
            return Result.failure(IllegalArgumentException("Cannot overwrite built-in preset \"$cleanName\""))
        }
        val snapshot = CustomEqPreset(
            name = cleanName,
            bandLevelsDb = bands.map { it.currentLevelDb.coerceIn(-12, 12) },
            preampDb = preampDb.coerceIn(0f, 12f),
            bassBoostLevel = bassBoostLevel.coerceIn(0f, 1f),
            trebleBoostLevel = trebleBoostLevel.coerceIn(0f, 1f)
        )
        val existingIdx = customPresets.indexOfFirst { it.name.equals(cleanName, ignoreCase = true) }
        val updatedList = customPresets.toMutableList()
        if (existingIdx >= 0) {
            updatedList[existingIdx] = snapshot
        } else {
            updatedList.add(snapshot)
        }
        customPresets = updatedList
        selectedPreset = snapshot.name
        editingCustomPresetName = snapshot.name
        for (i in bands.indices) customBands[i] = snapshot.bandLevelsDb[i]
        customPreampDb = snapshot.preampDb
        customBassBoostLevel = snapshot.bassBoostLevel
        customTrebleBoostLevel = snapshot.trebleBoostLevel
        persistState()
        broadcastState()
        recordDiagnosticsState()
        return Result.success(snapshot)
    }

    fun updateCustomPreset(name: String): Result<CustomEqPreset> {
        val cleanName = name.trim()
        if (isBuiltInPreset(cleanName)) {
            return Result.failure(IllegalArgumentException("Built-in presets cannot be modified"))
        }
        val existing = customPresets.firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
            ?: return Result.failure(IllegalArgumentException("Custom preset \"$cleanName\" not found"))
        return saveCustomPreset(existing.name)
    }

    fun deleteCustomPreset(name: String): Boolean {
        val cleanName = name.trim()
        if (isBuiltInPreset(cleanName)) return false
        val idx = customPresets.indexOfFirst { it.name.equals(cleanName, ignoreCase = true) }
        if (idx < 0) return false
        val removed = customPresets[idx]
        customPresets = customPresets.filterIndexed { i, _ -> i != idx }
        if (selectedPreset.equals(removed.name, ignoreCase = true)) {
            selectedPreset = "Custom"
            for (i in bands.indices) customBands[i] = bands[i].currentLevelDb
            customPreampDb = preampDb
            customBassBoostLevel = bassBoostLevel
            customTrebleBoostLevel = trebleBoostLevel
        }
        if (editingCustomPresetName.equals(removed.name, ignoreCase = true)) {
            editingCustomPresetName = null
        }
        persistState()
        broadcastState()
        recordDiagnosticsState()
        return true
    }

    private fun recordDiagnosticsState() {
        RuntimeDiagnostics.recordEqState(
            isEnabled,
            bands.map { it.currentLevelDb.toFloat() }.toFloatArray(),
            preampDb,
            bassBoostLevel * 12f,
            trebleBoostLevel * 12f,
            selectedPreset
        )
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
        sharedPreampDb: Float,
        sharedBassBoost: Float,
        sharedTrebleBoost: Float,
        sharedCustomPresets: List<CustomEqPreset>
    ) {
        for (i in bands.indices) {
            bands[i].currentLevelDb = levels.getOrElse(i) { 0f }.toInt().coerceIn(-12, 12)
        }
        isEnabled = enabled
        selectedPreset = preset
        preampDb = sharedPreampDb.coerceIn(0f, 12f)
        bassBoostLevel = sharedBassBoost.coerceIn(0f, 1f)
        trebleBoostLevel = sharedTrebleBoost.coerceIn(0f, 1f)
        customPresets = sharedCustomPresets
        syncQuickFromBands()
        onUpdate()
    }

    /** Push one authoritative EQ snapshot to every deck-owned controller. */
    private fun broadcastState() {
        val levels = bands.map { it.currentLevelDb.toFloat() }.toFloatArray()
        val enabled = isEnabled
        val preset = selectedPreset
        val sharedPreamp = if (enabled) preampDb else 0f
        val sharedBassBoost = if (enabled) bassBoostLevel * 12f else 0f
        val sharedTrebleBoost = if (enabled) trebleBoostLevel * 12f else 0f

        GlobalEqualizerState.update(levels, enabled, sharedPreamp, sharedBassBoost, sharedTrebleBoost)
        for (controller in instanceRegistry) {
            controller.applySharedSnapshot(
                levels = levels,
                enabled = enabled,
                preset = preset,
                sharedPreampDb = preampDb,
                sharedBassBoost = bassBoostLevel,
                sharedTrebleBoost = trebleBoostLevel,
                sharedCustomPresets = customPresets
            )
        }
    }

    private fun persistState() {
        try {
            val customPresetsArr = JSONArray().apply {
                customPresets.forEach { preset ->
                    put(
                        JSONObject().apply {
                            put("name", preset.name)
                            put("bands", JSONArray().apply { preset.bandLevelsDb.forEach { put(it) } })
                            put("preamp", preset.preampDb.toDouble())
                            put("bassBoost", preset.bassBoostLevel.toDouble())
                            put("trebleBoost", preset.trebleBoostLevel.toDouble())
                        }
                    )
                }
            }

            val editor = context.getSharedPreferences("quick_eq", Context.MODE_PRIVATE).edit()
            editor.putInt("bass", quickBassDb)
                .putInt("mid", quickMidDb)
                .putInt("treble", quickTrebleDb)
                .putString("preset", selectedPreset)
                .putBoolean("enabled", isEnabled)
                .putFloat("preamp", preampDb)
                .putFloat("bassBoost", bassBoostLevel)
                .putFloat("trebleBoost", trebleBoostLevel)
                .putString("custom_presets_json", customPresetsArr.toString())
            for (i in bands.indices) {
                editor.putInt("band_$i", bands[i].currentLevelDb)
                editor.putInt("custom_band_$i", customBands[i])
            }
            editor.putFloat("custom_preamp", customPreampDb)
            editor.putFloat("custom_bass_boost", customBassBoostLevel)
            editor.putFloat("custom_treble_boost", customTrebleBoostLevel)
            editor.commit()
        } catch (e: Exception) { android.util.Log.w("EqualizerController", "Caught throwable", e) }
    }

    private fun loadState() {
        try {
            val prefs = context.getSharedPreferences("quick_eq", Context.MODE_PRIVATE)
            val loadedCustomPresets = mutableListOf<CustomEqPreset>()
            val customJson = prefs.getString("custom_presets_json", null)
            if (!customJson.isNullOrBlank()) {
                runCatching {
                    val arr = JSONArray(customJson)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val name = obj.optString("name").trim()
                        if (name.isBlank() || isBuiltInPreset(name)) continue
                        val bandArr = obj.optJSONArray("bands")
                        val levels = List(10) { idx ->
                            (bandArr?.optInt(idx, 0) ?: 0).coerceIn(-12, 12)
                        }
                        loadedCustomPresets.add(
                            CustomEqPreset(
                                name = name,
                                bandLevelsDb = levels,
                                preampDb = obj.optDouble("preamp", 0.0).toFloat().coerceIn(0f, 12f),
                                bassBoostLevel = obj.optDouble("bassBoost", 0.0).toFloat().coerceIn(0f, 1f),
                                trebleBoostLevel = obj.optDouble("trebleBoost", 0.0).toFloat().coerceIn(0f, 1f)
                            )
                        )
                    }
                }
            }
            customPresets = loadedCustomPresets

            for (i in bands.indices) {
                val fallback = prefs.getInt(when(i) {
                    0 -> "bass"
                    4 -> "mid"
                    9 -> "treble"
                    else -> "unknown_default"
                }, 0)
                customBands[i] = prefs.getInt("custom_band_$i", prefs.getInt("band_$i", fallback)).coerceIn(-12, 12)
                bands[i].currentLevelDb = prefs.getInt("band_$i", customBands[i]).coerceIn(-12, 12)
            }
            customPreampDb = prefs.getFloat("custom_preamp", prefs.getFloat("preamp", 0f)).coerceIn(0f, 12f)
            customBassBoostLevel = prefs.getFloat("custom_bass_boost", prefs.getFloat("bassBoost", 0f)).coerceIn(0f, 1f)
            customTrebleBoostLevel = prefs.getFloat("custom_treble_boost", prefs.getFloat("trebleBoost", 0f)).coerceIn(0f, 1f)

            val savedPreset = prefs.getString("preset", "Custom") ?: "Custom"
            selectedPreset = when {
                savedPreset in builtInPresets -> savedPreset
                loadedCustomPresets.any { it.name == savedPreset } -> savedPreset
                else -> "Custom"
            }
            editingCustomPresetName = loadedCustomPresets.firstOrNull { it.name == selectedPreset }?.name
            // Fresh install defaults Equalizer to ON (true); persisted disabled state stays OFF.
            isEnabled = prefs.getBoolean("enabled", true)
            preampDb = prefs.getFloat("preamp", if (selectedPreset == "Custom") customPreampDb else 0f).coerceIn(0f, 12f)
            bassBoostLevel = prefs.getFloat("bassBoost", if (selectedPreset == "Custom") customBassBoostLevel else 0f).coerceIn(0f, 1f)
            trebleBoostLevel = prefs.getFloat("trebleBoost", if (selectedPreset == "Custom") customTrebleBoostLevel else 0f).coerceIn(0f, 1f)

            GlobalEqualizerState.update(
                bands.map { it.currentLevelDb.toFloat() }.toFloatArray(),
                isEnabled,
                if (isEnabled) preampDb else 0f,
                if (isEnabled) bassBoostLevel * 12f else 0f,
                if (isEnabled) trebleBoostLevel * 12f else 0f
            )
            syncQuickFromBands()
            onUpdate()
        } catch (e: Exception) { android.util.Log.w("EqualizerController", "Caught throwable", e) }
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

        @Volatile
        private var activeInstance: EqualizerController? = null

        @JvmStatic
        fun obtain(context: Context): EqualizerController {
            return activeInstance ?: synchronized(this) {
                activeInstance ?: EqualizerController(context.applicationContext).also { activeInstance = it }
            }
        }

        fun resetInstanceForTests() {
            activeInstance = null
            companionObjectRegistry.clear()
        }

        fun adjustQuickBand(context: Context, band: Int) {
            val controller = obtain(context)
            when (band) {
                0 -> controller.setQuickBass((controller.quickBassDb + 1).coerceAtMost(12))
                1 -> controller.setQuickMid((controller.quickMidDb + 1).coerceAtMost(12))
                2 -> controller.setQuickTreble((controller.quickTrebleDb + 1).coerceAtMost(12))
            }
        }
    }
}
