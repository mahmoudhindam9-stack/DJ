package com.example.onlinemusic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class OnlineMusicViewModel(private val repository: OnlineMusicRepository) : ViewModel() {
    var home by mutableStateOf(AlbumatyHomeData())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun loadHome(force: Boolean = false) {
        if (isLoading) return
        if (!force && (home.albums.isNotEmpty() || home.songs.isNotEmpty())) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            runCatching { repository.getHome() }
                .onSuccess { home = it }
                .onFailure { errorMessage = it.message ?: "تعذر تحميل ألبوماتي" }
            isLoading = false
        }
    }
}
