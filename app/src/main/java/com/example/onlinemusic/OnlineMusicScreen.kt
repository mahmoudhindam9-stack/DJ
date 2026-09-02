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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AudioItem
import com.example.player.AudioPlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun OnlineMusicScreen(viewModel: OnlineMusicViewModel) {
    val context = LocalContext.current
    val playerController = remember { AudioPlayerController.obtain(context) }
    val scope = rememberCoroutineScope()
    var foreign by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !foreign, onClick = { foreign = false }, label = { Text("🇦🇪 عربي") }, modifier = Modifier.weight(1f))
            FilterChip(selected = foreign, onClick = { foreign = true }, label = { Text("🌎 أجنبي") }, modifier = Modifier.weight(1f))
        }
        if (foreign) AudiusOnlineScreen(viewModel, playerController, scope) else AlbumatyOnlineScreen(viewModel, playerController, scope)
    }
}

@Composable
private fun AlbumatyOnlineScreen(viewModel: OnlineMusicViewModel, playerController: AudioPlayerController, scope: CoroutineScope) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var pendingDownload by remember { mutableStateOf<PendingOnlineDownload?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val saveDownloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mpeg")) { uri: Uri? ->
        val pending = pendingDownload; pendingDownload = null
        if (uri == null || pending == null) return@rememberLauncherForActivityResult
        scope.launch { message = "جاري تنزيل ${pending.title}..."; runCatching { viewModel.downloadTrack(pending.audioUrl, context.contentResolver, uri) }.onSuccess { message = "تم تنزيل الأغنية بنجاح" }.onFailure { message = it.message ?: "فشل تنزيل الأغنية" } }
    }
    LaunchedEffect(Unit) { viewModel.loadHome() }
    fun playSong(link: AlbumatyLink) {
        scope.launch {
            val same = playerController.currentSong?.id == link.url
            if (same) { playerController.togglePlayPause(); return@launch }
            message = "جاري تجهيز الأغنية..."
            runCatching { viewModel.resolveTrack(link) }.onSuccess { track ->
                val audio = track.streamUrl ?: error("لا يوجد رابط صوت")
                val item = AudioItem(link.url, track.title, track.artist.ifBlank { "ألبوماتي" }, track.album ?: "Online Music", 0L, Uri.parse(audio))
                playerController.playSong(item, listOf(item)); message = "يتم تشغيل: ${track.title}"
            }.onFailure { message = it.message ?: "تعذر تشغيل الأغنية" }
        }
    }
    fun downloadSong(link: AlbumatyLink) {
        scope.launch {
            message = "جاري تجهيز رابط التنزيل..."
            runCatching { viewModel.resolveTrack(link) }.onSuccess { track ->
                val audio = track.downloadUrl ?: track.streamUrl ?: error("لا يوجد رابط تنزيل")
                pendingDownload = PendingOnlineDownload(track.title, audio); saveDownloadLauncher.launch(suggestedFileName(track.title))
            }.onFailure { message = it.message ?: "تعذر تجهيز التنزيل" }
        }
    }
    fun activate(link: AlbumatyLink) { if (link.isSong()) playSong(link) else viewModel.openSection(link) }
    viewModel.section?.let { section ->
        OnlineSectionScreen(section, viewModel.isLoading, viewModel.errorMessage, message, viewModel::closeSection, ::activate, ::playSong, ::downloadSong, playerController)
        return
    }
    val normalized = query.trim()
    val albums = viewModel.home.albums.filter { normalized.isBlank() || it.title.contains(normalized, true) }
    val songs = viewModel.home.songs.filter { normalized.isBlank() || it.title.contains(normalized, true) }
    val artists = viewModel.home.artists.filter { normalized.isBlank() || it.title.contains(normalized, true) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MusicNote, null, Modifier.size(28.dp)); Spacer(Modifier.size(8.dp)); Text("ألبوماتي", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); IconButton(onClick = { viewModel.loadHome(true) }) { Icon(Icons.Filled.Refresh, "تحديث") }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp), singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("ابحث في ألبوماتي") })
        if (viewModel.isLoading && viewModel.home.albums.isEmpty() && viewModel.home.songs.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (viewModel.errorMessage != null && viewModel.home.albums.isEmpty() && viewModel.home.songs.isEmpty()) Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.error); TextButton(onClick = { viewModel.loadHome(true) }) { Text("إعادة المحاولة") } } }
        else LazyColumn(Modifier.fillMaxSize().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { OnlineSection("الأقسام") { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(viewModel.home.categories) { link -> Card(Modifier.clickable { activate(link) }) { Text(link.title, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), maxLines = 1) } } } } }
            item { OnlineSection("جديد الألبومات") { LinkList(albums, ::activate, playerController) } }
            item { OnlineSection("جديد الأغاني") { SongList(songs, ::playSong, ::downloadSong, playerController) } }
            item { OnlineSection("الفنانين") { LinkList(artists, ::activate, playerController, null) } }
            message?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) } }
        }
    }
}

