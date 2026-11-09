package com.example.onlinemusic

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class OnlineMusicViewModel(
    private val repository: OnlineMusicRepository,
    private val audiusRepository: AudiusMusicRepository = AudiusMusicRepository()
) : ViewModel() {
    companion object {
        private const val TAG = "OnlineMusicViewModel"
    }

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
    var isSearchingAlbumaty by mutableStateOf(false)
        private set
    var albumatySearchResults by mutableStateOf<List<AlbumatyLink>>(emptyList())
        private set
    var isResolvingTrack by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private val resolvedTracks = mutableMapOf<String, OnlineMusicTrack>()
    var moodPlaylist by mutableStateOf<List<AlbumatyLink>>(emptyList())
        private set
    var moodPlaylistTitle by mutableStateOf("")
        private set
    var isBuildingMoodPlaylist by mutableStateOf(false)
        private set
    var moodPlaylistMessage by mutableStateOf<String?>(null)
        private set
    private val moodSeenUrls = mutableMapOf<String, MutableSet<String>>()
    private var localAssistant: LocalMusicAssistant? = null

    var isAssistantReady by mutableStateOf(true)
        private set
    var isAssistantLoading by mutableStateOf(false)
        private set
    var isAssistantDownloading by mutableStateOf(false)
        private set
    var assistantDownloadProgress by mutableStateOf(1f)
        private set
    var assistantStatusMessage by mutableStateOf<String?>(null)
        private set

    fun attachAssistant(context: Context) {
        if (localAssistant == null) {
            localAssistant = LocalMusicAssistant(context.applicationContext)
        }
        isAssistantReady = true
        isAssistantLoading = false
        isAssistantDownloading = false
        assistantDownloadProgress = 1f
        assistantStatusMessage = null
    }

    fun downloadAssistantModel(context: Context) {
        attachAssistant(context)
    }

    fun clearMoodPlaylist() {
        moodPlaylist = emptyList()
        moodPlaylistTitle = ""
        moodPlaylistMessage = null
    }

    fun loadHome(force: Boolean = false) {
        if (isLoading) return
        if (!force && (home.albums.isNotEmpty() || home.songs.isNotEmpty())) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            runCatching { repository.getHome() }
                .onSuccess { home = it }
                .onFailure { errorMessage = it.message ?: "Failed to load Albumaty" }
            isLoading = false
        }
    }

    fun searchAlbumaty(query: String) {
        val q = query.trim()
        if (q.isBlank()) {
            albumatySearchResults = emptyList()
            isSearchingAlbumaty = false
            return
        }
        viewModelScope.launch {
            isSearchingAlbumaty = true
            errorMessage = null
            runCatching { repository.search(q) }
                .onSuccess { albumatySearchResults = it }
                .onFailure { errorMessage = it.message ?: "Search failed" }
            isSearchingAlbumaty = false
        }
    }

    fun buildMoodPlaylist(category: MoodCategory, refresh: Boolean = false) {
        if (isBuildingMoodPlaylist) return
        viewModelScope.launch {
            isBuildingMoodPlaylist = true
            moodPlaylistTitle = category.title
            moodPlaylistMessage = null
            if (refresh) moodPlaylist = emptyList()
            val seen = moodSeenUrls.getOrPut(category.id) { mutableSetOf() }
            val assistant = localAssistant
            try {
                val queries = if (assistant != null) {
                    assistant.createSearchQueries(category.title, category.searchTerms)
                } else {
                    category.searchTerms.shuffled().take(8)
                }
                val candidates = collectMoodCandidates(
                    queries.ifEmpty { category.searchTerms.shuffled().take(8) },
                    seen,
                    category
                )
                val tracks = if (assistant != null && candidates.isNotEmpty()) {
                    assistant.rankTracks(category.title, candidates)
                } else {
                    candidates
                }
                moodPlaylist = tracks.take(60)
                seen.addAll(moodPlaylist.map { it.url })
                moodPlaylistMessage = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build mood playlist", e)
                moodPlaylistMessage = null
            } finally {
                isBuildingMoodPlaylist = false
            }
        }
    }

    fun askMusicAssistant(request: String) {
        val prompt = request.trim()
        if (prompt.isBlank() || isBuildingMoodPlaylist) return
        val assistant = localAssistant
        viewModelScope.launch {
            isBuildingMoodPlaylist = true
            moodPlaylistTitle = prompt
            moodPlaylist = emptyList()
            moodPlaylistMessage = null
            val seen = moodSeenUrls.getOrPut("custom:$prompt") { mutableSetOf() }
            try {
                val queries = assistant?.createSearchQueries(prompt, emptyList())
                    ?.ifEmpty { listOf(prompt) }
                    ?: listOf(prompt)
                val candidates = collectMoodCandidates(queries, seen, null)
                val tracks = if (assistant != null && candidates.isNotEmpty()) {
                    assistant.rankTracks(prompt, candidates)
                } else {
                    candidates
                }
                moodPlaylist = tracks.take(60)
                seen.addAll(moodPlaylist.map { it.url })
                moodPlaylistMessage = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Music assistant request failed", e)
                moodPlaylistMessage = null
            } finally {
                isBuildingMoodPlaylist = false
            }
        }
    }

    private suspend fun collectMoodCandidates(
        queries: List<String>,
        seen: MutableSet<String>,
        categoryFilter: MoodCategory?
    ): List<AlbumatyLink> = coroutineScope {
        val distinctQueries = queries.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(8)

        // Execute all search queries concurrently in parallel for sub-second speed
        val parallelLists = distinctQueries.map { term ->
            async {
                runCatching { repository.search(term) }.getOrDefault(emptyList())
            }
        }.awaitAll()

        val unseenSongs = linkedMapOf<String, AlbumatyLink>()
        val allFoundSongs = linkedMapOf<String, AlbumatyLink>()
        val artistLinks = mutableListOf<AlbumatyLink>()

        // Interleave results across the parallel query lists so every queried artist contributes songs
        val maxLen = parallelLists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxLen) {
            for (list in parallelLists) {
                val item = list.getOrNull(i) ?: continue
                if (item.isMoodSongLink()) {
                    allFoundSongs.putIfAbsent(item.url, item)
                    if (item.url !in seen) {
                        unseenSongs.putIfAbsent(item.url, item)
                    }
                } else if (item.isMoodArtistOrAlbumLink() && artistLinks.size < 4) {
                    if (artistLinks.none { it.url == item.url }) {
                        artistLinks.add(item)
                    }
                }
            }
            if (unseenSongs.size >= 90) break
        }

        // If direct song matches are few but artist/album pages were found, fetch songs from those artist pages in parallel
        if (unseenSongs.size < 12 && artistLinks.isNotEmpty()) {
            val sectionLists = artistLinks.take(3).map { link ->
                async {
                    runCatching { repository.getSection(link).content }.getOrDefault(emptyList())
                }
            }.awaitAll()
            for (list in sectionLists) {
                for (item in list) {
                    if (item.isMoodSongLink()) {
                        allFoundSongs.putIfAbsent(item.url, item)
                        if (item.url !in seen) {
                            unseenSongs.putIfAbsent(item.url, item)
                        }
                    }
                }
            }
        }

        if (unseenSongs.size >= 6) {
            return@coroutineScope unseenSongs.values.take(90)
        }

        if (allFoundSongs.isNotEmpty()) {
            seen.clear()
            return@coroutineScope allFoundSongs.values.take(90)
        }

        val fallbackSongs = if (home.songs.isNotEmpty()) {
            home.songs
        } else {
            runCatching { repository.getHome().also { home = it }.songs }.getOrDefault(emptyList())
        }
        fallbackSongs.filter { it.isMoodSongLink() }.shuffled().take(50)
    }

    override fun onCleared() {
        localAssistant?.close()
        localAssistant = null
        super.onCleared()
    }

    fun clearAlbumatySearch() {
        albumatySearchResults = emptyList()
        isSearchingAlbumaty = false
    }

    fun loadAudiusHome(force: Boolean = false) {
        if (isLoading) return
        if (!force && (audiusHome.trending.isNotEmpty() || audiusHome.artists.isNotEmpty())) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            runCatching { audiusRepository.getHome() }
                .onSuccess { audiusHome = it }
                .onFailure { errorMessage = it.message ?: "Failed to load online music" }
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
                .onFailure { errorMessage = it.message ?: "Search failed" }
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
                .onFailure { errorMessage = it.message ?: "Failed to load section content" }
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
                .onFailure { errorMessage = it.message ?: "Failed to load artist songs" }
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
                .onFailure { errorMessage = it.message ?: "Failed to load this genre" }
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
        OnlineDownloadEngine.downloadToUri(audioUrl, resolver, destination)

    suspend fun downloadAudiusTrack(audioUrl: String, resolver: ContentResolver, destination: Uri): Long =
        OnlineDownloadEngine.downloadToUri(audioUrl, resolver, destination, referer = "https://audius.co/")
}

private fun AlbumatyLink.isMoodSongLink(): Boolean = runCatching {
    val path = java.net.URI(url).path.orEmpty().lowercase()
    path.split('/').any { it == "song" || it.startsWith("song") }
}.getOrDefault(false)

private fun AlbumatyLink.isMoodArtistOrAlbumLink(): Boolean = runCatching {
    val path = java.net.URI(url).path.orEmpty().lowercase()
    path.split('/').any {
        it == "singer" || it == "artist" || it == "album" ||
            it.startsWith("singer") || it.startsWith("artist") || it.startsWith("album")
    }
}.getOrDefault(false)
