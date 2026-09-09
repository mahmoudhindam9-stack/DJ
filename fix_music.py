import re

path = "app/src/main/java/com/example/player/MusicService.kt"
with open(path, 'r') as f:
    content = f.read()

content = content.replace("serviceJob.cancel() mediaSession", "serviceJob.cancel(); mediaSession")
content = content.replace("mediaSession.isActive = true", "if (isPlaying) startProgressTick() else stopProgressTick(); mediaSession.isActive = true")

with open(path, 'w') as f:
    f.write(content)