@Composable
private fun AudiusOnlineScreen(viewModel: OnlineMusicViewModel, playerController: AudioPlayerController, scope: CoroutineScope) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var pendingDownload by remember { mutableStateOf<PendingOnlineDownload?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val saveDownloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mpeg")) { uri: Uri? ->
        val pending = pendingDownload; pendingDownload = null
        if (uri == null || pending == null) return@rememberLauncherForActivityResult
        scope.launch { message = "جاري تنزيل ${pending.title}..."; runCatching { viewModel.downloadAudiusTrack(pending.audioUrl, context.contentResolver, uri) }.onSuccess { message = "تم تنزيل الأغنية بنجاح" }.onFailure { message = it.message ?: "فشل تنزيل الأغنية" } }
    }
    LaunchedEffect(Unit) { viewModel.loadAudiusHome() }
    fun playSong(track: AudiusTrack) {
        scope.launch {
            val same = playerController.currentSong?.id == "audius:${track.id}"
            if (same) { playerController.togglePlayPause(); return@launch }
            message = "جاري تجهيز الأغنية..."
            runCatching { viewModel.resolveAudiusTrack(track) }.onSuccess { resolved ->
                val audio = resolved.streamUrl ?: error("الأغنية غير قابلة للتشغيل")
                val item = AudioItem("audius:${track.id}", resolved.title, resolved.artist, resolved.album ?: "Audius", 0L, Uri.parse(audio))
                playerController.playSong(item, listOf(item)); message = "يتم تشغيل: ${resolved.title}"
            }.onFailure { message = it.message ?: "تعذر تشغيل الأغنية" }
        }
    }
    fun downloadSong(track: AudiusTrack) {
        scope.launch {
            message = "جاري تجهيز رابط التنزيل..."
            runCatching { viewModel.resolveAudiusTrack(track) }.onSuccess { resolved ->
                val audio = resolved.downloadUrl ?: resolved.streamUrl ?: error("هذه الأغنية لا تسمح بالتنزيل")
                pendingDownload = PendingOnlineDownload(resolved.title, audio); saveDownloadLauncher.launch(suggestedFileName(resolved.title))
            }.onFailure { message = it.message ?: "تعذر تجهيز التنزيل" }
        }
    }
    viewModel.audiusArtistDetail?.let { detail -> AudiusDetailScreen(detail.artist.name, "Artist", detail.tracks, viewModel.isLoading, viewModel.errorMessage, viewModel::closeAudiusDetail, ::playSong, ::downloadSong, playerController); return }
    viewModel.audiusGenreDetail?.let { (genre, tracks) -> AudiusDetailScreen(genre, "Genre", tracks, viewModel.isLoading, viewModel.errorMessage, viewModel::closeAudiusDetail, ::playSong, ::downloadSong, playerController); return }
    val searchResults = viewModel.audiusSearchResults
    val latest = if (query.isBlank()) viewModel.audiusHome.latest else searchResults
    val trending = if (query.isBlank()) viewModel.audiusHome.trending else searchResults
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.MusicNote, null, Modifier.size(28.dp)); Spacer(Modifier.size(8.dp)); Text("أجنبي • Audius", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); IconButton(onClick = { viewModel.loadAudiusHome(true) }) { Icon(Icons.Filled.Refresh, "تحديث") } }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp), singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Search foreign music") })
        LaunchedEffect(query) { if (query.trim().length >= 2) { kotlinx.coroutines.delay(350); viewModel.searchAudius(query.trim()) } else if (query.isBlank()) viewModel.clearAudiusSearch() }
        if (viewModel.isLoading && viewModel.audiusHome.trending.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (viewModel.errorMessage != null && viewModel.audiusHome.trending.isEmpty()) Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.error); TextButton(onClick = { viewModel.loadAudiusHome(true) }) { Text("إعادة المحاولة") } } }
        else LazyColumn(Modifier.fillMaxSize().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (query.isBlank()) {
                item { OnlineSection("الأنواع") { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(viewModel.audiusHome.genres) { genre -> Card(Modifier.clickable { viewModel.openAudiusGenre(genre) }) { Text(genre, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), maxLines = 1) } } } } }
                item { OnlineSection("الفنانين") { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(viewModel.audiusHome.artists) { artist -> Card(Modifier.width(170.dp).clickable { viewModel.openAudiusArtist(artist) }) { Column(Modifier.padding(12.dp)) { Icon(Icons.Filled.MusicNote, null, Modifier.size(30.dp)); Spacer(Modifier.height(6.dp)); Text(artist.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) } } } } } }
                item { OnlineSection("الأكثر رواجًا") { AudiusSongList(trending, ::playSong, ::downloadSong, playerController) } }
            }
            item { OnlineSection(if (query.isBlank()) "أحدث الأغاني" else "نتائج البحث") { AudiusSongList(latest, ::playSong, ::downloadSong, playerController) } }
            message?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) } }
        }
    }
}

