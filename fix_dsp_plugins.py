import re

with open('app/src/main/java/com/example/fx/DspPluginManager.kt', 'r') as f:
    content = f.read()

# Fix DelayPlugin
delay_old = '''    private val maxFrames = 96000
    private val buffer = FloatArray(maxFrames * 2) // We always allocate for 2 channels to be safe
    private var writePos = 0
    override fun process(sample: Float, channel: Int): Float {
        val delayLength = (sampleRate * 0.5).toInt().coerceIn(1, maxFrames)
        val readPos = (writePos - delayLength * 2 + buffer.size) % buffer.size
        val delayed = buffer[readPos / 2 * 2 + channel]
        val wet = sample + delayed * 0.5f
        val writeIdx = writePos / 2 * 2 + channel
        buffer[writeIdx] = sample + delayed * 0.3f
        if (channel == channelCount - 1) writePos = (writePos + channelCount) % buffer.size
        return sample * (1f - amount) + wet * amount
    }'''

delay_new = '''    private val maxFrames = 96000
    private val buffer = FloatArray(maxFrames * 2) // Always allocate for 2 channels (stereo max)
    private var writeFrame = 0
    override fun process(sample: Float, channel: Int): Float {
        val delayLength = (sampleRate * 0.5).toInt().coerceIn(1, maxFrames - 1)
        val readFrame = (writeFrame - delayLength + maxFrames) % maxFrames
        val delayed = buffer[readFrame * 2 + channel]
        val wet = sample + delayed * 0.5f
        buffer[writeFrame * 2 + channel] = sample + delayed * 0.3f
        
        if (channel == channelCount - 1) {
            writeFrame = (writeFrame + 1) % maxFrames
        }
        return sample * (1f - amount) + wet * amount
    }'''

content = content.replace(delay_old, delay_new)

# Fix ReverbPlugin
reverb_old = '''    private val maxFrames = 96000
    private val buffer = FloatArray(maxFrames * 2) // We always allocate for 2 channels to be safe
    private var writePos = 0
    override fun process(sample: Float, channel: Int): Float {
        val delayLength = (sampleRate * 0.2).toInt().coerceIn(1, maxFrames)
        val readPos = (writePos - delayLength * 2 + buffer.size) % buffer.size
        val delayed = buffer[readPos / 2 * 2 + channel]
        val wet = sample + delayed * 0.4f
        val writeIdx = writePos / 2 * 2 + channel
        buffer[writeIdx] = sample + delayed * 0.6f 
        if (channel == channelCount - 1) writePos = (writePos + channelCount) % buffer.size
        return sample * (1f - amount) + wet * amount
    }'''

reverb_new = '''    private val maxFrames = 96000
    private val buffer = FloatArray(maxFrames * 2)
    private var writeFrame = 0
    override fun process(sample: Float, channel: Int): Float {
        val delayLength = (sampleRate * 0.2).toInt().coerceIn(1, maxFrames - 1)
        val readFrame = (writeFrame - delayLength + maxFrames) % maxFrames
        val delayed = buffer[readFrame * 2 + channel]
        val wet = sample + delayed * 0.4f
        buffer[writeFrame * 2 + channel] = sample + delayed * 0.6f 
        
        if (channel == channelCount - 1) {
            writeFrame = (writeFrame + 1) % maxFrames
        }
        return sample * (1f - amount) + wet * amount
    }'''

content = content.replace(reverb_old, reverb_new)
content = content.replace('writePos = 0', 'writeFrame = 0') # Make sure resets are updated too


# Fix FlangerPlugin
flanger_old = '''    private var buffer = FloatArray(96000 * 2) // We always allocate for 2 channels to be safe
    private var writePos = 0
    private var lfoPhase = 0.0
    override fun process(sample: Float, channel: Int): Float {
        val maxDelay = (sampleRate * 0.01).toInt() // 10ms max delay
        if (buffer.size != maxDelay * 2) {
            buffer = FloatArray(maxDelay * 2)
            writePos = 0
        }
        if (channel == 0) {
            lfoPhase += 0.0001 * (44100.0 / sampleRate)
            if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI
        }
        val lfo = (sin(lfoPhase) + 1.0) / 2.0
        val currentDelay = (maxDelay * lfo).toInt().coerceIn(1, maxDelay - 1)
        val readPos = (writePos - currentDelay * 2 + buffer.size) % buffer.size
        val delayed = buffer[readPos / 2 * 2 + channel]
        val wet = sample + delayed * 0.7f
        val writeIdx = writePos / 2 * 2 + channel
        buffer[writeIdx] = sample + delayed * 0.7f
        if (channel == channelCount - 1) writePos = (writePos + channelCount) % buffer.size
        return sample * (1f - amount) + wet * amount
    }'''

flanger_new = '''    private val maxFrames = 96000
    private val buffer = FloatArray(maxFrames * 2)
    private var writeFrame = 0
    private var lfoPhase = 0.0
    override fun process(sample: Float, channel: Int): Float {
        val maxDelay = (sampleRate * 0.01).toInt().coerceIn(1, maxFrames - 1) // 10ms max delay
        
        if (channel == 0) {
            lfoPhase += 0.0001 * (44100.0 / sampleRate)
            if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI
        }
        val lfo = (sin(lfoPhase) + 1.0) / 2.0
        val currentDelay = (maxDelay * lfo).toInt().coerceIn(1, maxDelay - 1)
        val readFrame = (writeFrame - currentDelay + maxFrames) % maxFrames
        val delayed = buffer[readFrame * 2 + channel]
        val wet = sample + delayed * 0.7f
        buffer[writeFrame * 2 + channel] = sample + delayed * 0.7f
        
        if (channel == channelCount - 1) {
            writeFrame = (writeFrame + 1) % maxFrames
        }
        return sample * (1f - amount) + wet * amount
    }'''

content = content.replace(flanger_old, flanger_new)

with open('app/src/main/java/com/example/fx/DspPluginManager.kt', 'w') as f:
    f.write(content)

print("Updated DspPluginManager.kt")
