package com.example.onlinemusic

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class OnlineMusicViewModel(
    private val repository: OnlineMusicRepository,
    private val audiusRepository: AudiusMusicRepository = AudiusMusicRepository()
) : ViewModel() {
    var home by mutableStateOf(AlbumatyHomeData())
        private set
    var section by mutableStateOf<AlbumatySection?>(null)
        private set
    var audiusHome by mutableStateOf(AudiusHomeData())
        private set
    var audiusSearchResults by mutableStateOf<List<AudiusTrack>>(emptyList())
        private set
    var audiusArtistDetail by mutableStateOf<AudiusArtistDetail?>(null)
        private set
    var audiusGenreDetail by mutableStateOf<Pair<String, List<AudiusTrack>>?>(null)
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

    fun loadAudiusHome(force: Boolean = false) {
        if (isLoading) return
        if (!force && (audiusHome.trending.isNotEmpty() || audiusHome.artists.isNotEmpty())) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            runCatching { audiusRepository.getHome() }
                .onSuccess { audiusHome = it }
                .onFailure { errorMessage = it.message ?: "تعذر تحميل الموسيقى الأجنبية" }
            isLoading = false
        }
    }

    fun searchAudius(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            runCatching { audiusRepository.search(query) }
                .onSuccess { audiusSearchResults = it }
                .onFailure { errorMessage = it.message ?: "تعذر البحث" }
            isLoading = false
        }
    }

    fun clearAudiusSearch() {
        audiusSearchResults = emptyList()
        errorMessage = null
    }

    fun openSection(link: AlbumatyLink) {
        section = AlbumatySection(link.title, link.url)
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            runCatching { repository.getSection(link) }
                .onSuccess { loaded ->
                    section = loaded
                    errorMessage = null
                }
                .onFailure { errorMessage = it.message ?: "تعذر تحميل محتوى القسم" }
            isLoading = false
        }
    }

    fun openAudiusArtist(artist: AudiusArtist) {
        audiusArtistDetail = AudiusArtistDetail(artist, emptyList())
        audiusGenreDetail = null
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            runCatching { audiusRepository.getArtistTracks(artist.id) }
                .onSuccess { audiusArtistDetail = it }
                .onFailure { errorMessage = it.message ?: "تعذر تحميل أغاني الفنان" }
            isLoading = false
        }
    }

    fun openAudiusGenre(genre: String) {
        audiusGenreDetail = genre to emptyList()
        audiusArtistDetail = null
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            runCatching { audiusRepository.getGenreTracks(genre) }
                .onSuccess { audiusGenreDetail = genre to it }
                .onFailure { errorMessage = it.message ?: "تعذر تحميل هذا النوع" }
            isLoading = false
        }
    }

    fun closeSection() {
        section = null
        errorMessage = null
        isLoading = false
    }

    fun closeAudiusDetail() {
        audiusArtistDetail = null
        audiusGenreDetail = null
        errorMessage = null
        isLoading = false
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

    suspend fun resolveAudiusTrack(track: AudiusTrack): OnlineMusicTrack {
        resolvedTracks["audius:${track.id}"]?.let { return it }
        isResolvingTrack = true
        return try {
            audiusRepository.resolveTrack(track).also { resolvedTracks["audius:${track.id}"] = it }
        } finally {
            isResolvingTrack = false
        }
    }

    suspend fun downloadTrack(audioUrl: String, resolver: ContentResolver, destination: Uri): Long =
        repository.downloadToUri(audioUrl, resolver, destination)

    suspend fun downloadAudiusTrack(audioUrl: String, resolver: ContentResolver, destination: Uri): Long =
        audiusRepository.downloadToUri(audioUrl, resolver, destination)
}
