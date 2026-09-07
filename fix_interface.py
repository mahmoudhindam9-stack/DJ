import re

with open('app/src/main/java/com/example/fx/DspPluginManager.kt', 'r') as f:
    text = f.read()

interface_code = """
interface AudioPlugin {
    val id: String
    val name: String
    var enabled: Boolean
    var amount: Float
    fun process(sample: Float, channel: Int): Float
    fun reset()
}
"""

if 'interface AudioPlugin' not in text:
    text = text.replace('import org.json.JSONObject\nimport kotlin.math.*', 'import org.json.JSONObject\nimport kotlin.math.*\n\n' + interface_code)

with open('app/src/main/java/com/example/fx/DspPluginManager.kt', 'w') as f:
    f.write(text)
