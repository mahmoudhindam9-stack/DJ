import re

content = open("app/src/main/java/com/example/player/DeckFxAudioProcessor.kt").read()

content = content.replace("private val activeEqFilters = Array(2) { Array(10) { BiquadFilter() } }", "private val activeEqFilters = Array(2) { Array(12) { BiquadFilter() } }")
content = content.replace("val EQ_FREQUENCIES = floatArrayOf(32f, 64f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)", "val EQ_FREQUENCIES = floatArrayOf(60f, 170f, 310f, 600f, 1000f, 3000f, 6000f, 12000f, 14000f, 16000f)")

syncGlobalState = """    private fun syncGlobalState() {
        val version = GlobalEqualizerState.version
        if (version == appliedEqVersion) return
        val levels = GlobalEqualizerState.levelsDb
        eqEnabled = GlobalEqualizerState.enabled
        for (ch in 0 until channelCount) {
            for (i in 0 until 10) {
                activeEqFilters[ch][i].setPeakingEQ(EQ_FREQUENCIES[i], levels.getOrElse(i) { 0f }, 1.0f, sampleRate.toFloat())
            }
            activeEqFilters[ch][10].setLowShelf(80f, GlobalEqualizerState.bassBoostDb, sampleRate.toFloat())
            activeEqFilters[ch][11].setHighShelf(10000f, GlobalEqualizerState.trebleBoostDb, sampleRate.toFloat())
        }
        appliedEqVersion = version
    }"""
content = re.sub(r'    private fun syncGlobalState\(\) \{.*?\n    \}', syncGlobalState, content, flags=re.DOTALL)

applyEq = """    private fun applyEq(sample: Float, ch: Int): Float {
        var s = sample
        for (i in 0 until 12) {
            s = activeEqFilters[ch][i].process(s)
        }
        return s
    }"""
content = re.sub(r'    private fun applyEq\(sample: Float, ch: Int\): Float \{.*?\n    \}', applyEq, content, flags=re.DOTALL)

# Add preamp logic and soft limiter inside queueInput
queueInput = """    private fun softLimit(sample: Float): Float {
        val LIMITER_THRESHOLD = 0.82f
        val magnitude = abs(sample)
        if (magnitude <= LIMITER_THRESHOLD) return sample
        val excess = (magnitude - LIMITER_THRESHOLD) / (1f - LIMITER_THRESHOLD)
        val compressed = LIMITER_THRESHOLD + (1f - LIMITER_THRESHOLD) * tanh(excess)
        return if (sample < 0f) -compressed else compressed
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive()) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val bytes = inputBuffer.remaining()
        if (bytes <= 0) return
        
        syncGlobalState()
        
        if (activeEffects.isEmpty() && !eqEnabled) {
            val output = replaceOutputBuffer(bytes)
            output.put(inputBuffer)
            output.flip()
            return
        }
        
        val output = replaceOutputBuffer(bytes)
        val frames = bytes / (2 * channelCount)
        val fxAmount = amount.coerceIn(0.01f, 1f)
        val gain = if (eqEnabled) Math.pow(10.0, GlobalEqualizerState.preampDb.toDouble() / 20.0).toFloat() else 1f
        
        for (f in 0 until frames) {
            for (ch in 0 until channelCount) {
                if (!inputBuffer.hasRemaining()) break
                val inputShort = inputBuffer.short
                var sample = inputShort.toFloat() / 32768.0f
                
                if (eqEnabled) {
                    sample = applyEq(sample, ch)
                }
                
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
                }
                
                if (eqEnabled) {
                    sample *= gain
                }
                sample = softLimit(sample)
                
                val outSample = sample.coerceIn(-1f, 1f)
                output.putShort((outSample * 32767.0f).roundToInt().toShort())
            }
        }
        output.flip()
    }"""
content = re.sub(r'    override fun queueInput\(inputBuffer: ByteBuffer\) \{.*?\n    \}', queueInput, content, flags=re.DOTALL)

# Fix configurations limits
content = content.replace("""        for (ch in 0 until 2) {
            for (i in 0 until 10) {
                activeEqFilters[ch][i].resetState()
            }
        }""", """        for (ch in 0 until 2) {
            for (i in 0 until 12) {
                activeEqFilters[ch][i].resetState()
            }
        }""")
content = content.replace("""        for (ch in 0 until 2) {
            for (i in 0 until 10) activeEqFilters[ch][i].resetState()
        }""", """        for (ch in 0 until 2) {
            for (i in 0 until 12) activeEqFilters[ch][i].resetState()
        }""")

open("app/src/main/java/com/example/player/DeckFxAudioProcessor.kt", "w").write(content)
