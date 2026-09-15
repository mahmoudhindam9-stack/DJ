content = open("app/src/main/java/com/example/player/GlobalEqualizerAudioProcessor.kt").read()

content = content.replace("private fun createBank() = Array(2) { Array(10) { BiquadFilter() } }", "private fun createBank() = Array(2) { Array(12) { BiquadFilter() } }")

configure_bank = """    private fun configureBank(bank: Array<Array<BiquadFilter>>, levels: FloatArray, bassBoostDb: Float, trebleBoostDb: Float) {
        for (ch in 0 until channelCount) {
            for (i in 0 until 10) {
                bank[ch][i].setPeakingEQ(
                    FREQUENCIES[i],
                    levels.getOrElse(i) { 0f },
                    Q,
                    sampleRate.toFloat()
                )
                bank[ch][i].resetState()
            }
            bank[ch][10].setLowShelf(80f, bassBoostDb, sampleRate.toFloat())
            bank[ch][10].resetState()
            bank[ch][11].setHighShelf(10000f, trebleBoostDb, sampleRate.toFloat())
            bank[ch][11].resetState()
        }
    }"""
content = content.replace("""    private fun configureBank(bank: Array<Array<BiquadFilter>>, levels: FloatArray) {
        for (ch in 0 until channelCount) {
            for (i in 0 until 10) {
                bank[ch][i].setPeakingEQ(
                    FREQUENCIES[i],
                    levels.getOrElse(i) { 0f },
                    Q,
                    sampleRate.toFloat()
                )
                bank[ch][i].resetState()
            }
        }
    }""", configure_bank)

sync = """    private fun syncGlobalState() {
        val version = GlobalEqualizerState.version
        if (version == appliedVersion) return
        configureBank(target, GlobalEqualizerState.levelsDb, GlobalEqualizerState.bassBoostDb, GlobalEqualizerState.trebleBoostDb)
        transitionPosition = 0
        transitionActive = true
        appliedVersion = version
    }"""
content = content.replace("""    private fun syncGlobalState() {
        val version = GlobalEqualizerState.version
        if (version == appliedVersion) return
        configureBank(target, GlobalEqualizerState.levelsDb)
        transitionPosition = 0
        transitionActive = true
        appliedVersion = version
    }""", sync)

applyEq = """    private fun applyEq(sample: Float, ch: Int, amount: Float): Float {
        var current = sample
        var next = sample
        for (i in 0 until 12) {
            current = active[ch][i].process(current)
            next = target[ch][i].process(next)
        }
        return current * (1f - amount) + next * amount
    }"""
content = content.replace("""    private fun applyEq(sample: Float, ch: Int, amount: Float): Float {
        var current = sample
        var next = sample
        for (i in 0 until 10) {
            current = active[ch][i].process(current)
            next = target[ch][i].process(next)
        }
        return current * (1f - amount) + next * amount
    }""", applyEq)

config_replace = """        for (bank in arrayOf(active, target)) {
            for (ch in 0 until channelCount) {
                for (i in 0 until 10) {
                    bank[ch][i].setPeakingEQ(FREQUENCIES[i], 0f, Q, sampleRate.toFloat())
                    bank[ch][i].resetState()
                }
                bank[ch][10].setLowShelf(80f, 0f, sampleRate.toFloat())
                bank[ch][10].resetState()
                bank[ch][11].setHighShelf(10000f, 0f, sampleRate.toFloat())
                bank[ch][11].resetState()
            }
        }"""
content = content.replace("""        for (bank in arrayOf(active, target)) {
            for (ch in 0 until channelCount) {
                for (i in 0 until 10) {
                    bank[ch][i].setPeakingEQ(FREQUENCIES[i], 0f, Q, sampleRate.toFloat())
                    bank[ch][i].resetState()
                }
            }
        }""", config_replace)

flush = """    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        for (bank in arrayOf(active, target)) {
            for (ch in 0 until channelCount) {
                for (i in 0 until 12) bank[ch][i].resetState()
            }
        }
        appliedVersion = -1L
        transitionActive = false
        transitionPosition = TRANSITION_FRAMES
    }"""
content = content.replace("""    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        for (bank in arrayOf(active, target)) {
            for (ch in 0 until channelCount) {
                for (i in 0 until 10) bank[ch][i].resetState()
            }
        }
        appliedVersion = -1L
        transitionActive = false
        transitionPosition = TRANSITION_FRAMES
    }""", flush)

open("app/src/main/java/com/example/player/GlobalEqualizerAudioProcessor.kt", "w").write(content)
