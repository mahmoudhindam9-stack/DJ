import re

with open('app/src/main/java/com/example/fx/DspPluginManager.kt', 'r') as f:
    content = f.read()

def patch_plugin(name, pattern, content):
    replacement = r"""    override var sampleRate = 44100
        set(value) {
            field = value
            ensureBuffer()
        }
    override var channelCount = 2
    private var maxFrames = 0
    private var buffer = FloatArray(0)
    
    private fun ensureBuffer() {
        val required = (sampleRate * 2).coerceAtLeast(44100)
        if (buffer.size < required * 2) {
            maxFrames = required
            val newBuf = FloatArray(maxFrames * 2)
            System.arraycopy(buffer, 0, newBuf, 0, minOf(buffer.size, newBuf.size))
            buffer = newBuf
        }
    }"""
    # Replace the sampleRate and maxFrames variables
    return re.sub(pattern, replacement, content)

# FlangerPlugin
content = patch_plugin("FlangerPlugin",
r"""    override var sampleRate = 44100
    override var channelCount = 2
    private val maxFrames = 96000
    private val buffer = FloatArray\(maxFrames \* 2\)""", content)

# CustomFlangerPlugin
content = patch_plugin("CustomFlangerPlugin",
r"""    override var sampleRate = 44100
    override var channelCount = 2
    private val maxFrames = 96000
    private val buffer = FloatArray\(maxFrames \* 2\)""", content)

# CustomDelayPlugin
content = patch_plugin("CustomDelayPlugin",
r"""    override var sampleRate = 44100
    override var channelCount = 2
    private val maxFrames = 96000
    private val buffer = FloatArray\(maxFrames \* 2\)""", content)

# CustomReverbPlugin
content = patch_plugin("CustomReverbPlugin",
r"""    override var sampleRate = 44100
    override var channelCount = 2
    private val maxFrames = 96000
    private val buffer = FloatArray\(maxFrames \* 2\)""", content)

# DelayPlugin
content = patch_plugin("DelayPlugin",
r"""    override var sampleRate = 44100
    override var channelCount = 2
    private val maxFrames = 96000
    private val buffer = FloatArray\(maxFrames \* 2\)( // Always allocate for 2 channels \(stereo max\))?""", content)

# ReverbPlugin
content = patch_plugin("ReverbPlugin",
r"""    override var sampleRate = 44100
    override var channelCount = 2
    private val maxFrames = 96000
    private val buffer = FloatArray\(maxFrames \* 2\)""", content)

with open('app/src/main/java/com/example/fx/DspPluginManager.kt', 'w') as f:
    f.write(content)
