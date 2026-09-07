import re

# 1. Fix MainPlayerExperience.kt insertion
main_path = 'app/src/main/java/com/example/MainPlayerExperience.kt'
with open(main_path, 'r') as f:
    main_text = f.read()

# I need to find the erroneous insertion.
# The erroneous code is likely around line 44 or so?
bad_ui = """
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Crossfade Duration: ${playerController.crossfadeDurationMs / 1000}s", style = MaterialTheme.typography.labelSmall)
            Slider(value = playerController.crossfadeDurationMs.toFloat(), onValueChange = { playerController.crossfadeDurationMs = it.toLong() }, valueRange = 0f..10000f, steps = 9)
        }
"""
main_text = main_text.replace(bad_ui, "")

# Find the correct place to put it. We want it at the end of the MainPlayerExperience composable, which is right after the Row with shuffle/repeat.
good_ui = """
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Crossfade Duration: ${playerController.crossfadeDurationMs / 1000}s", style = MaterialTheme.typography.labelSmall)
            Slider(value = playerController.crossfadeDurationMs.toFloat(), onValueChange = { playerController.crossfadeDurationMs = it.toLong() }, valueRange = 0f..10000f, steps = 9)
        }
"""

main_text = main_text.replace('OutlinedButton(onClick = onQueue) { Icon(Icons.Filled.QueueMusic, null); Spacer(Modifier.width(5.dp)); Text("Queue") } }', 'OutlinedButton(onClick = onQueue) { Icon(Icons.Filled.QueueMusic, null); Spacer(Modifier.width(5.dp)); Text("Queue") } }\n' + good_ui)

with open(main_path, 'w') as f:
    f.write(main_text)

# 2. Fix DJFxRackScreen.kt imports for items
fx_path = 'app/src/main/java/com/example/DJFxRackScreen.kt'
with open(fx_path, 'r') as f:
    fx_text = f.read()

if "import androidx.compose.foundation.lazy.items" not in fx_text:
    fx_text = fx_text.replace("import androidx.compose.foundation.layout.*", "import androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.LazyRow")

with open(fx_path, 'w') as f:
    f.write(fx_text)

