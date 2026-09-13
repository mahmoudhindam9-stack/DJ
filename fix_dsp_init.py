import re

with open('app/src/main/java/com/example/fx/DspPluginManager.kt', 'r') as f:
    content = f.read()

replacement = r"""
    private fun ensureBuffer() {
        val required = (sampleRate * 2).coerceAtLeast(44100)
        if (buffer.size < required * 2) {
            maxFrames = required
            val newBuf = FloatArray(maxFrames * 2)
            System.arraycopy(buffer, 0, newBuf, 0, minOf(buffer.size, newBuf.size))
            buffer = newBuf
        }
    }
    init {
        ensureBuffer()
    }
"""

content = content.replace("""
    private fun ensureBuffer() {
        val required = (sampleRate * 2).coerceAtLeast(44100)
        if (buffer.size < required * 2) {
            maxFrames = required
            val newBuf = FloatArray(maxFrames * 2)
            System.arraycopy(buffer, 0, newBuf, 0, minOf(buffer.size, newBuf.size))
            buffer = newBuf
        }
    }""", replacement)

with open('app/src/main/java/com/example/fx/DspPluginManager.kt', 'w') as f:
    f.write(content)
