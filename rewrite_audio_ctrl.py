import re

with open('app/src/main/java/com/example/player/AudioPlayerController.kt', 'r') as f:
    text = f.read()

# We will rewrite the AudioPlayerController to use two ExoPlayers that crossfade.
# Actually, that's a massive rewrite. Is there a simpler way?
# What if I just use `exoPlayer.play()` on a second player when `remaining < crossfadeDurationMs`?

# Let's see the current exoPlayer usage:
