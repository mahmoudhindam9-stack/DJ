package com.example.onlinemusic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.model.AudioItem

data class OnlineDjRequest(val id: Long, val song: AudioItem, val deck: OnlineDeckTarget)
object OnlineDjBridge {
 private var nextId=0L
 var request by mutableStateOf<OnlineDjRequest?>(null)
  private set
 fun send(song: AudioItem, deck: OnlineDeckTarget){ nextId++; request=OnlineDjRequest(nextId,song,deck) }
 fun clear(){ request=null }
}
