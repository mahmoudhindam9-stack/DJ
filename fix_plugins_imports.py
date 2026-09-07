import re

with open('app/src/main/java/com/example/fx/DspPluginManager.kt', 'r') as f:
    text = f.read()

# Add Context import if missing
if 'import android.content.Context' not in text:
    text = text.replace('import kotlin.math.*', 'import android.content.Context\nimport org.json.JSONArray\nimport org.json.JSONObject\nimport kotlin.math.*')

text = text.replace('class DspPluginManager {', 'class DspPluginManager(private val context: Context) {')
if 'fun loadCustomPlugins()' not in text:
    custom_logic = """
    private fun loadCustomPlugins(): List<AudioPlugin> {
        val plugins = mutableListOf<AudioPlugin>()
        val prefs = context.getSharedPreferences("modular_fx", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("plugins", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val type = obj.getString("type")
                val param1 = obj.optDouble("param1", 0.5).toFloat()
                val param2 = obj.optDouble("param2", 0.5).toFloat()
                
                when (type) {
                    "delay" -> plugins.add(CustomDelayPlugin(id, name, param1))
                    "distortion" -> plugins.add(CustomDistortionPlugin(id, name, param1))
                    "filter" -> plugins.add(CustomFilterPlugin(id, name, param1))
                    else -> plugins.add(GenericCustomPlugin(id, name, param1, param2))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return plugins
    }
"""
    text = text.replace('fun getAvailablePlugins(): List<AudioPlugin> {', custom_logic + '\n    fun getAvailablePlugins(): List<AudioPlugin> {')
    text = text.replace('return listOf(', 'val defaultPlugins = listOf(')
    text = text.replace('CompressorPlugin()\n        )', 'CompressorPlugin()\n        )\n        return loadCustomPlugins() // Only custom plugins as requested')

custom_classes = """
class CustomDelayPlugin(override val id: String, override val name: String, private val lengthParam: Float) : AudioPlugin {
    override var enabled = false
    override var amount = 0.5f
    private val delayLength = (88200 * lengthParam).toInt().coerceAtLeast(1)
    private val buffer = FloatArray(delayLength * 2)
    private var writePos = 0
    override fun process(sample: Float, channel: Int): Float {
        val readPos = (writePos - delayLength + buffer.size) % buffer.size
        val delayed = buffer[readPos / 2 * 2 + channel]
        val wet = sample + delayed * 0.5f
        val writeIdx = writePos / 2 * 2 + channel
        buffer[writeIdx] = sample + delayed * 0.3f
        if (channel == 1) writePos = (writePos + 2) % buffer.size
        return sample * (1f - amount) + wet * amount
    }
    override fun reset() {
        buffer.fill(0f)
        writePos = 0
    }
}

class CustomDistortionPlugin(override val id: String, override val name: String, private val driveParam: Float) : AudioPlugin {
    override var enabled = false
    override var amount = 0.5f
    override fun process(sample: Float, channel: Int): Float {
        val drive = 1f + (amount * driveParam) * 15f
        val wet = (sample * drive).coerceIn(-1f, 1f)
        val out = if (wet > 0) 1f - kotlin.math.exp(-wet) else -1f + kotlin.math.exp(wet)
        return sample * (1f - amount) + out * amount
    }
    override fun reset() {}
}

class CustomFilterPlugin(override val id: String, override val name: String, private val cutoffParam: Float) : AudioPlugin {
    override var enabled = false
    override var amount = 0.5f
    private val lpState = FloatArray(2)
    override fun process(sample: Float, channel: Int): Float {
        val cutoff = 0.05f + 0.95f * (amount * cutoffParam)
        lpState[channel] += cutoff * (sample - lpState[channel])
        val wet = lpState[channel]
        return sample * (1f - amount) + wet * amount
    }
    override fun reset() {
        lpState.fill(0f)
    }
}

class GenericCustomPlugin(override val id: String, override val name: String, val param1: Float, val param2: Float) : AudioPlugin {
    override var enabled = false
    override var amount = 0.5f
    override fun process(sample: Float, channel: Int): Float {
        val wet = sample * param1
        return sample * (1f - amount) + wet * amount
    }
    override fun reset() {}
}
"""

if 'CustomDelayPlugin' not in text:
    text += custom_classes

with open('app/src/main/java/com/example/fx/DspPluginManager.kt', 'w') as f:
    f.write(text)

