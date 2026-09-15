content = open("app/src/main/java/com/example/EqualizerScreen.kt").read()
blocks = content.split("        Spacer(modifier = Modifier.height(24.dp))\n")

header = blocks[0][:blocks[0].find("        // Bass Boost")]
bass_treble = "        // Bass Boost" + blocks[0].split("        // Bass Boost")[1]
preamp = blocks[1]
eq_bands = blocks[2]
presets = blocks[3].rsplit("    }\n}", 1)[0]

new_order = f"""{header}
{presets}        Spacer(modifier = Modifier.height(24.dp))
{eq_bands}        Spacer(modifier = Modifier.height(24.dp))
{bass_treble}
        Spacer(modifier = Modifier.height(24.dp))
{preamp}        Spacer(modifier = Modifier.height(24.dp))
    }}
}}"""

open("app/src/main/java/com/example/EqualizerScreen.kt", "w").write(new_order)
