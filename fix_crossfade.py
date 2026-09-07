import re

with open('app/src/main/java/com/example/player/AudioPlayerController.kt', 'r') as f:
    text = f.read()

# Add crossfade state
text = text.replace('val eqController = EqualizerController(context) { syncEq() }', 
                    'val eqController = EqualizerController(context) { syncEq() }\n    var crossfadeDurationMs by mutableLongStateOf(2000L)')

with open('app/src/main/java/com/example/player/AudioPlayerController.kt', 'w') as f:
    f.write(text)
