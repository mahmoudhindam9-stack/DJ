package com.example.player.fx

object DspPluginManager {
    private val plugins = mutableMapOf<String, AudioPlugin>()
    
    init {
        // Register default plugins
        register(FilterPlugin())
        register(DelayPlugin())
        register(ReverbPlugin())
        register(FlangerPlugin())
        register(PhaserPlugin())
        register(BitCrusherPlugin())
        register(DistortionPlugin())
        register(CompressorPlugin())
    }
    
    fun register(plugin: AudioPlugin) {
        plugins[plugin.id] = plugin
    }
    
    fun getPlugin(id: String): AudioPlugin? = plugins[id]
    
    fun getAllPlugins(): List<AudioPlugin> = plugins.values.toList()
    
    fun createChain(): List<AudioPlugin> {
        // Creates a new instance chain for a deck
        return listOf(
            FilterPlugin(),
            DelayPlugin(),
            ReverbPlugin(),
            FlangerPlugin(),
            PhaserPlugin(),
            BitCrusherPlugin(),
            DistortionPlugin(),
            CompressorPlugin()
        )
    }
}