@Composable
private fun AudiusDetailScreen(title: String, subtitle: String, tracks: List<AudiusTrack>, isLoading: Boolean, errorMessage: String?, onBack: () -> Unit, onPlay: (AudiusTrack) -> Unit, onDownload: (AudiusTrack) -> Unit, playerController: AudioPlayerController) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }; Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(subtitle, style = MaterialTheme.typography.bodySmall) }; if (isLoading) CircularProgressIndicator(Modifier.size(22.dp)) }
        when { tracks.isEmpty() && isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; tracks.isEmpty() && errorMessage != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(errorMessage, color = MaterialTheme.colorScheme.error) }; tracks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا توجد أغاني متاحة") }; else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { items(tracks, key = { it.id }) { AudiusSongCard(it, onPlay, onDownload, playerController) } } }
    }
}

@Composable private fun AudiusSongList(tracks: List<AudiusTrack>, onPlay: (AudiusTrack) -> Unit, onDownload: (AudiusTrack) -> Unit, playerController: AudioPlayerController) { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { tracks.take(50).forEach { AudiusSongCard(it, onPlay, onDownload, playerController) } } }

@Composable private fun AudiusSongCard(track: AudiusTrack, onPlay: (AudiusTrack) -> Unit, onDownload: (AudiusTrack) -> Unit, playerController: AudioPlayerController) {
    val active = playerController.currentSong?.id == "audius:${track.id}"; val playing = active && playerController.isPlaying
    Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Filled.MusicNote, null) }; Spacer(Modifier.size(9.dp)); Column(Modifier.weight(1f)) { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold); Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }; IconButton(onClick = { onPlay(track) }, enabled = track.streamable) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "إيقاف مؤقت" else "تشغيل") }; IconButton(onClick = { onDownload(track) }) { Icon(Icons.Filled.Download, "تنزيل") } } }
}

@Composable
private fun OnlineSectionScreen(section: AlbumatySection, isLoading: Boolean, errorMessage: String?, message: String?, onBack: () -> Unit, onOpen: (AlbumatyLink) -> Unit, onPlay: (AlbumatyLink) -> Unit, onDownload: (AlbumatyLink) -> Unit, playerController: AudioPlayerController) {
    Column(Modifier.fillMaxSize()) { Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }; Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); if (isLoading) CircularProgressIndicator(Modifier.size(22.dp)) }; val content = section.content; when { content.isEmpty() && isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; content.isEmpty() && errorMessage != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(errorMessage, color = MaterialTheme.colorScheme.error) }; content.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا يوجد محتوى متاح في هذا القسم") }; else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { items(content, key = { it.url }) { link -> if (link.isSong()) OnlineSongCard(link, onPlay, onDownload, playerController) else SectionLinkCard(link, onOpen) }; message?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp)) } } } } }
}

@Composable private fun SectionLinkCard(link: AlbumatyLink, onOpen: (AlbumatyLink) -> Unit) { Card(Modifier.fillMaxWidth().clickable { onOpen(link) }) { Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Folder, null) }; Spacer(Modifier.size(9.dp)); Text(link.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold); TextButton(onClick = { onOpen(link) }) { Text("فتح") } } } }
@Composable private fun LinkList(links: List<AlbumatyLink>, onOpen: (AlbumatyLink) -> Unit, playerController: AudioPlayerController, limit: Int? = 24) { val visible = if (limit == null) links else links.take(limit); Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { visible.forEach { SectionLinkCard(it, onOpen) } } }
@Composable private fun SongList(links: List<AlbumatyLink>, onPlay: (AlbumatyLink) -> Unit, onDownload: (AlbumatyLink) -> Unit, playerController: AudioPlayerController) { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { links.take(24).forEach { OnlineSongCard(it, onPlay, onDownload, playerController) } } }
@Composable private fun OnlineSongCard(link: AlbumatyLink, onPlay: (AlbumatyLink) -> Unit, onDownload: (AlbumatyLink) -> Unit, playerController: AudioPlayerController) { val active = playerController.currentSong?.id == link.url; val playing = active && playerController.isPlaying; Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Filled.MusicNote, null) }; Spacer(Modifier.size(9.dp)); Text(link.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold); IconButton(onClick = { onPlay(link) }) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "إيقاف مؤقت" else "تشغيل") }; IconButton(onClick = { onDownload(link) }) { Icon(Icons.Filled.Download, "تنزيل") } } } }
@Composable private fun OnlineSection(title: String, content: @Composable () -> Unit) { Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); content() } }
private fun AlbumatyLink.isSong(): Boolean = runCatching { java.net.URI(url).path.orEmpty().trim('/').lowercase().split('/').any { it == "song" || it.startsWith("song") } }.getOrDefault(false)
private data class PendingOnlineDownload(val title: String, val audioUrl: String)
private fun suggestedFileName(title: String): String { val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "online_music" }; return if (safe.lowercase().endsWith(".mp3")) safe else "$safe.mp3" }
