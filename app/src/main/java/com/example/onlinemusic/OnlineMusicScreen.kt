package com.example.onlinemusic

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.QueueSheet
import com.example.model.AudioItem
import com.example.model.Playlist
import com.example.player.AudioPlayerController
import com.example.player.AudiusTrackCatalog
import com.example.player.UnifiedPlaybackQueue
import com.example.room.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OnlineMusicScreen(
    viewModel: OnlineMusicViewModel,
    playerController: AudioPlayerController,
    playlists: List<Playlist>,
    audioLibrary: SnapshotStateList<AudioItem>,
    playlistRepo: PlaylistRepository
) {
    val scope = rememberCoroutineScope()
    val isArabic by com.example.ui.locale.AppLanguageManager.isArabicFlow.collectAsState()
    var selectedSource by rememberSaveable { mutableStateOf("albumaty") }
    var showQueue by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedSource == "albumaty",
                onClick = { selectedSource = "albumaty" },
                label = { Text(if (isArabic) "🇦🇪 عربي" else "🇦🇪 Arabic", maxLines = 1) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedSource == "hiba",
                onClick = { selectedSource = "hiba" },
                label = { Text(if (isArabic) "🎼 كلاسيك" else "🎼 Classics", maxLines = 1) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedSource == "audius",
                onClick = { selectedSource = "audius" },
                label = { Text(if (isArabic) "🌎 أجنبي" else "🌎 Foreign", maxLines = 1) },
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    when (selectedSource) {
                        "audius" -> viewModel.loadAudiusHome(true)
                        "albumaty" -> viewModel.loadHome(true)
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = if (isArabic) "تحديث" else "Refresh")
            }
            IconButton(
                onClick = { showQueue = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = if (isArabic) "قائمة التشغيل" else "Queue")
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (selectedSource) {
                "audius" -> AudiusOnlineScreen(viewModel, playerController, scope)
                "hiba" -> HibaMusicScreen(
                    playerController = playerController,
                    onShowQueue = { showQueue = true }
                )
                else -> AlbumatyOnlineScreen(viewModel, playerController, scope)
            }
        }
    }

    if (showQueue) {
        QueueSheet(
            controller = playerController,
            playlists = playlists,
            library = audioLibrary,
            playlistRepo = playlistRepo,
            onDismiss = { showQueue = false },
            onSelect = { song ->
                playerController.play(song, null)
                showQueue = false
            }
        )
    }
}

