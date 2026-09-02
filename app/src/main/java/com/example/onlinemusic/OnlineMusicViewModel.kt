package com.example.onlinemusic

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class OnlineMusicViewModel(private val repository: OnlineMusicRepository) : ViewModel() {
    
    val tracks = mutableStateListOf<OnlineMusicTrack>()
    var isLoading = mutableStateListOf<Boolean>() // Simple loading state

    fun search(query: String) {
        viewModelScope.launch {
            // Update tracks based on repository search
        }
    }
}
