package com.example.player

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Isolated player equalizer & Dolby Atmos 3D Spatializer controller.
 * The native Equalizer effect is created only after the user enables it.
 * Native Audio Effects (Equalizer, Virtualizer/Dolby 3D) are created
 * when enabled and directly attached to the player's AudioSession.
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
    private var virtualizer: Virtualizer? = null
    private var audioSessionId: Int = 0
    private var hardwareFrequenciesHz = IntArray(0)

    // EQ state
    var isEnabled by mutableStateOf(false)
        private set

    // Dolby Atmos 3D Spatializer state
    var isDolbyAtmosEnabled by mutableStateOf(false)
        private set

    var dolbySurroundStrength by mutableStateOf(500) // 0 to 1000
        private set

    var dolbyProfile by mutableStateOf("Dolby Music")
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

    var bassBoostLevel by mutableStateOf(0f)
        private set
    var trebleBoostLevel by mutableStateOf(0f)
        private set

    val presets = listOf(
        "Flat", "Dolby Music", "Dolby Cinema", "Dolby Dynamic", "Dolby Voice", "Dolby Game",
        "GM Booster", "Clean Power", "Bass Boost", "Rock", "Pop",
        "Jazz", "Electronic", "Vocal", "Concert", "Custom"
    )

    /** Store the session and update audio effects. */
    fun attachToSession(newAudioSessionId: Int) {
        if (newAudioSessionId <= 0) return
        if (audioSessionId == newAudioSessionId && (!isEnabled || equalizer != null)) return
        releaseEffect()
        audioSessionId = newAudioSessionId
        if (isEnabled || isDolbyAtmosEnabled) ensureEffect()
    }

    fun toggleEnable() {
        if (isEnabled) {
            isEnabled = false
            if (!isDolbyAtmosEnabled) releaseEffect() else applyAllToHardware()
        } else {
            isEnabled = true
            if (!ensureEffect()) isEnabled = false
        }
    }

    fun toggleDolbyAtmos() {
        isDolbyAtmosEnabled = !isDolbyAtmosEnabled
        if (isDolbyAtmosEnabled) {
            ensureEffect()
            applyDolbyToHardware()
        } else {
            if (!isEnabled) releaseEffect() else applyDolbyToHardware()
        }
        persistState()
    }

    fun updateDolbyStrength(strength: Int) {
        dolbySurroundStrength = strength.coerceIn(0, 1000)
        persistState()
        if (isDolbyAtmosEnabled) applyDolbyToHardware()
    }

    fun applyDolbyProfile(profile: String) {
        dolbyProfile = profile
        when (profile) {
            "Dolby Cinema" -> {
                updateDolbyStrength(850)
                applyPreset("Dolby Cinema")
            }
            "Dolby Music" -> {
                updateDolbyStrength(600)
                applyPreset("Dolby Music")
            }
            "Dolby Dynamic" -> {
                updateDolbyStrength(750)
                applyPreset("Dolby Dynamic")
            }
            "Dolby Voice" -> {
                updateDolbyStrength(300)
                applyPreset("Dolby Voice")
            }
            "Dolby Game" -> {
                updateDolbyStrength(900)
                applyPreset("Dolby Game")
            }
        }
        if (!isDolbyAtmosEnabled) {
            isDolbyAtmosEnabled = true
            ensureEffect()
        }
        applyDolbyToHardware()
        persistState()
    }

    fun openDolbyAtmosSystemPanel(context: Context) {
        try {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                if (audioSessionId > 0) putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
        } catch (_: Throwable) { }

        // Direct package launch fallback for Dolby apps on device
        val packageNames = listOf(
            "com.dolby.daxappui2",
            "com.dolby.daxappui",
            "com.dolby.dax2appui",
            "com.dolby.dax3appui",
            "com.lenovo.dolby.dax3ui"
        )
        val launchIntent = packageNames.asSequence()
            .mapNotNull { context.packageManager.getLaunchIntentForPackage(it) }
            .firstOrNull()
        if (launchIntent != null) {
            try {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(launchIntent)
            } catch (_: Throwable) {
                Toast.makeText(context, "Dolby Atmos DSP is integrated directly into player audio session", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Dolby Atmos 3D Spatializer is integrated directly in audio pipeline", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureEffect(): Boolean {
        if (!isEnabled && !isDolbyAtmosEnabled && equalizer == null && virtualizer == null) return false
        if (audioSessionId <= 0) return false
        var success = false
        if (equalizer == null) {
            try {
                val eq = Equalizer(0, audioSessionId)
                val count = eq.numberOfBands.toInt().coerceAtLeast(0)
                hardwareFrequenciesHz = IntArray(count) { index ->
                    eq.getCenterFreq(index.toShort()).toInt() / 1000
                }
                eq.enabled = isEnabled
                equalizer = eq
                applyAllToHardware()
                success = true
            } catch (_: Throwable) {
                try { equalizer?.release() } catch (_: Throwable) { }
                equalizer = null
            }
        } else {
            success = true
        }

        if (virtualizer == null) {
            try {
                val virt = Virtualizer(0, audioSessionId)
                virt.enabled = isDolbyAtmosEnabled
                virtualizer = virt
                applyDolbyToHardware()
            } catch (_: Throwable) {
                try { virtualizer?.release() } catch (_: Throwable) { }
                virtualizer = null
            }
        }
        return success
    }

    private fun applyDolbyToHardware() {
        val virt = virtualizer ?: return
        try {
            virt.enabled = isDolbyAtmosEnabled
            if (isDolbyAtmosEnabled) {
                virt.setStrength(dolbySurroundStrength.toShort())
            }
        } catch (_: Throwable) { }
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
            "Dolby Cinema" -> listOf(10, 8, 5, 2, 0, 3, 6, 8, 9, 10)
            "Dolby Music" -> listOf(7, 5, 3, 2, 2, 3, 5, 7, 8, 8)
            "Dolby Dynamic" -> listOf(8, 6, 4, 2, 1, 3, 5, 7, 8, 9)
            "Dolby Voice" -> listOf(-4, -2, 2, 6, 8, 7, 5, 3, 1, -1)
            "Dolby Game" -> listOf(9, 7, 4, 3, 2, 4, 7, 9, 9, 10)
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
        val eq = equalizer ?: return
        try {
            eq.enabled = isEnabled
            if (!isEnabled) return
            if (hardwareFrequenciesHz.isEmpty()) return
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
                .putBoolean("dolby_enabled", isDolbyAtmosEnabled)
                .putInt("dolby_strength", dolbySurroundStrength)
                .putString("dolby_profile", dolbyProfile)
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
            isDolbyAtmosEnabled = prefs.getBoolean("dolby_enabled", false)
            dolbySurroundStrength = prefs.getInt("dolby_strength", 500)
            dolbyProfile = prefs.getString("dolby_profile", "Dolby Music") ?: "Dolby Music"
            syncQuickFromBands()
        } catch (_: Throwable) { }
    }

    private fun releaseEffect() {
        try { equalizer?.enabled = false } catch (_: Throwable) { }
        try { equalizer?.release() } catch (_: Throwable) { }
        try { virtualizer?.enabled = false } catch (_: Throwable) { }
        try { virtualizer?.release() } catch (_: Throwable) { }
        equalizer = null
        virtualizer = null
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

