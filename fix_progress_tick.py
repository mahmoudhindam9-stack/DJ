import re

with open('app/src/main/java/com/example/player/MusicService.kt', 'r') as f:
    content = f.read()

bad_loop = '''                if (playerController?.isPlaying == true) {
                    playerController?.updateProgress()
                    refreshPlaybackPosition()
                    playerController?.let {
                        PlaybackNotificationRouter.updateProgress(
                            applicationContext, "player", it.currentPositionMs, it.durationMs
                        )
                    }
                } else {
                    progressJob?.cancel()
                    break
                }'''

good_loop = '''                if (playerController?.isPlaying == true) {
                    playerController?.updateProgress()
                    refreshPlaybackPosition()
                    playerController?.let {
                        PlaybackNotificationRouter.updateProgress(
                            applicationContext, "player", it.currentPositionMs, it.durationMs
                        )
                    }
                    kotlinx.coroutines.delay(100)
                } else {
                    break
                }'''

content = content.replace(bad_loop, good_loop)

with open('app/src/main/java/com/example/player/MusicService.kt', 'w') as f:
    f.write(content)

print("Updated MusicService.kt")
