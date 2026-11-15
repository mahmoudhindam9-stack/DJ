package com.example.onlinemusic

import androidx.media3.common.MediaItem
import com.example.model.AudioItem
import com.example.player.AudioPlayerController
import com.example.player.UnifiedPlaybackQueue
import com.example.player.UnifiedStreamResolver

fun AudioPlayerController.enqueueOnlineSong(song: AudioItem) {
    val key = UnifiedPlaybackQueue.stableKey(song)
    if (playlist.any { UnifiedPlaybackQueue.stableKey(it) == key }) return
    val prepared = UnifiedStreamResolver.getCached(song) ?: song
    playlist.add(prepared)
    baseQueue.add(prepared)
    val mediaItem = MediaItem.Builder()
        .setUri(prepared.uri)
        .setMediaId(key)
        .apply { UnifiedStreamResolver.mimeTypeFor(prepared)?.let(::setMimeType) }
        .build()
    exoPlayer.addMediaItem(mediaItem)
    syncPlaylistShuffleIfNeeded()
}

