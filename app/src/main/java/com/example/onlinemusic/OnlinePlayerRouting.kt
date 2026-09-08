package com.example.onlinemusic

import androidx.media3.common.MediaItem
import com.example.model.AudioItem
import com.example.player.AudioPlayerController

fun AudioPlayerController.enqueueOnlineSong(song: AudioItem){
 if(playlist.any{it.id==song.id}) return
 playlist.add(song)
 exoPlayer.addMediaItem(MediaItem.fromUri(song.uri))
}
