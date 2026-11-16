package com.example.onlinemusic

import androidx.compose.runtime.saveable.rememberSaveable

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioItem
import com.example.model.Playlist
import com.example.QueueSheet
import com.example.room.PlaylistRepository
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.player.AudioPlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun OnlineMusicScreen(
    viewModel: OnlineMusicViewModel,
    playerController: AudioPlayerController,
    playlists: List<Playlist>,
    audioLibrary: SnapshotStateList<AudioItem>,
    playlistRepo: PlaylistRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var foreign by rememberSaveable { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !foreign, onClick = { foreign = false }, label = { Text("🇦🇪 Arabic") }, modifier = Modifier.weight(1f))
            FilterChip(selected = foreign, onClick = { foreign = true }, label = { Text("🌎 Foreign") }, modifier = Modifier.weight(1f))
        }
        if (foreign) AudiusOnlineScreen(viewModel, playerController, scope) { showQueue = true } else AlbumatyOnlineScreen(viewModel, playerController, scope) { showQueue = true }
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
private fun AlbumatyOnlineScreen(viewModel: OnlineMusicViewModel, playerController: AudioPlayerController, scope: CoroutineScope, onShowQueue: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var pendingDownload by remember { mutableStateOf<PendingOnlineDownload?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val saveDownloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mpeg")) { uri: Uri? ->
        val pending = pendingDownload; pendingDownload = null
        if (uri == null || pending == null) return@rememberLauncherForActivityResult
        scope.launch { message = "Downloading ${pending.title}..."; runCatching { viewModel.downloadTrack(pending.audioUrl, context.contentResolver, uri) }.onSuccess { message = "Song downloaded successfully" }.onFailure { message = it.message ?: "Failed to download song" } }
    }
    LaunchedEffect(Unit) { viewModel.loadHome() }
    fun playSong(link: AlbumatyLink) {
        scope.launch {
            val same = playerController.currentSong?.id == link.url
            if (same) { playerController.togglePlayPause(); return@launch }
            message = "Preparing song..."
            runCatching { viewModel.resolveTrack(link) }.onSuccess { track ->
                val audio = track.streamUrl ?: error("No audio link")
                val item = AudioItem(link.url, track.title, track.artist.ifBlank { "Albumaty" }, track.album ?: "Online Music", 0L, Uri.parse(audio))
                playerController.play(item, listOf(item)); message = "Playing: ${track.title}"
            }.onFailure { message = it.message ?: "Failed to play song" }
        }
    }
    fun downloadSong(link: AlbumatyLink) {
        scope.launch {
            message = "Preparing download link..."
            runCatching { viewModel.resolveTrack(link) }.onSuccess { track ->
                val audio = track.downloadUrl ?: track.streamUrl ?: error("No download link")
                pendingDownload = PendingOnlineDownload(track.title, audio); saveDownloadLauncher.launch(suggestedFileName(track.title))
            }.onFailure { message = it.message ?: "Failed to prepare download" }
        }
    }
    fun queueSong(link: AlbumatyLink) { scope.launch { runCatching { viewModel.resolveTrack(link) }.onSuccess { track -> val audio=track.streamUrl ?: error("No audio link"); playerController.enqueueOnlineSong(AudioItem(link.url, track.title, track.artist.ifBlank { "Albumaty" }, track.album ?: "Online Music", 0L, Uri.parse(audio))); message="Added ${track.title} to Playlist" }.onFailure { message=it.message ?: "Failed to add song to Playlist" } } }
    fun sendToDeck(link: AlbumatyLink, deck: OnlineDeckTarget) { scope.launch { runCatching { viewModel.resolveTrack(link) }.onSuccess { track -> val audio=track.streamUrl ?: error("No audio link"); OnlineDjBridge.send(AudioItem(link.url, track.title, track.artist.ifBlank { "Albumaty" }, track.album ?: "Online Music", 0L, Uri.parse(audio)), deck); message="Sent ${track.title} to Deck ${deck.name}" }.onFailure { message=it.message ?: "Failed to send song to DJ" } } }
    fun activate(link: AlbumatyLink) { if (link.isSong()) playSong(link) else viewModel.openSection(link) }
    viewModel.section?.let { section ->
        OnlineSectionScreen(section, viewModel.isLoading, viewModel.errorMessage, message, viewModel::closeSection, ::activate, ::playSong, ::downloadSong, ::queueSong, ::sendToDeck, playerController)
        return
    }
    val normalized = query.trim()
    val albums = viewModel.home.albums.filter { normalized.isBlank() || it.title.contains(normalized, true) }
    val songs = viewModel.home.songs.filter { normalized.isBlank() || it.title.contains(normalized, true) }
    val artists = viewModel.home.artists.filter { normalized.isBlank() || it.title.contains(normalized, true) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MusicNote, null, Modifier.size(28.dp)); Spacer(Modifier.size(8.dp)); Text("Albumaty", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); IconButton(onClick = { viewModel.loadHome(true) }) { Icon(Icons.Filled.Refresh, "Refresh") }; IconButton(onClick = onShowQueue) { Icon(Icons.Filled.QueueMusic, "Queue") }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp), singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("ابحث في Albumaty") })
        if (viewModel.isLoading && viewModel.home.albums.isEmpty() && viewModel.home.songs.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (viewModel.errorMessage != null && viewModel.home.albums.isEmpty() && viewModel.home.songs.isEmpty()) Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(viewModel.errorMessage ?: "Unknown error", color = MaterialTheme.colorScheme.error); TextButton(onClick = { viewModel.loadHome(true) }) { Text("Retry") } } }
        else LazyColumn(Modifier.fillMaxSize().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { OnlineSection("Sections") { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(viewModel.home.categories) { link -> Card(Modifier.clickable { activate(link) }) { Text(link.title, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), maxLines = 1) } } } } }
            item { OnlineSection("New Albums") { LinkList(albums, ::activate, playerController) } }
            item { OnlineSection("New Songs") { SongList(songs, ::playSong, ::downloadSong, ::queueSong, ::sendToDeck, playerController) } }
            item { OnlineSection("Artists") { LinkList(artists, ::activate, playerController, null) } }
            message?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) } }
        }
    }
}

