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
    var moodPlaylistError by mutableStateOf<String?>(null)
        private set
    private val moodSeenUrls = mutableMapOf<String, MutableSet<String>>()
    private val playlistCache = linkedMapOf<String, List<AlbumatyLink>>()
    private val recentTrackSignatures = ArrayDeque<String>()
    private var lastMoodRequest: Pair<MoodCategory?, String>? = null
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
        moodPlaylistError = null
    }

    fun retryLastMoodPlaylist() {
        val (category, prompt) = lastMoodRequest ?: return
        if (category != null) {
            buildMoodPlaylist(category, refresh = true)
        } else if (prompt.isNotBlank()) {
            askMusicAssistant(prompt, refresh = true)
        }
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
        val genRequest = PlaylistGenerationRequest(
            categoryId = category.id,
            prompt = category.title,
            source = "albumaty",
            playlistSize = 50,
            diversify = true
        )
        lastMoodRequest = category to category.title

        if (!refresh) {
            val cached = playlistCache[genRequest.cacheKey]
            if (!cached.isNullOrEmpty()) {
                moodPlaylistTitle = category.title
                moodPlaylist = cached
                moodPlaylistError = null
                moodPlaylistMessage = null
                return
            }
        }

        viewModelScope.launch {
            isBuildingMoodPlaylist = true
            moodPlaylistTitle = category.title
            moodPlaylistMessage = null
            moodPlaylistError = null
            if (refresh) moodPlaylist = emptyList()
            val seen = moodSeenUrls.getOrPut(category.id) { mutableSetOf() }
            val assistant = localAssistant ?: LocalMusicAssistant(null)
            try {
                val queries = assistant.createSearchQueries(category.title, category.searchTerms)
                    .ifEmpty { category.searchTerms.shuffled().take(8) }
                val candidates = collectMoodCandidates(
                    queries = queries,
                    seen = seen,
                    categoryFilter = category
                )
                val recentSet = recentTrackSignatures.toSet()
                val tracks = assistant.rankTracks(
                    request = category.title,
                    candidates = candidates,
                    category = category,
                    recentTrackSignatures = recentSet,
                    diversify = genRequest.diversify
                ).take(genRequest.playlistSize)

                if (tracks.isEmpty()) {
                    moodPlaylist = emptyList()
                    moodPlaylistError = "لم يتم العثور على أغاني مطابقة لـ ${category.title}"
                } else {
                    moodPlaylist = tracks
                    moodPlaylistError = null
                    seen.addAll(tracks.map { it.url })
                    recordRecentSignatures(tracks)
                    cachePlaylist(genRequest.cacheKey, tracks)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build mood playlist", e)
                moodPlaylist = emptyList()
                moodPlaylistError = e.message?.takeIf { it.isNotBlank() }
                    ?: "تعذر إنشاء القائمة الذكية، تأكد من الاتصال"
            } finally {
                isBuildingMoodPlaylist = false
            }
        }
    }

    fun askMusicAssistant(request: String, refresh: Boolean = false) {
        val prompt = request.trim()
        if (prompt.isBlank() || isBuildingMoodPlaylist) return
        val genRequest = PlaylistGenerationRequest(
            categoryId = null,
            prompt = prompt,
            source = "albumaty",
            playlistSize = 50,
            diversify = true
        )
        lastMoodRequest = null to prompt

        if (!refresh) {
            val cached = playlistCache[genRequest.cacheKey]
            if (!cached.isNullOrEmpty()) {
                moodPlaylistTitle = prompt
                moodPlaylist = cached
                moodPlaylistError = null
                moodPlaylistMessage = null
                return
            }
        }

        val assistant = localAssistant ?: LocalMusicAssistant(null)
        viewModelScope.launch {
            isBuildingMoodPlaylist = true
            moodPlaylistTitle = prompt
            moodPlaylist = emptyList()
            moodPlaylistMessage = null
            moodPlaylistError = null
            val seen = moodSeenUrls.getOrPut("custom:$prompt") { mutableSetOf() }
            try {
                val queries = assistant.createSearchQueries(prompt, emptyList())
                    .ifEmpty { listOf(prompt) }
                val candidates = collectMoodCandidates(queries, seen, null)
                val recentSet = recentTrackSignatures.toSet()
                val tracks = assistant.rankTracks(
                    request = prompt,
                    candidates = candidates,
                    category = null,
                    recentTrackSignatures = recentSet,
                    diversify = genRequest.diversify
                ).take(genRequest.playlistSize)

                if (tracks.isEmpty()) {
                    moodPlaylist = emptyList()
                    moodPlaylistError = "لم يتم العثور على أغاني مطابقة للطلب"
                } else {
                    moodPlaylist = tracks
                    moodPlaylistError = null
                    seen.addAll(tracks.map { it.url })
                    recordRecentSignatures(tracks)
                    cachePlaylist(genRequest.cacheKey, tracks)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Music assistant request failed", e)
                moodPlaylist = emptyList()
                moodPlaylistError = e.message?.takeIf { it.isNotBlank() }
                    ?: "تعذر إنشاء القائمة الذكية، حاول مرة أخرى"
            } finally {
                isBuildingMoodPlaylist = false
            }
        }
    }

    private fun cachePlaylist(key: String, tracks: List<AlbumatyLink>) {
        playlistCache[key] = tracks
        while (playlistCache.size > 24) {
            val oldest = playlistCache.keys.firstOrNull() ?: break
            playlistCache.remove(oldest)
        }
    }

    private fun recordRecentSignatures(tracks: List<AlbumatyLink>) {
        tracks.forEach { link ->
            val sig = LocalMusicAssistant.normalizeTrackSignature(link)
            if (sig.isNotBlank()) {
                recentTrackSignatures.addLast(sig)
                while (recentTrackSignatures.size > 180) {
                    recentTrackSignatures.removeFirst()
                }
            }
        }
    }

    private suspend fun collectMoodCandidates(
        queries: List<String>,
        seen: MutableSet<String>,
        categoryFilter: MoodCategory?
    ): List<AlbumatyLink> = coroutineScope {
        val distinctQueries = queries.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(10)
        var firstError: Throwable? = null
        var successfulQueries = 0

        // Execute remote queries concurrently in parallel without falling back to unrelated home.songs
        val parallelLists = distinctQueries.map { term ->
            async {
                runCatching { repository.searchRemoteOnly(term) }
                    .onSuccess { successfulQueries++ }
                    .onFailure { err -> if (firstError == null) firstError = err }
                    .getOrDefault(emptyList())
            }
        }.awaitAll()

        val unseenSongs = linkedMapOf<String, AlbumatyLink>()
        val allFoundSongs = linkedMapOf<String, AlbumatyLink>()
        val artistLinks = mutableListOf<AlbumatyLink>()

        // Strictly ONLY include home.songs for explicit "new" or "trending" categories
        if (categoryFilter?.id == "new" || categoryFilter?.id == "trending") {
            val homeSongs = if (home.songs.isNotEmpty()) {
                home.songs
            } else {
                runCatching { repository.getHome().also { home = it }.songs }.getOrDefault(emptyList())
            }
            for (song in homeSongs.shuffled()) {
                if (song.isMoodSongLink() && categoryFilter.isAllowed(song.title)) {
                    allFoundSongs.putIfAbsent(song.url, song)
                    if (song.url !in seen) {
                        unseenSongs.putIfAbsent(song.url, song)
                    }
                }
            }
        }

        // Interleave results across the parallel query lists so every queried artist contributes songs
        val maxLen = parallelLists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxLen) {
            for (list in parallelLists) {
                val item = list.getOrNull(i) ?: continue
                if (item.isMoodSongLink()) {
                    if (categoryFilter == null || categoryFilter.isAllowed(item.title)) {
                        allFoundSongs.putIfAbsent(item.url, item)
                        if (item.url !in seen) {
                            unseenSongs.putIfAbsent(item.url, item)
                        }
                    }
                } else if (item.isMoodArtistOrAlbumLink() && artistLinks.size < 8) {
                    if (artistLinks.none { it.url == item.url }) {
                        artistLinks.add(item)
                    }
                }
            }
            if (unseenSongs.size >= 110) break
        }

        // Expand top artist/album pages in parallel when more variety is needed
        if (unseenSongs.size < 30 && artistLinks.isNotEmpty()) {
            val sectionLists = artistLinks.take(5).map { link ->
                async {
                    runCatching { repository.getSection(link).content }.getOrDefault(emptyList())
                }
            }.awaitAll()
            val maxSecLen = sectionLists.maxOfOrNull { it.size } ?: 0
            for (i in 0 until maxSecLen) {
                for (list in sectionLists) {
                    val item = list.getOrNull(i) ?: continue
                    if (item.isMoodSongLink() && (categoryFilter == null || categoryFilter.isAllowed(item.title))) {
                        allFoundSongs.putIfAbsent(item.url, item)
                        if (item.url !in seen) {
                            unseenSongs.putIfAbsent(item.url, item)
                        }
                    }
                }
            }
        }

        if (unseenSongs.size >= 6) {
            return@coroutineScope unseenSongs.values.take(100)
        }

        if (allFoundSongs.isNotEmpty()) {
            seen.clear()
            return@coroutineScope allFoundSongs.values.take(100)
        }

        if (successfulQueries == 0 && firstError != null) {
            throw firstError!!
        }
        emptyList()
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
