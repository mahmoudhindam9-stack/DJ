package com.example.fx

interface AudioPlugin {
    val id: String
    val name: String
    var enabled: Boolean
    var amount: Float // 0.0 to 1.0 (dry/wet)
    var sampleRate: Int
    var channelCount: Int
    
    fun process(sample: Float, channel: Int): Float
    fun reset()
}
