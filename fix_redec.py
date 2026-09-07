import re

with open('app/src/main/java/com/example/fx/DspPluginManager.kt', 'r') as f:
    text = f.read()

interface_def = """interface AudioPlugin {
    val id: String
    val name: String
    var enabled: Boolean
    var amount: Float
    fun process(sample: Float, channel: Int): Float
    fun reset()
}"""

# Remove it from DspPluginManager.kt since it already exists in AudioPlugin.kt
text = text.replace(interface_def, '')

with open('app/src/main/java/com/example/fx/DspPluginManager.kt', 'w') as f:
    f.write(text)
