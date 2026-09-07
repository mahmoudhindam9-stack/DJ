with open('app/src/main/java/com/example/player/AudioPlayerController.kt', 'r') as f:
    text = f.read()

# 1. Update persistSession
target_persist = """                .putFloat(KEY_VOLUME, volume)
                .putString(KEY_TITLE, currentSong?.title ?: "مشغل الموسيقى")"""
replacement_persist = """                .putFloat(KEY_VOLUME, volume)
                .putLong("crossfade", crossfadeDurationMs)
                .putString(KEY_TITLE, currentSong?.title ?: "مشغل الموسيقى")"""
text = text.replace(target_persist, replacement_persist)

# 2. Update restoreSession
target_restore = """            volume = prefs.getFloat(KEY_VOLUME, 1f).coerceIn(0f, 1f)

            currentSongIndex = savedIndex"""
replacement_restore = """            volume = prefs.getFloat(KEY_VOLUME, 1f).coerceIn(0f, 1f)
            crossfadeDurationMs = prefs.getLong("crossfade", 2000L).coerceIn(0L, 10000L)

            currentSongIndex = savedIndex"""
text = text.replace(target_restore, replacement_restore)

# 3. Update updateProgress
target_progress = """    fun updateProgress() {
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
    }"""
    
replacement_progress = """    fun updateProgress() {
        if (exoPlayer.isPlaying || durationMs == 0L) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val realDuration = exoPlayer.duration
            if (realDuration > 0L) durationMs = realDuration
            
            if (crossfadeDurationMs > 0L && exoPlayer.isPlaying) {
                val remaining = durationMs - currentPositionMs
                if (remaining > 0 && remaining < crossfadeDurationMs) {
                    // Fade out
                    val fraction = (remaining.toFloat() / crossfadeDurationMs.toFloat()).coerceIn(0f, 1f)
                    val targetVol = kotlin.math.sin(fraction * (kotlin.math.PI / 2)).toFloat()
                    try { exoPlayer.volume = targetVol * volume } catch(e:Exception){}
                } else if (currentPositionMs < crossfadeDurationMs) {
                    // Fade in
                    val fraction = (currentPositionMs.toFloat() / crossfadeDurationMs.toFloat()).coerceIn(0f, 1f)
                    val targetVol = kotlin.math.sin(fraction * (kotlin.math.PI / 2)).toFloat()
                    try { exoPlayer.volume = targetVol * volume } catch(e:Exception){}
                } else {
                    try { exoPlayer.volume = volume } catch(e:Exception){}
                }
            } else if (exoPlayer.isPlaying) {
                try { exoPlayer.volume = volume } catch(e:Exception){}
            }
            
            persistSession()
        }
    }"""
text = text.replace(target_progress, replacement_progress)

with open('app/src/main/java/com/example/player/AudioPlayerController.kt', 'w') as f:
    f.write(text)
