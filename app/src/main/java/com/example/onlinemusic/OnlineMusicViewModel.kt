package com.example.onlinemusic

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class OnlineMusicViewModel(private val repository: OnlineMusicRepository) : ViewModel() {
    var home by mutableStateOf(AlbumatyHomeData())
        private set
    var section by mutableStateOf<AlbumatySection?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isResolvingTrack by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private val resolvedTracks = mutableMapOf<String, OnlineMusicTrack>()

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

    fun openSection(link: AlbumatyLink) {
        if (isLoading) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            runCatching { repository.getSection(link) }
                .onSuccess { section = it }
                .onFailure { errorMessage = it.message ?: "تعذر تحميل محتوى القسم" }
            isLoading = false
        }
    }

    fun closeSection() {
        section = null
        errorMessage = null
    }

    suspend fun resolveTrack(link: AlbumatyLink): OnlineMusicTrack {
        resolvedTracks[link.url]?.let { return it }
        isResolvingTrack = true
        return try {
            repository.resolveTrack(link).also { resolvedTracks[link.url] = it }
        } finally {
            isResolvingTrack = false
        }
    }

    suspend fun downloadTrack(audioUrl: String, resolver: ContentResolver, destination: Uri): Long =
        repository.downloadToUri(audioUrl, resolver, destination)
}
