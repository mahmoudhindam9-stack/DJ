import re

content = open("app/src/main/java/com/example/player/EqualizerProcessor.kt").read()

new_methods = """
    fun setLowShelf(freq: Float, dbGain: Float, sampleRate: Float) {
        val safeSampleRate = sampleRate.coerceAtLeast(1f)
        val safeFreq = freq.coerceIn(1f, safeSampleRate * 0.45f)
        val A = 10.0f.pow(dbGain / 40.0f)
        val w0 = 2.0f * PI.toFloat() * safeFreq / safeSampleRate
        val alpha = sin(w0) / 2.0f * sqrt(2.0f)
        
        val a0 = (A + 1.0f) + (A - 1.0f) * cos(w0) + 2.0f * sqrt(A) * alpha
        b0 = (A * ((A + 1.0f) - (A - 1.0f) * cos(w0) + 2.0f * sqrt(A) * alpha)) / a0
        b1 = (2.0f * A * ((A - 1.0f) - (A + 1.0f) * cos(w0))) / a0
        b2 = (A * ((A + 1.0f) - (A - 1.0f) * cos(w0) - 2.0f * sqrt(A) * alpha)) / a0
        a1 = (-2.0f * ((A - 1.0f) + (A + 1.0f) * cos(w0))) / a0
        a2 = ((A + 1.0f) + (A - 1.0f) * cos(w0) - 2.0f * sqrt(A) * alpha) / a0
    }

    fun setHighShelf(freq: Float, dbGain: Float, sampleRate: Float) {
        val safeSampleRate = sampleRate.coerceAtLeast(1f)
        val safeFreq = freq.coerceIn(1f, safeSampleRate * 0.45f)
        val A = 10.0f.pow(dbGain / 40.0f)
        val w0 = 2.0f * PI.toFloat() * safeFreq / safeSampleRate
        val alpha = sin(w0) / 2.0f * sqrt(2.0f)
        
        val a0 = (A + 1.0f) - (A - 1.0f) * cos(w0) + 2.0f * sqrt(A) * alpha
        b0 = (A * ((A + 1.0f) + (A - 1.0f) * cos(w0) + 2.0f * sqrt(A) * alpha)) / a0
        b1 = (-2.0f * A * ((A - 1.0f) + (A + 1.0f) * cos(w0))) / a0
        b2 = (A * ((A + 1.0f) + (A - 1.0f) * cos(w0) - 2.0f * sqrt(A) * alpha)) / a0
        a1 = (2.0f * ((A - 1.0f) - (A + 1.0f) * cos(w0))) / a0
        a2 = ((A + 1.0f) - (A - 1.0f) * cos(w0) - 2.0f * sqrt(A) * alpha) / a0
    }
"""

content = content.replace("    fun process(sample: Float): Float {", new_methods + "\n    fun process(sample: Float): Float {")
open("app/src/main/java/com/example/player/EqualizerProcessor.kt", "w").write(content)
