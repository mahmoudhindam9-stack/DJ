with open('app/src/main/java/com/example/player/AudioPlayerController.kt', 'r') as f:
    text = f.read()

update_old = '''    fun updateProgress() {
        if (exoPlayer.isPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (durationMs <= 0L) durationMs = exoPlayer.duration.coerceAtLeast(0L)
            persistSession()
        }
    }'''

update_new = '''    fun updateProgress() {
        if (exoPlayer.isPlaying || durationMs == 0L) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (durationMs <= 0L) durationMs = exoPlayer.duration.coerceAtLeast(0L)
            
            if (crossfadeDurationMs > 0L && exoPlayer.isPlaying) {
                val remaining = durationMs - currentPositionMs
                if (remaining > 0 && remaining < crossfadeDurationMs) {
                    val targetVol = (remaining.toFloat() / crossfadeDurationMs.toFloat()).coerceIn(0f, 1f)
                    exoPlayer.volume = targetVol * volume
                } else if (currentPositionMs < crossfadeDurationMs) {
                    val targetVol = (currentPositionMs.toFloat() / crossfadeDurationMs.toFloat()).coerceIn(0f, 1f)
                    exoPlayer.volume = targetVol * volume
                } else {
                    exoPlayer.volume = volume
                }
            } else if (exoPlayer.isPlaying) {
                exoPlayer.volume = volume
            }
            
            persistSession()
        }
    }'''

text = text.replace(update_old, update_new)

with open('app/src/main/java/com/example/player/AudioPlayerController.kt', 'w') as f:
    f.write(text)
