import re
with open('app/src/main/java/com/example/player/DeckFxAudioProcessor.kt', 'r') as f:
    content = f.read()

# Replace variables
content = content.replace("    @Volatile private var pluginChain = emptyList<AudioPlugin>()", 
"""    @Volatile private var pluginChain = emptyList<AudioPlugin>()
    private val pluginCache = mutableMapOf<String, AudioPlugin>()""")

# Replace initContext
content = re.sub(
    r'    fun initContext.*?\}',
    """    fun initContext(context: android.content.Context) {
        pluginManager = DspPluginManager(context)
    }""",
    content,
    flags=re.DOTALL
)

# Replace refreshPlugins
content = re.sub(
    r'    fun refreshPlugins.*?\}',
    """    fun refreshPlugins() {
        // Clear cache for custom plugins so they can be rebuilt if they changed
        val keysToRemove = pluginCache.keys.filter { it.startsWith("custom_") }
        keysToRemove.forEach { pluginCache.remove(it) }
        
        // Re-evaluate the active effects to rebuild the chain
        val currentEffects = activeEffects
        activeEffects = emptySet()
        updateActiveEffects(currentEffects)
    }
    
    fun updateActiveEffects(newActiveEffects: Set<String>) {
        if (activeEffects == newActiveEffects) return
        
        val newChain = mutableListOf<AudioPlugin>()
        for (effectId in newActiveEffects) {
            if (effectId.startsWith("voice_")) continue
            var plugin = pluginCache[effectId]
            if (plugin == null) {
                plugin = pluginManager?.createPlugin(effectId)
                if (plugin != null) {
                    plugin.sampleRate = sampleRate
                    pluginCache[effectId] = plugin
                }
            }
            if (plugin != null) {
                newChain.add(plugin)
            }
        }
        
        activeEffects = newActiveEffects
        pluginChain = newChain
    }""",
    content,
    flags=re.DOTALL
)

# Update queueInput plugin chain processing
queueInput_old = """                // Apply Modular FX Engine
                for (plugin in pluginChain) {
                    val targetEnabled = activeEffects.contains(plugin.id)
                    if (targetEnabled && fxAmount > 0.01f) {
                        plugin.enabled = true
                        plugin.amount = fxAmount
                        plugin.sampleRate = sampleRate
                        sample = plugin.process(sample, ch)
                    } else {
                        if (plugin.enabled) {
                            plugin.enabled = false
                            plugin.reset()
                        }
                    }
                }"""
queueInput_new = """                // Apply Modular FX Engine
                for (plugin in pluginChain) {
                    if (fxAmount > 0.01f) {
                        if (!plugin.enabled) plugin.enabled = true
                        plugin.amount = fxAmount
                        if (plugin.sampleRate != sampleRate) plugin.sampleRate = sampleRate
                        sample = plugin.process(sample, ch)
                    } else {
                        if (plugin.enabled) {
                            plugin.enabled = false
                            plugin.reset()
                        }
                    }
                }"""
content = content.replace(queueInput_old, queueInput_new)

with open('app/src/main/java/com/example/player/DeckFxAudioProcessor.kt', 'w') as f:
    f.write(content)