@Composable
private fun AudiusOnlineScreen(viewModel: OnlineMusicViewModel, playerController: AudioPlayerController, scope: CoroutineScope, onShowQueue: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var pendingDownload by remember { mutableStateOf<PendingOnlineDownload?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val saveDownloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mpeg")) { uri: Uri? ->
        val pending = pendingDownload; pendingDownload = null
        if (uri == null || pending == null) return@rememberLauncherForActivityResult
        scope.launch { message = "Downloading ${pending.title}..."; runCatching { viewModel.downloadAudiusTrack(pending.audioUrl, context.contentResolver, uri) }.onSuccess { message = "Song downloaded successfully" }.onFailure { message = it.message ?: "Failed to download song" } }
    }
    LaunchedEffect(Unit) { viewModel.loadAudiusHome() }
    fun playSong(track: AudiusTrack) {
        scope.launch {
            val same = playerController.currentSong?.id == "audius:${track.id}"
            if (same) { playerController.togglePlayPause(); return@launch }
            message = "Preparing song..."
            runCatching { viewModel.resolveAudiusTrack(track) }.onSuccess { resolved ->
                val audio = resolved.streamUrl ?: error("Song cannot be played")
                val item = AudioItem("audius:${track.id}", resolved.title, resolved.artist, resolved.album ?: "Audius", 0L, Uri.parse(audio))
                playerController.play(item, listOf(item)); message = "Playing: ${resolved.title}"
            }.onFailure { message = it.message ?: "Failed to play song" }
        }
    }
    fun downloadSong(track: AudiusTrack) {
        scope.launch {
            message = "Preparing download link..."
            runCatching { viewModel.resolveAudiusTrack(track) }.onSuccess { resolved ->
                val audio = resolved.downloadUrl ?: resolved.streamUrl ?: error("This song cannot be downloaded")
                pendingDownload = PendingOnlineDownload(resolved.title, audio); saveDownloadLauncher.launch(suggestedFileName(resolved.title))
            }.onFailure { message = it.message ?: "Failed to prepare download" }
        }
    }
    fun queueSong(track: AudiusTrack) { scope.launch { runCatching { viewModel.resolveAudiusTrack(track) }.onSuccess { resolved -> val audio=resolved.streamUrl ?: error("Song cannot be played"); playerController.enqueueOnlineSong(AudioItem("audius:${track.id}", resolved.title, resolved.artist, resolved.album ?: "Audius", 0L, Uri.parse(audio))); message="Added ${resolved.title} to Playlist" }.onFailure { message=it.message ?: "Failed to add song to Playlist" } } }
    fun sendToDeck(track: AudiusTrack, deck: OnlineDeckTarget) { scope.launch { runCatching { viewModel.resolveAudiusTrack(track) }.onSuccess { resolved -> val audio=resolved.streamUrl ?: error("Song cannot be played"); OnlineDjBridge.send(AudioItem("audius:${track.id}", resolved.title, resolved.artist, resolved.album ?: "Audius", 0L, Uri.parse(audio)), deck); message="Sent ${resolved.title} to Deck ${deck.name}" }.onFailure { message=it.message ?: "Failed to send song to DJ" } } }
    viewModel.audiusArtistDetail?.let { detail -> AudiusDetailScreen(detail.artist.name, "Artist", detail.tracks, viewModel.isLoading, viewModel.errorMessage, viewModel::closeAudiusDetail, ::playSong, ::downloadSong, ::queueSong, ::sendToDeck, playerController); return }
    viewModel.audiusGenreDetail?.let { (genre, tracks) -> AudiusDetailScreen(genre, "Genre", tracks, viewModel.isLoading, viewModel.errorMessage, viewModel::closeAudiusDetail, ::playSong, ::downloadSong, ::queueSong, ::sendToDeck, playerController); return }
    val searchResults = viewModel.audiusSearchResults
    val latest = if (query.isBlank()) viewModel.audiusHome.latest else searchResults
    val trending = if (query.isBlank()) viewModel.audiusHome.trending else searchResults
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.MusicNote, null, Modifier.size(28.dp)); Spacer(Modifier.size(8.dp)); Text("Foreign • Audius", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); IconButton(onClick = { viewModel.loadAudiusHome(true) }) { Icon(Icons.Filled.Refresh, "Refresh") }; IconButton(onClick = onShowQueue) { Icon(Icons.Filled.QueueMusic, "Queue") } }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp), singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Search foreign music") })
        LaunchedEffect(query) { if (query.trim().length >= 2) { kotlinx.coroutines.delay(350); viewModel.searchAudius(query.trim()) } else if (query.isBlank()) viewModel.clearAudiusSearch() }
        if (viewModel.isLoading && viewModel.audiusHome.trending.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (viewModel.errorMessage != null && viewModel.audiusHome.trending.isEmpty()) Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(viewModel.errorMessage ?: "Unknown error", color = MaterialTheme.colorScheme.error); TextButton(onClick = { viewModel.loadAudiusHome(true) }) { Text("Retry") } } }
        else LazyColumn(Modifier.fillMaxSize().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (query.isBlank()) {
                item { OnlineSection("Genres") { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(viewModel.audiusHome.genres) { genre -> Card(Modifier.clickable { viewModel.openAudiusGenre(genre) }) { Text(genre, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), maxLines = 1) } } } } }
                item { OnlineSection("Artists") { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(viewModel.audiusHome.artists) { artist -> Card(Modifier.width(170.dp).clickable { viewModel.openAudiusArtist(artist) }) { Column(Modifier.padding(12.dp)) { Icon(Icons.Filled.MusicNote, null, Modifier.size(30.dp)); Spacer(Modifier.height(6.dp)); Text(artist.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) } } } } } }
                item { OnlineSection("Trending") { AudiusSongList(trending, ::playSong, ::downloadSong, ::queueSong, ::sendToDeck, playerController) } }
            }
            item { OnlineSection(if (query.isBlank()) "Latest Songs" else "Search Results") { AudiusSongList(latest, ::playSong, ::downloadSong, ::queueSong, ::sendToDeck, playerController) } }
            message?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) } }
        }
    }
}

