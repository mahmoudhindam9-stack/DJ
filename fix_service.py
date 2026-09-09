with open("app/src/main/java/com/example/player/MusicService.kt", "r") as f:
    content = f.read()

rep_old = "mediaSession.setMetadata(MediaMetadataCompat.Builder().putString(MediaMetadataCompat.METADATA_KEY_TITLE, title).putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist).build())"
rep_new = "mediaSession.setMetadata(MediaMetadataCompat.Builder().putString(MediaMetadataCompat.METADATA_KEY_TITLE, title).putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist).putLong(MediaMetadataCompat.METADATA_KEY_DURATION, playerController?.durationMs ?: 0L).build())"

content = content.replace(rep_old, rep_new)

with open("app/src/main/java/com/example/player/MusicService.kt", "w") as f:
    f.write(content)
