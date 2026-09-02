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
import androidx.compose.material.icons.filled.MusicNote
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
import kotlinx.coroutines.launch

@Composable
fun OnlineMusicScreen(viewModel: OnlineMusicViewModel) {
    val context = LocalContext.current
    val playerController = remember { AudioPlayerController.obtain(context) }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var pendingDownload by remember { mutableStateOf<PendingOnlineDownload?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val saveDownloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mpeg")) { uri: Uri? ->
        val pending = pendingDownload
        pendingDownload = null
        if (uri == null || pending == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = "جاري تنزيل ${pending.title}..."
            runCatching { viewModel.downloadTrack(pending.audioUrl, context.contentResolver, uri) }
                .onSuccess { message = "تم تنزيل الأغنية بنجاح" }
                .onFailure { message = it.message ?: "فشل تنزيل الأغنية" }
        }
    }

    LaunchedEffect(Unit) { viewModel.loadHome() }

    fun playSong(link: AlbumatyLink) {
        scope.launch {
            message = "جاري تجهيز الأغنية..."
            runCatching { viewModel.resolveTrack(link) }.onSuccess { track ->
                val audio = track.streamUrl ?: error("لا يوجد رابط صوت")
                val item = AudioItem(link.url, track.title, track.artist.ifBlank { "ألبوماتي" }, track.album ?: "Online Music", 0L, Uri.parse(audio))
                playerController.playSong(item, listOf(item))
                message = "يتم تشغيل: ${track.title}"
            }.onFailure { message = it.message ?: "تعذر تشغيل الأغنية" }
        }
    }

    fun downloadSong(link: AlbumatyLink) {
        scope.launch {
            message = "جاري تجهيز رابط التنزيل..."
            runCatching { viewModel.resolveTrack(link) }.onSuccess { track ->
                val audio = track.downloadUrl ?: track.streamUrl ?: error("لا يوجد رابط تنزيل")
                pendingDownload = PendingOnlineDownload(track.title, audio)
                saveDownloadLauncher.launch(suggestedFileName(track.title))
            }.onFailure { message = it.message ?: "تعذر تجهيز التنزيل" }
        }
    }

    viewModel.section?.let { section ->
        OnlineSectionScreen(section, viewModel.isLoading, message, viewModel::closeSection, ::playSong, ::downloadSong)
        return
    }

    val normalized = query.trim()
    val albums = viewModel.home.albums.filter { normalized.isBlank() || it.title.contains(normalized, true) }
    val songs = viewModel.home.songs.filter { normalized.isBlank() || it.title.contains(normalized, true) }
    val artists = viewModel.home.artists.filter { normalized.isBlank() || it.title.contains(normalized, true) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MusicNote, null, Modifier.size(28.dp))
            Spacer(Modifier.size(8.dp))
            Text("ألبوماتي", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.loadHome(true) }) { Icon(Icons.Filled.Refresh, "تحديث") }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp), singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("ابحث في ألبوماتي") })

        if (viewModel.isLoading && viewModel.home.albums.isEmpty() && viewModel.home.songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (viewModel.errorMessage != null && viewModel.home.albums.isEmpty() && viewModel.home.songs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.loadHome(true) }) { Text("إعادة المحاولة") }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OnlineSection("الأقسام") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(viewModel.home.categories) { link ->
                                Card(Modifier.clickable { viewModel.openSection(link) }) { Text(link.title, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), maxLines = 1) }
                            }
                        }
                    }
                }
                item { OnlineSection("جديد الألبومات") { LinkList(albums, viewModel::openSection) } }
                item { OnlineSection("جديد الأغاني") { SongList(songs, ::playSong, ::downloadSong) } }
                item { OnlineSection("الفنانين") { LinkList(artists, viewModel::openSection, limit = null) } }
                message?.let { item { Text(text = it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) } }
            }
        }
    }
}

@Composable
private fun OnlineSectionScreen(section: AlbumatySection, isLoading: Boolean, message: String?, onBack: () -> Unit, onPlay: (AlbumatyLink) -> Unit, onDownload: (AlbumatyLink) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
            Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (isLoading) CircularProgressIndicator(Modifier.size(22.dp))
        }
        when {
            section.songs.isEmpty() && isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            section.songs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا توجد أغاني متاحة في هذا القسم") }
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(section.songs) { song -> OnlineSongCard(song, onPlay, onDownload) }
                message?.let { item { Text(text = it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp)) } }
            }
        }
    }
}

@Composable
private fun LinkList(links: List<AlbumatyLink>, onOpen: (AlbumatyLink) -> Unit, limit: Int? = 24) {
    val visible = if (limit == null) links else links.take(limit)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        visible.forEach { link ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(link) }) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Filled.MusicNote, null) }
                    Spacer(Modifier.size(9.dp))
                    Text(text = link.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { onOpen(link) }) { Text("فتح") }
                }
            }
        }
    }
}

@Composable
private fun SongList(links: List<AlbumatyLink>, onPlay: (AlbumatyLink) -> Unit, onDownload: (AlbumatyLink) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { links.take(24).forEach { OnlineSongCard(it, onPlay, onDownload) } }
}

@Composable
private fun OnlineSongCard(link: AlbumatyLink, onPlay: (AlbumatyLink) -> Unit, onDownload: (AlbumatyLink) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Filled.MusicNote, null) }
            Spacer(Modifier.size(9.dp))
            Text(text = link.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { onPlay(link) }) { Icon(Icons.Filled.PlayArrow, "تشغيل") }
            IconButton(onClick = { onDownload(link) }) { Icon(Icons.Filled.Download, "تنزيل") }
        }
    }
}

@Composable
private fun OnlineSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); content() }
}

private data class PendingOnlineDownload(val title: String, val audioUrl: String)
private fun suggestedFileName(title: String): String {
    val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "online_music" }
    return if (safe.lowercase().endsWith(".mp3")) safe else "$safe.mp3"
}
