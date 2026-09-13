import re
with open('app/src/main/java/com/example/fx/DspPluginManager.kt', 'r') as f:
    content = f.read()

# Replace getAvailablePlugins
replacement_create = """
    fun createPlugin(id: String): AudioPlugin? {
        if (android.support.v4.BuildConfig.DEBUG) {
            android.util.Log.d("DspPluginManager", "Creating plugin instance for: $id")
        }
        when (id) {
            "fx_filter" -> return FilterPlugin()
            "fx_delay" -> return DelayPlugin()
            "fx_reverb" -> return ReverbPlugin()
            "fx_flanger" -> return FlangerPlugin()
            "fx_phaser" -> return PhaserPlugin()
            "fx_bitcrush" -> return BitcrusherPlugin()
            "fx_distortion" -> return DistortionPlugin()
            "fx_compressor" -> return CompressorPlugin()
        }
        val preset = getCustomPresets().find { it.id == id }
        if (preset != null) {
            return buildEngine(preset.engineType, preset.id, preset.name, preset.param1, preset.param2)
        }
        return null
    }
"""

content = re.sub(
    r'    fun getAvailablePlugins\(\): List<AudioPlugin> \{[\s\S]*?return builtIns \+ loadCustomPlugins\(\)\n    \}',
    replacement_create,
    content
)

content = re.sub(
    r'    private fun loadCustomPlugins\(\): List<AudioPlugin> =[\s\S]*?        \}',
    '',
    content
)

# Replace the BuildConfig reference if it's incorrect. I should just use android.util.Log.
replacement_create = replacement_create.replace('android.support.v4.BuildConfig.DEBUG', 'com.example.BuildConfig.DEBUG')
content = re.sub(r'if \(android.support.v4.BuildConfig.DEBUG\) \{', 'if (com.example.BuildConfig.DEBUG) {', content)


with open('app/src/main/java/com/example/fx/DspPluginManager.kt', 'w') as f:
    f.write(content)