@Composable
private fun AlbumatyOnlineScreen(
    viewModel: OnlineMusicViewModel,
    playerController: AudioPlayerController,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    val isArabic by com.example.ui.locale.AppLanguageManager.isArabicFlow.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var pendingDownload by remember { mutableStateOf<PendingOnlineDownload?>(null) }

    val saveDownloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/mpeg")
    ) { uri: Uri? ->
        val pending = pendingDownload
        pendingDownload = null
        if (uri == null || pending == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { viewModel.downloadTrack(pending.audioUrl, context.contentResolver, uri) }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.attachAssistant(context.applicationContext)
        viewModel.loadHome()
    }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length >= 2) {
            delay(350)
            viewModel.searchAlbumaty(trimmed)
        } else if (trimmed.isBlank()) {
            viewModel.clearAlbumatySearch()
        }
    }

    fun playSong(
        link: AlbumatyLink,
        collection: List<AlbumatyLink> = listOf(link),
        albumFallback: String = "Online Music"
    ) {
        val same = playerController.currentSong?.id == link.url
        if (same) {
            playerController.togglePlayPause()
            return
        }
        val songLinks = collection
            .filter { it.isSong() }
            .let { filtered ->
                if (filtered.any { it.url == link.url }) filtered else listOf(link) + filtered
            }
            .distinctBy { it.url }
        val queue = songLinks.map { UnifiedPlaybackQueue.albumatyLinkToAudioItem(it, albumFallback) }
        val startIndex = songLinks.indexOfFirst { it.url == link.url }.coerceAtLeast(0)
        playerController.playCollection(queue, startIndex)
    }

    fun downloadSong(link: AlbumatyLink) {
        scope.launch {
            runCatching { viewModel.resolveTrack(link) }.onSuccess { track ->
                val audio = track.downloadUrl ?: track.streamUrl ?: return@onSuccess
                pendingDownload = PendingOnlineDownload(track.title, audio)
                saveDownloadLauncher.launch(suggestedFileName(track.title))
            }
        }
    }

    fun queueSong(link: AlbumatyLink, albumFallback: String = "Online Music") {
        playerController.enqueueOnlineSong(UnifiedPlaybackQueue.albumatyLinkToAudioItem(link, albumFallback))
    }

    fun sendToDeck(link: AlbumatyLink, deck: OnlineDeckTarget) {
        scope.launch {
            runCatching { viewModel.resolveTrack(link) }.onSuccess { track ->
                val audio = track.streamUrl ?: return@onSuccess
                OnlineDjBridge.send(
                    AudioItem(
                        link.url,
                        track.title,
                        track.artist.ifBlank { "Albumaty" },
                        track.album ?: "Online Music",
                        0L,
                        Uri.parse(audio)
                    ),
                    deck
                )
            }
        }
    }

    fun activate(link: AlbumatyLink, collection: List<AlbumatyLink> = listOf(link)) {
        if (link.isSong()) playSong(link, collection) else viewModel.openSection(link)
    }

    viewModel.section?.let { section ->
        OnlineSectionScreen(
            section = section,
            isLoading = viewModel.isLoading,
            errorMessage = viewModel.errorMessage,
            onBack = viewModel::closeSection,
            onOpen = { link -> activate(link, section.content) },
            onPlay = { link -> playSong(link, section.content, section.title.ifBlank { "Online Music" }) },
            onDownload = ::downloadSong,
            onQueue = { link -> queueSong(link, section.title.ifBlank { "Online Music" }) },
            onDeck = ::sendToDeck,
            playerController = playerController
        )
        return
    }

    val normalized = query.trim()
    val isSearching = normalized.isNotBlank()
    val hasSearchResults = viewModel.albumatySearchResults.isNotEmpty()

    val searchSongs = if (hasSearchResults) viewModel.albumatySearchResults.filter { it.isSong() } else emptyList()
    val searchAlbums = if (hasSearchResults) viewModel.albumatySearchResults.filter { it.isAlbum() } else emptyList()
    val searchArtists = if (hasSearchResults) viewModel.albumatySearchResults.filter { it.isArtist() } else emptyList()

    val localAlbums = viewModel.home.albums.filter { normalized.isBlank() || it.title.contains(normalized, true) }
    val localSongs = viewModel.home.songs.filter { normalized.isBlank() || it.title.contains(normalized, true) }
    val localArtists = viewModel.home.artists.filter { normalized.isBlank() || it.title.contains(normalized, true) }

    Column(Modifier.fillMaxSize()) {
        // Compact unified Search + Smart AI Mood input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f).testTag("ai_prompt_input"),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = if (isArabic) "بحث" else "Search") },
                trailingIcon = if (query.isNotBlank()) {
                    {
                        IconButton(onClick = {
                            query = ""
                            viewModel.clearAlbumatySearch()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = if (isArabic) "مسح" else "Clear")
                        }
                    }
                } else null,
                placeholder = { Text(if (isArabic) "ابحث أو اطلب مود (مثال: حزين، روقان، فرح)" else "Search or ask AI mood (e.g. sad, chill, party)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    val trimmed = query.trim()
                    if (trimmed.isNotBlank()) {
                        viewModel.askMusicAssistant(trimmed)
                    }
                })
            )
            FilledTonalIconButton(
                onClick = {
                    val trimmed = query.trim()
                    if (trimmed.isNotBlank() && !viewModel.isBuildingMoodPlaylist) {
                        viewModel.askMusicAssistant(trimmed)
                    }
                },
                enabled = query.isNotBlank() && !viewModel.isBuildingMoodPlaylist,
                modifier = Modifier.size(48.dp).testTag("ai_generate_button")
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = if (isArabic) "اختيار ذكي" else "Smart AI Pick")
            }
        }

        // Compact single-line Mood Chips bar
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (viewModel.moodPlaylist.isNotEmpty() || viewModel.moodPlaylistError != null) {
                item(key = "clear_mood") {
                    AssistChip(
                        onClick = { viewModel.clearMoodPlaylist() },
                        label = { Text(if (isArabic) "✕ إغلاق القائمة" else "✕ Close Mix") }
                    )
                }
            }
            items(MoodCategory.entries, key = { it.id }) { category ->
                FilterChip(
                    modifier = Modifier.testTag("mood_chip_${category.id}"),
                    selected = viewModel.moodPlaylistTitle == category.title && viewModel.moodPlaylist.isNotEmpty(),
                    onClick = { viewModel.buildMoodPlaylist(category, refresh = true) },
                    label = { Text(category.title, maxLines = 1) }
                )
            }
        }

        if (viewModel.isBuildingMoodPlaylist || viewModel.isSearchingAlbumaty) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        }

        viewModel.moodPlaylistError?.let { errMsg ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = errMsg,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(
                        onClick = { viewModel.retryLastMoodPlaylist() },
                        modifier = Modifier.testTag("mood_retry_button")
                    ) {
                        Text(if (isArabic) "إعادة المحاولة" else "Retry")
                    }
                }
            }
        }

        // Maximum-height full-screen scrollable song area
        if (viewModel.isLoading && viewModel.home.albums.isEmpty() && viewModel.home.songs.isEmpty() && viewModel.moodPlaylist.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. Active Mood / AI Playlist songs displayed directly in the main scrollable list
                if (viewModel.moodPlaylist.isNotEmpty()) {
                    val moodMixName = viewModel.moodPlaylistTitle.ifBlank { "Mood Mix" }
                    item(key = "mood_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$moodMixName (${viewModel.moodPlaylist.size})",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(
                                onClick = {
                                    val matchingCat = MoodCategory.entries.firstOrNull { it.title == viewModel.moodPlaylistTitle }
                                    if (matchingCat != null) {
                                        viewModel.buildMoodPlaylist(matchingCat, refresh = true)
                                    } else if (viewModel.moodPlaylistTitle.isNotBlank()) {
                                        viewModel.askMusicAssistant(viewModel.moodPlaylistTitle, refresh = true)
                                    }
                                },
                                enabled = !viewModel.isBuildingMoodPlaylist,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(if (isArabic) "تحديث" else "Refresh")
                            }
                            FilledTonalButton(
                                onClick = {
                                    viewModel.moodPlaylist.forEach { link ->
                                        playerController.enqueueOnlineSong(
                                            UnifiedPlaybackQueue.albumatyLinkToAudioItem(link, moodMixName)
                                        )
                                    }
                                },
                                enabled = !viewModel.isBuildingMoodPlaylist,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text(if (isArabic) "إضافة الكل" else "Queue All")
                            }
                        }
                    }

                    items(viewModel.moodPlaylist, key = { "mood_${it.url}" }) { link ->
                        OnlineSongCard(
                            link = link,
                            onPlay = { playSong(it, viewModel.moodPlaylist, moodMixName) },
                            onDownload = ::downloadSong,
                            onQueue = { queueSong(it, moodMixName) },
                            onDeck = ::sendToDeck,
                            playerController = playerController
                        )
                    }
                } else if (isSearching && hasSearchResults) {
                    // 2. Direct Search Results
                    if (searchSongs.isNotEmpty()) {
                        items(searchSongs, key = { "s_song_${it.url}" }) { link ->
                            OnlineSongCard(
                                link = link,
                                onPlay = { playSong(it, searchSongs) },
                                onDownload = ::downloadSong,
                                onQueue = { queueSong(it) },
                                onDeck = ::sendToDeck,
                                playerController = playerController
                            )
                        }
                    }
                    if (searchAlbums.isNotEmpty()) {
                        item(key = "s_albums_hdr") { SectionHeader(if (isArabic) "الألبومات" else "Albums") }
                        items(searchAlbums, key = { "s_album_${it.url}" }) { link ->
                            SectionLinkCard(link) { activate(it, searchAlbums) }
                        }
                    }
                    if (searchArtists.isNotEmpty()) {
                        item(key = "s_artists_hdr") { SectionHeader(if (isArabic) "الفنانون" else "Artists") }
                        items(searchArtists, key = { "s_artist_${it.url}" }) { link ->
                            SectionLinkCard(link) { activate(it, searchArtists) }
                        }
                    }
                } else if (isSearching) {
                    if (localSongs.isNotEmpty()) {
                        items(localSongs, key = { "l_song_${it.url}" }) { link ->
                            OnlineSongCard(
                                link = link,
                                onPlay = { playSong(it, localSongs, "New Songs") },
                                onDownload = ::downloadSong,
                                onQueue = { queueSong(it, "New Songs") },
                                onDeck = ::sendToDeck,
                                playerController = playerController
                            )
                        }
                    }
                    if (localAlbums.isNotEmpty()) {
                        items(localAlbums, key = { "l_album_${it.url}" }) { link ->
                            SectionLinkCard(link) { activate(it, localAlbums) }
                        }
                    }
                    if (localArtists.isNotEmpty()) {
                        items(localArtists, key = { "l_artist_${it.url}" }) { link ->
                            SectionLinkCard(link) { activate(it, localArtists) }
                        }
                    }
                } else {
                    // 3. Default Home Browse — Songs first for immediate access
                    if (viewModel.home.categories.isNotEmpty()) {
                        item(key = "home_cats") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(viewModel.home.categories, key = { it.url }) { link ->
                                    Card(Modifier.clickable { activate(link) }) {
                                        Text(
                                            link.title,
                                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            maxLines = 1,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (localSongs.isNotEmpty()) {
                        items(localSongs, key = { "h_song_${it.url}" }) { link ->
                            OnlineSongCard(
                                link = link,
                                onPlay = { playSong(it, viewModel.home.songs, "New Songs") },
                                onDownload = ::downloadSong,
                                onQueue = { queueSong(it, "New Songs") },
                                onDeck = ::sendToDeck,
                                playerController = playerController
                            )
                        }
                    }
                    if (localAlbums.isNotEmpty()) {
                        item(key = "h_albums_hdr") { SectionHeader(if (isArabic) "ألبومات جديدة" else "New Albums") }
                        items(localAlbums.take(20), key = { "h_album_${it.url}" }) { link ->
                            SectionLinkCard(link) { activate(it, localAlbums) }
                        }
                    }
                    if (localArtists.isNotEmpty()) {
                        item(key = "h_artists_hdr") { SectionHeader(if (isArabic) "فنانون" else "Artists") }
                        items(localArtists.take(24), key = { "h_artist_${it.url}" }) { link ->
                            SectionLinkCard(link) { activate(it, localArtists) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@Composable
private fun AudiusOnlineScreen(
    viewModel: OnlineMusicViewModel,
    playerController: AudioPlayerController,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    val isArabic by com.example.ui.locale.AppLanguageManager.isArabicFlow.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var pendingDownload by remember { mutableStateOf<PendingOnlineDownload?>(null) }

    val saveDownloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/mpeg")
    ) { uri: Uri? ->
        val pending = pendingDownload
        pendingDownload = null
        if (uri == null || pending == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { viewModel.downloadAudiusTrack(pending.audioUrl, context.contentResolver, uri) }
        }
    }

    LaunchedEffect(Unit) { viewModel.loadAudiusHome() }

    val searchResults = viewModel.audiusSearchResults
    val displayedTracks = if (query.isBlank()) {
        (viewModel.audiusHome.trending + viewModel.audiusHome.latest).distinctBy { it.id }
    } else {
        searchResults
    }

    fun playSong(track: AudiusTrack, collection: List<AudiusTrack> = displayedTracks) {
        val same = playerController.currentSong?.id == "audius:${track.id}"
        if (same) {
            playerController.togglePlayPause()
            return
        }
        val list = if (collection.any { it.id == track.id }) collection else listOf(track) + collection
        AudiusTrackCatalog.registerAll(list)
        val queue = list.map { UnifiedPlaybackQueue.audiusTrackToAudioItem(it) }
        val startIdx = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playerController.playCollection(queue, startIdx)
    }

    fun downloadSong(track: AudiusTrack) {
        scope.launch {
            runCatching { viewModel.resolveAudiusTrack(track) }.onSuccess { resolved ->
                val audio = resolved.downloadUrl ?: resolved.streamUrl ?: return@onSuccess
                pendingDownload = PendingOnlineDownload(resolved.title, audio)
                saveDownloadLauncher.launch(suggestedFileName(resolved.title))
            }
        }
    }

    fun queueSong(track: AudiusTrack) {
        AudiusTrackCatalog.register(track)
        playerController.enqueueOnlineSong(UnifiedPlaybackQueue.audiusTrackToAudioItem(track))
    }

    fun sendToDeck(track: AudiusTrack, deck: OnlineDeckTarget) {
        scope.launch {
            runCatching { viewModel.resolveAudiusTrack(track) }.onSuccess { resolved ->
                val audio = resolved.streamUrl ?: return@onSuccess
                OnlineDjBridge.send(
                    AudioItem(
                        "audius:${track.id}",
                        resolved.title,
                        resolved.artist,
                        resolved.album ?: "Audius",
                        0L,
                        Uri.parse(audio)
                    ),
                    deck
                )
            }
        }
    }

    viewModel.audiusArtistDetail?.let { detail ->
        AudiusDetailScreen(
            detail.artist.name,
            detail.tracks,
            viewModel.isLoading,
            viewModel.errorMessage,
            viewModel::closeAudiusDetail,
            { track -> playSong(track, detail.tracks) },
            ::downloadSong,
            ::queueSong,
            ::sendToDeck,
            playerController
        )
        return
    }
    viewModel.audiusGenreDetail?.let { (genre, tracks) ->
        AudiusDetailScreen(
            genre,
            tracks,
            viewModel.isLoading,
            viewModel.errorMessage,
            viewModel::closeAudiusDetail,
            { track -> playSong(track, tracks) },
            ::downloadSong,
            ::queueSong,
            ::sendToDeck,
            playerController
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = if (isArabic) "بحث" else "Search") },
            trailingIcon = if (query.isNotBlank()) {
                {
                    IconButton(onClick = {
                        query = ""
                        viewModel.clearAudiusSearch()
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = if (isArabic) "مسح" else "Clear")
                    }
                }
            } else null,
            placeholder = { Text(if (isArabic) "ابحث في الموسيقى الأجنبية" else "Search foreign music") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                val trimmed = query.trim()
                if (trimmed.isNotBlank()) {
                    viewModel.searchAudius(trimmed)
                }
            })
        )

        LaunchedEffect(query) {
            if (query.trim().length >= 2) {
                delay(350)
                viewModel.searchAudius(query.trim())
            } else if (query.isBlank()) {
                viewModel.clearAudiusSearch()
            }
        }

        if (viewModel.isLoading && displayedTracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (query.isBlank() && viewModel.audiusHome.genres.isNotEmpty()) {
                    item(key = "audius_genres") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(viewModel.audiusHome.genres) { genre ->
                                FilterChip(
                                    selected = false,
                                    onClick = { viewModel.openAudiusGenre(genre) },
                                    label = { Text(genre, maxLines = 1) }
                                )
                            }
                        }
                    }
                }
                items(displayedTracks, key = { it.id }) { track ->
                    AudiusSongCard(track, ::playSong, ::downloadSong, ::queueSong, ::sendToDeck, playerController)
                }
            }
        }
    }
}

@Composable
private fun AudiusDetailScreen(
    title: String,
    tracks: List<AudiusTrack>,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onPlay: (AudiusTrack) -> Unit,
    onDownload: (AudiusTrack) -> Unit,
    onQueue: (AudiusTrack) -> Unit,
    onDeck: (AudiusTrack, OnlineDeckTarget) -> Unit,
    playerController: AudioPlayerController
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp))
        }
        when {
            tracks.isEmpty() && isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            tracks.isEmpty() && errorMessage != null -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tracks, key = { it.id }) {
                    AudiusSongCard(it, onPlay, onDownload, onQueue, onDeck, playerController)
                }
            }
        }
    }
}

