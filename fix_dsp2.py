import re

with open('app/src/main/java/com/example/fx/DspPluginManager.kt', 'r') as f:
    content = f.read()

def replacer(match):
    prefix = match.group(1)
    return prefix + '''
        val readFrame = (writeFrame - delayLength + maxFrames) % maxFrames
        val delayed = buffer[readFrame * 2 + channel]
        val wet = sample + delayed * 0.5f
        buffer[writeFrame * 2 + channel] = sample + delayed * 0.3f
        if (channel == channelCount - 1) {
            writeFrame = (writeFrame + 1) % maxFrames
        }'''

# FlangerPlugin
flanger_new = '''    private val maxFrames = 96000
    private val buffer = FloatArray(maxFrames * 2)
    private var writeFrame = 0
    private var lfoPhase = 0.0
    override fun process(sample: Float, channel: Int): Float {
        val maxDelay = (sampleRate * 0.01).toInt().coerceIn(1, maxFrames - 1)
        if (channel == 0) {
            lfoPhase += 0.0001 * (44100.0 / sampleRate)
            if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI
        }
        val lfo = (sin(lfoPhase) + 1.0) / 2.0
        val currentDelay = (maxDelay * lfo).toInt().coerceIn(1, maxDelay - 1)
        val readFrame = (writeFrame - currentDelay + maxFrames) % maxFrames
        val delayed = buffer[readFrame * 2 + channel]
        val wet = sample + delayed * 0.7f
        buffer[writeFrame * 2 + channel] = sample + delayed * 0.5f
        if (channel == channelCount - 1) writeFrame = (writeFrame + 1) % maxFrames
        return sample * (1f - amount) + wet * amount
    }'''

content = re.sub(r'    private var buffer = FloatArray\(96000 \* 2\).*?return sample \* \(1f - amount\) \+ wet \* amount\n    \}', flanger_new, content, flags=re.DOTALL)


# CustomDelayPlugin
cdelay_new = '''    private val maxFrames = 96000
    private val buffer = FloatArray(maxFrames * 2)
    private var writeFrame = 0
    override fun process(sample: Float, channel: Int): Float {
        val delayLength = (sampleRate * lengthParam).toInt().coerceIn(1, maxFrames - 1)
        val readFrame = (writeFrame - delayLength + maxFrames) % maxFrames
        val delayed = buffer[readFrame * 2 + channel]
        val wet = sample + delayed * 0.5f
        buffer[writeFrame * 2 + channel] = sample + delayed * 0.3f
        if (channel == channelCount - 1) writeFrame = (writeFrame + 1) % maxFrames
        return sample * (1f - amount) + wet * amount
    }'''

content = re.sub(r'    private val maxFrames = 96000\n    private val buffer = FloatArray\(maxFrames \* 2\) // We always allocate for 2 channels to be safe\n    private var writeFrame = 0\n    override fun process\(sample: Float, channel: Int\): Float \{\n        val delayLength = \(sampleRate \* lengthParam\)\.toInt\(\)\.coerceIn\(1, maxFrames\).*?return sample \* \(1f - amount\) \+ wet \* amount\n    \}', cdelay_new, content, flags=re.DOTALL)


# CustomReverbPlugin
crev_new = '''    private val maxFrames = 96000
    private val buffer = FloatArray(maxFrames * 2)
    private var writeFrame = 0
    override fun process(sample: Float, channel: Int): Float {
        val delayLength = (sampleRate * (0.04 + sizeParam * 0.4)).toInt().coerceIn(1, maxFrames - 1)
        val readFrame = (writeFrame - delayLength + maxFrames) % maxFrames
        val delayed = buffer[readFrame * 2 + channel]
        val wet = sample + delayed * 0.4f
        buffer[writeFrame * 2 + channel] = sample + delayed * 0.6f
        if (channel == channelCount - 1) writeFrame = (writeFrame + 1) % maxFrames
        return sample * (1f - amount) + wet * amount
    }'''

content = re.sub(r'    private val maxFrames = 96000\n    private val buffer = FloatArray\(maxFrames \* 2\) // We always allocate for 2 channels to be safe\n    private var writeFrame = 0\n    override fun process\(sample: Float, channel: Int\): Float \{\n        val delayLength = \(sampleRate \* \(0\.04 \+ sizeParam \* 0\.4\)\)\.toInt\(\)\.coerceIn\(1, maxFrames\).*?return sample \* \(1f - amount\) \+ wet \* amount\n    \}', crev_new, content, flags=re.DOTALL)


# CustomFlangerPlugin
cflanger_new = '''    private val maxFrames = 96000
    private val buffer = FloatArray(maxFrames * 2)
    private var writeFrame = 0
    private var lfoPhase = 0.0
    override fun process(sample: Float, channel: Int): Float {
        val maxDelay = (sampleRate * 0.01).toInt().coerceIn(1, maxFrames - 1)
        val lfoSpeed = 0.0001 + rateParam * 0.0004
        if (channel == 0) {
            lfoPhase += lfoSpeed * (44100.0 / sampleRate)
            if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI
        }
        val lfo = (sin(lfoPhase) + 1.0) / 2.0
        val currentDelay = (maxDelay * lfo).toInt().coerceIn(1, maxDelay - 1)
        val readFrame = (writeFrame - currentDelay + maxFrames) % maxFrames
        val delayed = buffer[readFrame * 2 + channel]
        val wet = sample + delayed * 0.7f
        buffer[writeFrame * 2 + channel] = sample + delayed * 0.5f
        if (channel == channelCount - 1) writeFrame = (writeFrame + 1) % maxFrames
        return sample * (1f - amount) + wet * amount
    }'''

content = re.sub(r'    private var buffer = FloatArray\(96000 \* 2\) // We always allocate for 2 channels to be safe\n    private var writePos = 0\n    private var lfoPhase = 0\.0\n    override fun process\(sample: Float, channel: Int\): Float \{.*?return sample \* \(1f - amount\) \+ wet \* amount\n    \}', cflanger_new, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/fx/DspPluginManager.kt', 'w') as f:
    f.write(content)

print("Updated DspPluginManager.kt again")
