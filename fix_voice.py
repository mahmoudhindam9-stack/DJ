with open('app/src/main/java/com/example/DJFxRackScreen.kt', 'r') as f:
    text = f.read()

target = """fun loadCustomEffects(context: Context): List<ModularEffect> {
    val prefs = context.getSharedPreferences("modular_fx", Context.MODE_PRIVATE)
    val jsonStr = prefs.getString("plugins", "[]") ?: "[]"
    val list = mutableListOf<ModularEffect>()"""

replacement = """fun loadCustomEffects(context: Context): List<ModularEffect> {
    val prefs = context.getSharedPreferences("modular_fx", Context.MODE_PRIVATE)
    val jsonStr = prefs.getString("plugins", "[]") ?: "[]"
    val list = mutableListOf<ModularEffect>()
    
    // Built-in Voice effects
    list.add(ModularEffect("voice_woman", "👩 Woman Voice", false))
    list.add(ModularEffect("voice_kid", "👶 Kid Voice", false))
    list.add(ModularEffect("voice_chipmunk", "🐿️ Chipmunk", false))
    list.add(ModularEffect("voice_monster", "👹 Monster", false))
    list.add(ModularEffect("voice_demon", "👻 Dark Demon", false))
    list.add(ModularEffect("voice_giant", "🏔️ Giant Bass", false))
"""

text = text.replace(target, replacement)

with open('app/src/main/java/com/example/DJFxRackScreen.kt', 'w') as f:
    f.write(text)