@Composable
private fun AudiusDetailScreen(title: String, subtitle: String, tracks: List<AudiusTrack>, isLoading: Boolean, errorMessage: String?, onBack: () -> Unit, onPlay: (AudiusTrack) -> Unit, onDownload: (AudiusTrack) -> Unit, onQueue: (AudiusTrack) -> Unit, onDeck: (AudiusTrack, OnlineDeckTarget) -> Unit, playerController: AudioPlayerController) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(subtitle, style = MaterialTheme.typography.bodySmall) }; if (isLoading) CircularProgressIndicator(Modifier.size(22.dp)) }
        when { tracks.isEmpty() && isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; tracks.isEmpty() && errorMessage != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(errorMessage, color = MaterialTheme.colorScheme.error) }; tracks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No songs available") }; else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { items(tracks, key = { it.id }) { AudiusSongCard(it, onPlay, onDownload, onQueue, onDeck, playerController) } } }
    }
}

@Composable private fun AudiusSongList(tracks: List<AudiusTrack>, onPlay: (AudiusTrack) -> Unit, onDownload: (AudiusTrack) -> Unit, onQueue: (AudiusTrack) -> Unit, onDeck: (AudiusTrack, OnlineDeckTarget) -> Unit, playerController: AudioPlayerController) { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { tracks.take(50).forEach { AudiusSongCard(it, onPlay, onDownload, onQueue, onDeck, playerController) } } }

@Composable private fun AudiusSongCard(track: AudiusTrack, onPlay: (AudiusTrack) -> Unit, onDownload: (AudiusTrack) -> Unit, onQueue: (AudiusTrack) -> Unit, onDeck: (AudiusTrack, OnlineDeckTarget) -> Unit, playerController: AudioPlayerController) {
    val active = playerController.currentSong?.id == "audius:${track.id}"; val playing = active && playerController.isPlaying
    com.example.ui.components.DjSurfaceCard(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Filled.MusicNote, null) }; Spacer(Modifier.size(9.dp)); Column(Modifier.weight(1f)) { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold); Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }; IconButton(modifier = Modifier, onClick = { onPlay(track) }) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play") }; IconButton(modifier = Modifier, onClick = { onQueue(track) }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist") }; OnlineDeckButton { onDeck(track, it) }; IconButton(modifier = Modifier, onClick = { onDownload(track) }) { Icon(Icons.Filled.Download, "Download") } } }
}

