from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EQ = ROOT / "app/src/main/java/com/example/player/EqualizerController.kt"
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
MARK = "// SAFE_EQ_DOLBY_V3"

EQ_CONTENT = r'''package com.example.player

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

class EqualizerController {
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

    fun release() {
        try { equalizer?.release() } catch (_: Throwable) { }
        equalizer = null
        hardwareFrequenciesHz = IntArray(0)
    }
}
'''


def patch_eq():
    EQ.write_text(EQ_CONTENT, encoding="utf-8")


def patch_main():
    text = MAIN.read_text(encoding="utf-8")
    if MARK in text:
        return
    needle = '@Composable\nfun EqualizerScreen(eqController: EqualizerController) {\n    Column('
    if needle not in text:
        raise SystemExit("EqualizerScreen header not found")
    replacement = '''@Composable\nfun EqualizerScreen(eqController: EqualizerController) {\n    val context = LocalContext.current\n    Column('''
    text = text.replace(needle, replacement, 1)
    anchor = '''        Spacer(modifier = Modifier.height(16.dp))\n\n        Text("EQ Presets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)'''
    insert = '''        Spacer(modifier = Modifier.height(16.dp))\n\n        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {\n            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {\n                Column(Modifier.weight(1f)) {\n                    Text("Dolby Atmos", fontWeight = FontWeight.Bold)\n                    Text("Use the phone's hardware/vendor audio processing without stacking aggressive EQ.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n                }\n                Button(onClick = {\n                    val packages = listOf("com.dolby.daxappui2", "com.dolby.daxappui")\n                    val intent = packages.asSequence().mapNotNull { pkg -> context.packageManager.getLaunchIntentForPackage(pkg) }.firstOrNull()\n                    if (intent != null) context.startActivity(intent)\n                    else Toast.makeText(context, "Dolby Atmos is not available on this device", Toast.LENGTH_SHORT).show()\n                }) { Text("Open Dolby") }\n            }\n        }\n\n        Spacer(modifier = Modifier.height(16.dp))\n\n        Text("EQ Presets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)'''
    if anchor not in text:
        raise SystemExit("EQ presets anchor not found")
    text = text.replace(anchor, insert, 1)
    text = text.replace('Text("10-BAND EQ (dB GAIN)",', 'Text("10-BAND EQ (SAFE ±6 dB)",', 1)
    text = text.replace('coerceIn(-12f, 12f)', 'coerceIn(-6f, 6f)', 1)
    text = text.replace('(-12f + fraction * 24f)', '(-6f + fraction * 12f)', 1)
    text = text.replace('((latestValue + 12f) / 24f)', '((latestValue + 6f) / 12f)', 1)
    text = text.replace('val delta = -dragAmount / height * 24f', 'val delta = -dragAmount / height * 12f', 1)
    text = text.replace(MARK, MARK, 1)
    text = text.replace('fun EqualizerScreen(eqController: EqualizerController) {', 'fun EqualizerScreen(eqController: EqualizerController) {\n    ' + MARK, 1)
    MAIN.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    patch_eq()
    patch_main()
    print("Equalizer and Dolby integration upgrade applied")
