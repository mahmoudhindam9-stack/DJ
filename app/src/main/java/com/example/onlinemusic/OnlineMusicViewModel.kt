package com.example.onlinemusic

import android.content.ContentResolver
import android.content.Context
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

    var isAssistantReady by mutableStateOf(false)
        private set
    var isAssistantLoading by mutableStateOf(false)
        private set
    var isAssistantDownloading by mutableStateOf(false)
        private set
    var assistantDownloadProgress by mutableStateOf(0f)
        private set
    var assistantStatusMessage by mutableStateOf<String?>(null)
        private set

    fun attachAssistant(context: Context) {
        if (localAssistant != null) return
        val assistant = LocalMusicAssistant(context.applicationContext)
        localAssistant = assistant
        viewModelScope.launch {
            isAssistantLoading = true
            runCatching { assistant.initializeIfDownloaded() }
                .onSuccess { ready ->
                    isAssistantReady = ready
                    assistantStatusMessage = if (ready) {
                        "المساعد المحلي جاهز ويعمل دون إنترنت أو تسجيل دخول."
                    } else {
                        "حمّل نموذج Qwen3 المجاني (حوالي 328 ميجابايت) لتفعيل فهم الطلبات الحرة."
                    }
                }
                .onFailure {
                    assistantStatusMessage = "تعذر تجهيز المساعد المحلي: " + (it.message ?: "خطأ غير معروف")
                }
            isAssistantLoading = false
        }
    }

    fun downloadAssistantModel(context: Context) {
        val assistant = localAssistant ?: LocalMusicAssistant(context.applicationContext).also {
            localAssistant = it
        }
        if (isAssistantDownloading || isAssistantReady) return
        viewModelScope.launch {
            isAssistantDownloading = true
            assistantDownloadProgress = 0f
            assistantStatusMessage = "جاري تنزيل نموذج Qwen3 المجاني..."
            runCatching {
                assistant.downloadAndInitialize { progress ->
                    assistantDownloadProgress = progress
                }
            }.onSuccess {
                isAssistantReady = true
                assistantStatusMessage = "تم تجهيز المساعد. يعمل الآن محليًا دون حساب أو مفتاح API."
            }.onFailure {
                assistantStatusMessage = "تعذر تنزيل أو تشغيل النموذج: " + (it.message ?: "خطأ غير معروف")
            }
            isAssistantDownloading = false
            isAssistantLoading = false
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
        viewModelScope.launch {
            isBuildingMoodPlaylist = true
            moodPlaylistTitle = category.title
            moodPlaylistMessage = null
            if (refresh) moodPlaylist = emptyList()
            val seen = moodSeenUrls.getOrPut(category.id) { mutableSetOf() }
            val assistant = localAssistant?.takeIf { it.isReady }
            runCatching {
                val queries = if (assistant != null) {
                    assistant.createSearchQueries(category.title, category.searchTerms)
                } else category.searchTerms
                val candidates = collectMoodCandidates(
                    queries.ifEmpty { category.searchTerms },
                    seen,
                    if (assistant == null) category else null
                )
                if (assistant != null && candidates.isNotEmpty()) {
                    assistant.rankTracks(category.title, candidates)
                } else candidates
            }.onSuccess { tracks ->
                moodPlaylist = tracks.take(50)
                seen.addAll(moodPlaylist.map { it.url })
                moodPlaylistMessage = when {
                    tracks.isEmpty() -> "لم نعثر على أغنيات مناسبة في نتائج المصدر الحالي. جرّب قائمة جديدة."
                    assistant != null -> "اختار المساعد المحلي " + moodPlaylist.size + " أغنية من نتائج حقيقية متاحة."
                    else -> "تم البحث بالطريقة التقليدية. حمّل نموذج Qwen3 لتفعيل الاختيار الذكي محليًا."
                }
            }.onFailure { moodPlaylistMessage = it.message ?: "تعذر إنشاء القائمة" }
            isBuildingMoodPlaylist = false
        }
    }

    fun askMusicAssistant(request: String) {
        val prompt = request.trim()
        if (prompt.isBlank() || isBuildingMoodPlaylist) return
        val assistant = localAssistant?.takeIf { it.isReady }
        if (assistant == null) {
            moodPlaylistMessage = "حمّل نموذج Qwen3 المجاني أولًا لتفعيل فهم الأوامر الحرة."
            return
        }
        viewModelScope.launch {
            isBuildingMoodPlaylist = true
            moodPlaylistTitle = "نتيجة طلبك"
            moodPlaylist = emptyList()
            moodPlaylistMessage = "المساعد يفهم طلبك ويبحث عن الأغاني المناسبة..."
            val seen = moodSeenUrls.getOrPut("custom") { mutableSetOf() }
            runCatching {
                val queries = assistant.createSearchQueries(prompt, emptyList())
                val candidates = collectMoodCandidates(queries.ifEmpty { listOf(prompt) }, seen, null)
                assistant.rankTracks(prompt, candidates)
            }.onSuccess { tracks ->
                moodPlaylist = tracks.take(50)
                seen.addAll(moodPlaylist.map { it.url })
                moodPlaylistMessage = if (tracks.isEmpty()) {
                    "لم أجد أغنيات مناسبة في النتائج المتاحة. جرّب وصفًا مختلفًا."
                } else {
                    "تم اختيار " + moodPlaylist.size + " أغنية من نتائج فعلية بناءً على طلبك."
                }
            }.onFailure {
                moodPlaylistMessage = "تعذر تنفيذ الطلب: " + (it.message ?: "خطأ غير معروف")
            }
            isBuildingMoodPlaylist = false
        }
    }

    private suspend fun collectMoodCandidates(
        queries: List<String>,
        seen: Set<String>,
        categoryFilter: MoodCategory?
    ): List<AlbumatyLink> {
        val collected = linkedMapOf<String, AlbumatyLink>()
        for (term in queries.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(5)) {
            repository.search(term).asSequence()
                .filter { it.isMoodSongLink() }
                .filter { categoryFilter == null || categoryFilter.matches(it.title) }
                .filter { it.url !in seen }
                .forEach { collected.putIfAbsent(it.url, it) }
            if (collected.size >= 80) break
        }
        return collected.values.take(80)
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
