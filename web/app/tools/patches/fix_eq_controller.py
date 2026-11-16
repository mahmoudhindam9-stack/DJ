content = open("app/src/main/java/com/example/player/EqualizerController.kt").read()

# Fix applySharedSnapshot
content = content.replace("""    private fun applySharedSnapshot(
        levels: FloatArray,
        enabled: Boolean,
        preset: String,
        sharedPreampDb: Float
    ) {""", """    private fun applySharedSnapshot(
        levels: FloatArray,
        enabled: Boolean,
        preset: String,
        sharedPreampDb: Float,
        sharedBassBoost: Float,
        sharedTrebleBoost: Float
    ) {""")
content = content.replace("""        preampDb = sharedPreampDb.coerceIn(0f, 12f)
        syncQuickFromBands()
        onUpdate()
    }""", """        preampDb = sharedPreampDb.coerceIn(0f, 12f)
        bassBoostLevel = sharedBassBoost.coerceIn(0f, 1f)
        trebleBoostLevel = sharedTrebleBoost.coerceIn(0f, 1f)
        syncQuickFromBands()
        onUpdate()
    }""")

# Fix broadcastState
content = content.replace("""    private fun broadcastState() {
        val levels = bands.map { it.currentLevelDb.toFloat() }.toFloatArray()
        val enabled = isEnabled
        val preset = selectedPreset
        val sharedPreamp = if (enabled) preampDb else 0f
        //DeckFxAudioProcessor.setGlobalPreampDb(sharedPreamp)
        GlobalEqualizerState.update(levels, enabled, sharedPreamp)
        for (controller in instanceRegistry) {
            controller.applySharedSnapshot(levels, enabled, preset, sharedPreamp)
        }
    }""", """    private fun broadcastState() {
        val levels = bands.map { it.currentLevelDb.toFloat() }.toFloatArray()
        val enabled = isEnabled
        val preset = selectedPreset
        val sharedPreamp = if (enabled) preampDb else 0f
        val sharedBassBoost = if (enabled) bassBoostLevel * 12f else 0f
        val sharedTrebleBoost = if (enabled) trebleBoostLevel * 12f else 0f
        
        GlobalEqualizerState.update(levels, enabled, sharedPreamp, sharedBassBoost, sharedTrebleBoost)
        for (controller in instanceRegistry) {
            controller.applySharedSnapshot(levels, enabled, preset, sharedPreamp, bassBoostLevel, trebleBoostLevel)
        }
    }""")

# Fix persistState
content = content.replace("""                .putBoolean("enabled", isEnabled)
                .putFloat("preamp", preampDb)""", """                .putBoolean("enabled", isEnabled)
                .putFloat("preamp", preampDb)
                .putFloat("bassBoost", bassBoostLevel)
                .putFloat("trebleBoost", trebleBoostLevel)""")

# Fix loadState
content = content.replace("""            isEnabled = prefs.getBoolean("enabled", false)
            preampDb = prefs.getFloat("preamp", 0f).coerceIn(0f, 12f)
            GlobalEqualizerState.update(
                bands.map { it.currentLevelDb.toFloat() }.toFloatArray(),
                isEnabled,
                if (isEnabled) preampDb else 0f
            )""", """            isEnabled = prefs.getBoolean("enabled", false)
            preampDb = prefs.getFloat("preamp", 0f).coerceIn(0f, 12f)
            bassBoostLevel = prefs.getFloat("bassBoost", 0f).coerceIn(0f, 1f)
            trebleBoostLevel = prefs.getFloat("trebleBoost", 0f).coerceIn(0f, 1f)
            GlobalEqualizerState.update(
                bands.map { it.currentLevelDb.toFloat() }.toFloatArray(),
                isEnabled,
                if (isEnabled) preampDb else 0f,
                if (isEnabled) bassBoostLevel * 12f else 0f,
                if (isEnabled) trebleBoostLevel * 12f else 0f
            )""")

open("app/src/main/java/com/example/player/EqualizerController.kt", "w").write(content)