@Composable
private fun AudiusSongCard(
    track: AudiusTrack,
    onPlay: (AudiusTrack) -> Unit,
    onDownload: (AudiusTrack) -> Unit,
    onQueue: (AudiusTrack) -> Unit,
    onDeck: (AudiusTrack, OnlineDeckTarget) -> Unit,
    playerController: AudioPlayerController
) {
    val active = playerController.currentSong?.id == "audius:${track.id}"
    val playing = active && playerController.isPlaying
    com.example.ui.components.DjSurfaceCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MusicNote, null, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { onPlay(track) }) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play")
            }
            IconButton(onClick = { onQueue(track) }) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist")
            }
            OnlineDeckButton { onDeck(track, it) }
            IconButton(onClick = { onDownload(track) }) {
                Icon(Icons.Filled.Download, "Download")
            }
        }
    }
}

@Composable
private fun OnlineSectionScreen(
    section: AlbumatySection,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onOpen: (AlbumatyLink) -> Unit,
    onPlay: (AlbumatyLink) -> Unit,
    onDownload: (AlbumatyLink) -> Unit,
    onQueue: (AlbumatyLink) -> Unit,
    onDeck: (AlbumatyLink, OnlineDeckTarget) -> Unit,
    playerController: AudioPlayerController
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text(
                section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp))
        }
        val content = section.content
        when {
            content.isEmpty() && isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            content.isEmpty() && errorMessage != null -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(content, key = { it.url }) { link ->
                    if (link.isSong()) {
                        OnlineSongCard(link, onPlay, onDownload, onQueue, onDeck, playerController)
                    } else {
                        SectionLinkCard(link, onOpen)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLinkCard(link: AlbumatyLink, onOpen: (AlbumatyLink) -> Unit) {
    val isArabic by com.example.ui.locale.AppLanguageManager.isArabicFlow.collectAsState()
    com.example.ui.components.DjSurfaceCard(Modifier.fillMaxWidth().clickable { onOpen(link) }) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Folder,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.size(10.dp))
            Text(
                link.title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = { onOpen(link) }) {
                Text(if (isArabic) "فتح" else "Open", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OnlineSongCard(
    link: AlbumatyLink,
    onPlay: (AlbumatyLink) -> Unit,
    onDownload: (AlbumatyLink) -> Unit,
    onQueue: (AlbumatyLink) -> Unit,
    onDeck: (AlbumatyLink, OnlineDeckTarget) -> Unit,
    playerController: AudioPlayerController
) {
    val active = playerController.currentSong?.id == link.url
    val playing = active && playerController.isPlaying
    val cleanFull = remember(link.title) {
        link.title
            .replace(Regex("^(?:اغنية|أغنية|تحميل|استماع)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*MP3\\s*$", RegexOption.IGNORE_CASE), "")
            .trim()
    }
    val songName = remember(cleanFull) {
        if (cleanFull.contains(" - ")) cleanFull.substringBefore(" - ").trim() else cleanFull
    }
    val artistName = remember(cleanFull) {
        if (cleanFull.contains(" - ")) cleanFull.substringAfter(" - ").trim() else ""
    }

    com.example.ui.components.DjSurfaceCard(
        modifier = Modifier.fillMaxWidth().clickable { onPlay(link) },
        tonal = active
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    null,
                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    songName.ifBlank { link.title },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (artistName.isNotBlank()) {
                    Text(
                        artistName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            FilledTonalIconButton(
                onClick = { onPlay(link) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (playing) "Pause" else "Play",
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = { onQueue(link) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist", modifier = Modifier.size(20.dp))
            }
            OnlineDeckButton { onDeck(link, it) }
            IconButton(onClick = { onDownload(link) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Download, "Download", modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun AlbumatyLink.isSong(): Boolean = runCatching {
    java.net.URI(url).path.orEmpty().trim('/').lowercase().split('/').any { it == "song" || it.startsWith("song") }
}.getOrDefault(false)

private fun AlbumatyLink.isAlbum(): Boolean = runCatching {
    java.net.URI(url).path.orEmpty().trim('/').lowercase().split('/').any { it == "album" || it.startsWith("album") }
}.getOrDefault(false)

private fun AlbumatyLink.isArtist(): Boolean = runCatching {
    java.net.URI(url).path.orEmpty().trim('/').lowercase().split('/').any {
        it == "singer" || it == "artist" || it.startsWith("singer") || it.startsWith("artist")
    }
}.getOrDefault(false)

enum class OnlineDeckTarget { A, B }

@Composable
private fun OnlineDeckButton(onSelected: (OnlineDeckTarget) -> Unit) {
    val isArabic by com.example.ui.locale.AppLanguageManager.isArabicFlow.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    IconButton(onClick = { showPicker = true }) {
        Icon(Icons.Filled.Headset, if (isArabic) "إرسال إلى DJ Deck" else "Send to DJ Deck")
    }
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(if (isArabic) "إرسال الأغنية إلى أي Deck؟" else "Send song to which Deck?") },
            text = { Text(if (isArabic) "اختر Deck A أو Deck B" else "Select A or B") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        showPicker = false
                        onSelected(OnlineDeckTarget.A)
                    }) { Text("Deck A") }
                    TextButton(onClick = {
                        showPicker = false
                        onSelected(OnlineDeckTarget.B)
                    }) { Text("Deck B") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(if (isArabic) "إلغاء" else "Cancel") }
            }
        )
    }
}

private data class PendingOnlineDownload(val title: String, val audioUrl: String)

private fun suggestedFileName(title: String): String {
    val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "online_music" }
    return if (safe.lowercase().endsWith(".mp3")) safe else "$safe.mp3"
}
