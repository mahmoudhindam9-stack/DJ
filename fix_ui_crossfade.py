import re

with open('app/src/main/java/com/example/MainPlayerExperience.kt', 'r') as f:
    text = f.read()

replacement = """
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { playerController.toggleShuffle() }) { Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (playerController.isShuffle) MaterialTheme.colorScheme.primary else LocalContentColor.current) }; IconButton(onClick = { playerController.toggleRepeat() }) { Icon(Icons.Filled.Repeat, "Repeat", tint = if (playerController.repeatOption != RepeatOption.OFF) MaterialTheme.colorScheme.primary else LocalContentColor.current) }; OutlinedButton(onClick = onQueue) { Icon(Icons.Filled.QueueMusic, null); Spacer(Modifier.width(5.dp)); Text("Queue") } }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Crossfade Duration: ${playerController.crossfadeDurationMs / 1000}s", style = MaterialTheme.typography.labelSmall)
            Slider(value = playerController.crossfadeDurationMs.toFloat(), onValueChange = { playerController.crossfadeDurationMs = it.toLong() }, valueRange = 0f..10000f, steps = 9)
        }
"""
text = text.replace('Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { playerController.toggleShuffle() }) { Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (playerController.isShuffle) MaterialTheme.colorScheme.primary else LocalContentColor.current) }; IconButton(onClick = { playerController.toggleRepeat() }) { Icon(Icons.Filled.Repeat, "Repeat", tint = if (playerController.repeatOption != RepeatOption.OFF) MaterialTheme.colorScheme.primary else LocalContentColor.current) }; OutlinedButton(onClick = onQueue) { Icon(Icons.Filled.QueueMusic, null); Spacer(Modifier.width(5.dp)); Text("Queue") } }', replacement.strip())

with open('app/src/main/java/com/example/MainPlayerExperience.kt', 'w') as f:
    f.write(text)