@Composable
private fun OnlineSectionScreen(section: AlbumatySection, isLoading: Boolean, errorMessage: String?, message: String?, onBack: () -> Unit, onOpen: (AlbumatyLink) -> Unit, onPlay: (AlbumatyLink) -> Unit, onDownload: (AlbumatyLink) -> Unit, onQueue: (AlbumatyLink) -> Unit, onDeck: (AlbumatyLink, OnlineDeckTarget) -> Unit, playerController: AudioPlayerController) {
    Column(Modifier.fillMaxSize()) { Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); if (isLoading) CircularProgressIndicator(Modifier.size(22.dp)) }; val content = section.content; when { content.isEmpty() && isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; content.isEmpty() && errorMessage != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(errorMessage, color = MaterialTheme.colorScheme.error) }; content.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No content available in this section") }; else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { items(content, key = { it.url }) { link -> if (link.isSong()) OnlineSongCard(link, onPlay, onDownload, onQueue, onDeck, playerController) else SectionLinkCard(link, onOpen) }; message?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp)) } } } } }
}

@Composable private fun SectionLinkCard(link: AlbumatyLink, onOpen: (AlbumatyLink) -> Unit) { Card(Modifier.fillMaxWidth().clickable { onOpen(link) }) { Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Folder, null) }; Spacer(Modifier.size(9.dp)); Text(link.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold); TextButton(onClick = { onOpen(link) }) { Text("Open") } } } }
@Composable private fun LinkList(links: List<AlbumatyLink>, onOpen: (AlbumatyLink) -> Unit, playerController: AudioPlayerController, limit: Int? = 24) { val visible = if (limit == null) links else links.take(limit); Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { visible.forEach { SectionLinkCard(it, onOpen) } } }
@Composable private fun SongList(links: List<AlbumatyLink>, onPlay: (AlbumatyLink) -> Unit, onDownload: (AlbumatyLink) -> Unit, onQueue: (AlbumatyLink) -> Unit, onDeck: (AlbumatyLink, OnlineDeckTarget) -> Unit, playerController: AudioPlayerController) { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { links.take(24).forEach { OnlineSongCard(it, onPlay, onDownload, onQueue, onDeck, playerController) } } }
@Composable private fun OnlineSongCard(link: AlbumatyLink, onPlay: (AlbumatyLink) -> Unit, onDownload: (AlbumatyLink) -> Unit, onQueue: (AlbumatyLink) -> Unit, onDeck: (AlbumatyLink, OnlineDeckTarget) -> Unit, playerController: AudioPlayerController) { val active = playerController.currentSong?.id == link.url; val playing = active && playerController.isPlaying; com.example.ui.components.DjSurfaceCard(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Filled.MusicNote, null) }; Spacer(Modifier.size(9.dp)); Text(link.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold); IconButton(modifier = Modifier, onClick = { onPlay(link) }) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play") }; IconButton(modifier = Modifier, onClick = { onQueue(link) }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist") }; OnlineDeckButton { onDeck(link, it) }; IconButton(modifier = Modifier, onClick = { onDownload(link) }) { Icon(Icons.Filled.Download, "Download") } } } }
@Composable private fun OnlineSection(title: String, content: @Composable () -> Unit) { Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); content() } }
private fun AlbumatyLink.isSong(): Boolean = runCatching { java.net.URI(url).path.orEmpty().trim('/').lowercase().split('/').any { it == "song" || it.startsWith("song") } }.getOrDefault(false)
enum class OnlineDeckTarget { A, B }
@Composable private fun OnlineDeckButton(onSelected: (OnlineDeckTarget) -> Unit) { var showPicker by remember { mutableStateOf(false) }; IconButton(onClick = { showPicker=true }) { Icon(Icons.Filled.Headset, "Send to DJ Deck") }; if(showPicker) AlertDialog(onDismissRequest={showPicker=false}, title={Text("Send song to which Deck?")}, text={Text("Select A or B")}, confirmButton={Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton(onClick={showPicker=false;onSelected(OnlineDeckTarget.A)}){Text("A")};TextButton(onClick={showPicker=false;onSelected(OnlineDeckTarget.B)}){Text("B")}}}) }
private data class PendingOnlineDownload(val title: String, val audioUrl: String)
private fun suggestedFileName(title: String): String { val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "online_music" }; return if (safe.lowercase().endsWith(".mp3")) safe else "$safe.mp3" }
