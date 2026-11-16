package com.example
import androidx.core.content.ContextCompat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import com.example.model.AudioItem
import com.example.model.Playlist
import com.example.player.AudioPlayerController
import com.example.player.RepeatOption
import com.example.room.AppDatabase
import com.example.room.PlaylistEntity
import com.example.room.PlaylistRepository
import com.example.utils.MusicScanner
import com.example.updater.GitHubUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.Locale
import java.text.Normalizer

private fun addToLibrary(library: SnapshotStateList<AudioItem>, songs: List<AudioItem>, context: Context) {
    val existing = library.map { it.uri }.toHashSet()
    songs.forEach { song ->
        if (existing.add(song.uri)) library.add(song)
    }
}
private fun normalizeSmartSearch(value: String): String {
    val withoutMarks = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return withoutMarks.lowercase(Locale.ROOT)
        .replace("أ", "ا")
        .replace("إ", "ا")
        .replace("آ", "ا")
        .replace("ٱ", "ا")
        .replace("ى", "ي")
        .replace("ة", "ه")
        .replace("ؤ", "و")
        .replace("ئ", "ي")
        .replace("ـ", "")
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun smartTrackMatches(song: AudioItem, query: String): Boolean {
    val normalizedQuery = normalizeSmartSearch(query)
    if (normalizedQuery.isBlank()) return true
    val haystack = normalizeSmartSearch(
        listOf(song.title, song.artist, song.album, song.uri.toString()).joinToString(" ")
    )
    return normalizedQuery.split(" ")
        .filter { it.isNotBlank() }
        .all { token -> haystack.contains(token) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreenV2(
    playerController: AudioPlayerController,
    audioLibrary: SnapshotStateList<AudioItem>,
    playlists: List<Playlist>,
    onPauseDJ: () -> Unit,
    navController: NavHostController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { PlaylistRepository(AppDatabase.getDatabase(context).playlistDao()) }
    var showNowPlaying by remember { mutableStateOf(playerController.currentSong != null) }
    var showQueue by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var addSongToPlaylist by remember { mutableStateOf<AudioItem?>(null) }
    var addSongsPlaylistId by remember { mutableStateOf<String?>(null) }
    var addFolderPlaylistId by remember { mutableStateOf<String?>(null) }
    var showMixPlaylists by remember { mutableStateOf(false) }
    var showLibraryMenu by remember { mutableStateOf(false) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    
    
     

    LaunchedEffect(playerController.currentSong?.id) {
        if (playerController.currentSong != null) showNowPlaying = true
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val songs = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    runCatching { MusicScanner.parsePickedUri(context, uri) }.getOrNull()
                }
            }
            addToLibrary(audioLibrary, songs, context)
            infoMessage = "Added ${songs.size} song(s)"
        }
    }

    var showMusicImport by remember { mutableStateOf(false) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val target = addFolderPlaylistId
        addFolderPlaylistId = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val songs = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                FolderAudioImporter.scan(context, uri)
            }
            addToLibrary(audioLibrary, songs, context)
            if (target != null) {
                playlists.firstOrNull { it.id == target }?.let { playlist ->
                    repo.updateSongs(playlist.id, (playlist.songIds + songs.map { it.id }).distinct().joinToString(","))
                }
            }
            infoMessage = "Added ${songs.size} song(s) from folder"
        }
    }

    fun runDeviceScan() {
        scope.launch {
            val songs = withContext(Dispatchers.IO) { MusicScanner.scanMediaStoreAudio(context) }
            addToLibrary(audioLibrary, songs, context)
            infoMessage = "Scanned ${songs.size} device song(s)"
        }
    }

    val scanPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runDeviceScan()
        else infoMessage = "Storage permission denied; device music was not scanned"
    }

    val scanDevice = {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            runDeviceScan()
        } else {
            scanPermissionLauncher.launch(permission)
        }
    }

    if (showNowPlaying && playerController.currentSong != null) {
        NowPlayingFullScreenV2(playerController, { showNowPlaying = false }, { showQueue = true }, onPauseDJ)
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MUSIC LIBRARY", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Local audio and playlists", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                var showThemeMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showThemeMenu = true }, modifier = Modifier) {
                        Icon(androidx.compose.material.icons.Icons.Filled.Palette, contentDescription = "Themes")
                    }
                    DropdownMenu(expanded = showThemeMenu, onDismissRequest = { showThemeMenu = false }) {
                        com.example.ui.theme.AppThemeOption.values().forEach { themeOpt ->
                            DropdownMenuItem(
                                text = { Text(themeOpt.displayName) },
                                onClick = {
                                    showThemeMenu = false
                                    com.example.ui.theme.ThemeManager.setTheme(context, themeOpt)
                                }
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { showLibraryMenu = true }, modifier = Modifier) { Icon(Icons.Filled.MoreVert, "Library menu") }
                    DropdownMenu(expanded = showLibraryMenu, onDismissRequest = { showLibraryMenu = false }) {
                    DropdownMenuItem(modifier = Modifier, text = { Text("Scan device music") }, onClick = { showLibraryMenu = false; scanDevice() }, leadingIcon = { Icon(Icons.Filled.LibraryMusic, null) })
                    DropdownMenuItem(modifier = Modifier, text = { Text("Add audio files") }, onClick = { showLibraryMenu = false; filePicker.launch(arrayOf("audio/*")) }, leadingIcon = { Icon(Icons.Filled.Add, null) })
                    DropdownMenuItem(modifier = Modifier, text = { Text("Import Music") }, onClick = { showLibraryMenu = false; showMusicImport = true }, leadingIcon = { Icon(Icons.Filled.Folder, null) })
                    DropdownMenuItem(
                        modifier = Modifier,
                        text = { Text("Check for updates") },
                        onClick = {
                            showLibraryMenu = false
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                                    val version = pInfo.versionName ?: "1.0"
                                    GitHubUpdater.checkForUpdates(context, version, showToast = true)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        leadingIcon = { Icon(Icons.Filled.SystemUpdate, null) }
                    )
                    }
                }
            }
            infoMessage?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showCreatePlaylist = true }, Modifier.weight(1f)) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(5.dp)); Text("New playlist") }
                OutlinedButton(onClick = { showMixPlaylists = true }, Modifier.weight(1f), enabled = playlists.size >= 2) { Icon(Icons.Filled.Shuffle, null); Spacer(Modifier.width(5.dp)); Text("Mix playlists") }
            }
            Spacer(Modifier.height(10.dp))
            var trackSearchQuery by rememberSaveable { mutableStateOf("") }
            val visibleTracks = remember(audioLibrary.toList(), trackSearchQuery) {
                audioLibrary.filter { smartTrackMatches(it, trackSearchQuery) }
            }

            OutlinedTextField(
                value = trackSearchQuery,
                onValueChange = { trackSearchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (trackSearchQuery.isNotBlank()) {
                        IconButton(onClick = { trackSearchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                label = { Text("Smart Search") },
                placeholder = { Text("Song, artist, album, or location") }
            )
            Spacer(Modifier.height(5.dp))
            com.example.ui.components.DjSectionHeader("ALL TRACKS")
            if (trackSearchQuery.isNotBlank()) {
                Text(
                    "${visibleTracks.size} result(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 3.dp)
                )
            }
            Spacer(Modifier.height(5.dp))
            if (audioLibrary.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(0.58f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.LibraryMusic, null, Modifier.size(48.dp)); Spacer(Modifier.height(8.dp))
                        Text("Your library is empty", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp))
                        Button(onClick = { filePicker.launch(arrayOf("audio/*")) }) { Text("Add songs") }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(0.58f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(visibleTracks, key = { it.id }) { song ->
                        val isCurrentSong = playerController.currentSong?.id == song.id
                        val showPause = isCurrentSong && playerController.isPlaying
                        com.example.ui.components.DjSurfaceCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.MusicNote, null); Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                IconButton(onClick = { addSongToPlaylist = song }) { Icon(Icons.Filled.Add, "Add to playlist") }
                                FilledIconButton(onClick = {
                                    if (isCurrentSong) {
                                        playerController.togglePlayPause()
                                    } else {
                                        onPauseDJ()
                                        playerController.play(song, audioLibrary)
                                    }
                                    showNowPlaying = true
                                }) {
                                    Icon(
                                        if (showPause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        if (showPause) "Pause" else "Play"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            if (playlists.isNotEmpty()) {
                com.example.ui.components.DjSectionHeader("PLAYLISTS")
                Spacer(Modifier.height(5.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(0.42f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    playlists.chunked(3).forEach { rowPlaylists ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            rowPlaylists.forEach { playlist ->
                                val songs = playlist.songIds.mapNotNull { id -> audioLibrary.firstOrNull { it.id == id } }
                                Box(Modifier.weight(1f)) {
                                    com.example.ui.components.DjSurfaceCard(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, Modifier.size(25.dp))
                                            Spacer(Modifier.height(3.dp))
                                            Text(
                                                playlist.name,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Text(
                                                "${songs.size} song(s)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                                IconButton(
                                                    onClick = {
                                                        if (songs.isNotEmpty()) {
                                                            onPauseDJ()
                                                            playerController.play(songs.first(), songs)
                                                            showNowPlaying = true
                                                        }
                                                    },
                                                    modifier = Modifier.size(34.dp)
                                                ) { Icon(Icons.Filled.PlayArrow, "Play", Modifier.size(20.dp)) }
                                                IconButton(
                                                    onClick = {
                                                        if (songs.isNotEmpty()) {
                                                            onPauseDJ()
                                                            playerController.startShuffle(songs)
                                                            showNowPlaying = true
                                                        }
                                                    },
                                                    modifier = Modifier.size(34.dp)
                                                ) { Icon(Icons.Filled.Shuffle, "Shuffle", Modifier.size(20.dp)) }
                                                IconButton(
                                                    onClick = { addSongsPlaylistId = playlist.id },
                                                    modifier = Modifier.size(34.dp)
                                                ) { Icon(Icons.Filled.Add, "Add songs", Modifier.size(20.dp)) }
                                            }
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                                IconButton(
                                                    onClick = { addFolderPlaylistId = playlist.id; folderPicker.launch(null) },
                                                    modifier = Modifier.size(34.dp)
                                                ) { Icon(Icons.Filled.Folder, "Add folder", Modifier.size(20.dp)) }
                                                IconButton(
                                                    onClick = { scope.launch { repo.delete(playlist.id) } },
                                                    modifier = Modifier.size(34.dp)
                                                ) { Icon(Icons.Filled.Delete, "Delete", Modifier.size(20.dp)) }
                                            }
                                        }
                                    }
                                }
                            }
                            repeat(3 - rowPlaylists.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

        }
    }

    if (showCreatePlaylist) {
        CreatePlaylistDialog({ showCreatePlaylist = false }) { name ->
            scope.launch { repo.insert(PlaylistEntity(playlistId = UUID.randomUUID().toString(), name = name, songIdsJson = "")); showCreatePlaylist = false }
        }
    }
    addSongToPlaylist?.let { song ->
        PlaylistPickerDialog(song, playlists, { addSongToPlaylist = null }, { playlist ->
            scope.launch { repo.updateSongs(playlist.id, (playlist.songIds + song.id).distinct().joinToString(",")); addSongToPlaylist = null }
        }, { name ->
            scope.launch { repo.insert(PlaylistEntity(playlistId = UUID.randomUUID().toString(), name = name, songIdsJson = song.id)); addSongToPlaylist = null }
        })
    }
    addSongsPlaylistId?.let { playlistId ->
        playlists.firstOrNull { it.id == playlistId }?.let { playlist ->
            LibraryMultiSelectDialog(playlist, audioLibrary, { addSongsPlaylistId = null }) { ids ->
                scope.launch { repo.updateSongs(playlist.id, ids.joinToString(",")); addSongsPlaylistId = null }
            }
        }
    }
    if (showQueue) QueueSheet(playerController, playlists, audioLibrary, repo, { showQueue = false }) { song -> playerController.play(song, null); showQueue = false }
    if (showMusicImport) {
        MusicImportDialog(
            onDismiss = { showMusicImport = false },
            playlists = playlists,
            onImportToPlaylist = { items, playlistId ->
                scope.launch {
                    addToLibrary(audioLibrary, items, context)
                    playlists.firstOrNull { it.id == playlistId }?.let { playlist ->
                        repo.updateSongs(playlistId, (playlist.songIds + items.map { it.id }).distinct().joinToString(","))
                    }
                    infoMessage = "Added ${items.size} song(s)"
                }
            },
            onCreatePlaylistAndImport = { items, name ->
                scope.launch {
                    addToLibrary(audioLibrary, items, context)
                    repo.insert(com.example.room.PlaylistEntity(playlistId = java.util.UUID.randomUUID().toString(), name = name, songIdsJson = items.map { it.id }.joinToString(",")))
                    infoMessage = "Created playlist $name"
                }
            },
            onPlayNow = { items ->
                scope.launch {
                    addToLibrary(audioLibrary, items, context)
                    playerController.play(items.first(), items)
                    showNowPlaying = true
                }
            }
        )
    }

    if (showMixPlaylists) MixPlaylistsDialog(playlists, audioLibrary, { showMixPlaylists = false }) { songs, shuffle ->
        if (songs.isNotEmpty()) {
            onPauseDJ()
            if (shuffle) {
                playerController.startShuffle(songs)
            } else {
                playerController.play(songs.first(), songs)
            }
            showNowPlaying = true
        }
        showMixPlaylists = false
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create playlist") }, text = { OutlinedTextField(name, { name = it }, label = { Text("Playlist name") }, singleLine = true) }, confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onCreate(name.trim()) }) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun PlaylistPickerDialog(song: AudioItem, playlists: List<Playlist>, onDismiss: () -> Unit, onSelect: (Playlist) -> Unit, onCreatePlaylist: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add ${song.title}") }, text = { Column { if (playlists.isEmpty()) Text("No playlists yet."); playlists.forEach { p -> TextButton(onClick = { onSelect(p) }, Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth()) { Text(p.name, Modifier.weight(1f)); if (song.id in p.songIds) Icon(Icons.Filled.Check, null) } } }; OutlinedTextField(name, { name = it }, label = { Text("New playlist") }, singleLine = true); if (name.isNotBlank()) TextButton(onClick = { onCreatePlaylist(name.trim()) }) { Text("Create and add") } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
private fun LibraryMultiSelectDialog(playlist: Playlist, library: List<AudioItem>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    val selected = remember { mutableStateMapOf<String, Boolean>().also { map -> playlist.songIds.forEach { map[it] = true } } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Songs for ${playlist.name}") }, text = { LazyColumn(Modifier.heightIn(max = 500.dp)) { items(library, key = { it.id }) { song -> Row(Modifier.fillMaxWidth().clickable { selected[song.id] = selected[song.id] != true }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(selected[song.id] == true, { selected[song.id] = it }); Spacer(Modifier.width(6.dp)); Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) } } } }, confirmButton = { TextButton(onClick = { onSave(library.filter { selected[it.id] == true }.map { it.id }) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingFullScreenV2(playerController: AudioPlayerController, onBack: () -> Unit, onQueue: () -> Unit, onPauseDJ: () -> Unit) {
    val song = playerController.currentSong ?: return
    val maxPos = playerController.durationMs.coerceAtLeast(1L).toFloat()
    val current = playerController.currentPositionMs.coerceIn(0L, maxPos.toLong()).toFloat()
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; Text("NOW PLAYING", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary); IconButton(onClick = onQueue) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue") } }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().weight(0.85f).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Album, null, Modifier.size(180.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(18.dp))
        Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(song.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Slider(current, { playerController.seekTo(it.toLong()) }, valueRange = 0f..maxPos)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(MusicScanner.formatMs(playerController.currentPositionMs), style = MaterialTheme.typography.labelSmall); Text(MusicScanner.formatMs(playerController.durationMs), style = MaterialTheme.typography.labelSmall) }
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { playerController.playPrevious() }) { Icon(Icons.Filled.SkipPrevious, "Previous") }; FilledIconButton(onClick = { onPauseDJ(); playerController.togglePlayPause() }, Modifier.size(66.dp)) { Icon(if (playerController.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", Modifier.size(36.dp)) }; IconButton(onClick = { playerController.playNext() }) { Icon(Icons.Filled.SkipNext, "Next") } }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { playerController.toggleShuffle() }) { Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (playerController.isShuffle) MaterialTheme.colorScheme.primary else LocalContentColor.current) }; IconButton(onClick = { playerController.toggleRepeat() }) { Icon(Icons.Filled.Repeat, "Repeat", tint = if (playerController.repeatOption != RepeatOption.OFF) MaterialTheme.colorScheme.primary else LocalContentColor.current) }; OutlinedButton(onClick = onQueue) { Icon(Icons.AutoMirrored.Filled.QueueMusic, null); Spacer(Modifier.width(5.dp)); Text("Queue") } }

        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("CROSSFADE DURATION: ${playerController.crossfadeDurationMs / 1000}s", style = MaterialTheme.typography.labelSmall)
            Slider(value = playerController.crossfadeDurationMs.toFloat(), onValueChange = { playerController.crossfadeDurationMs = it.toLong() }, valueRange = 0f..10000f, steps = 9)
        }

        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    controller: AudioPlayerController,
    playlists: List<Playlist>,
    library: SnapshotStateList<AudioItem>,
    playlistRepo: PlaylistRepository,
    onDismiss: () -> Unit,
    onSelect: (AudioItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showExistingDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }

    val onlineSongs = remember(controller.playlist.toList()) {
        controller.playlist.filter {
            val source = it.uri.toString()
            (source.startsWith("http://") || source.startsWith("https://")) && it.album != "Live Radio"
        }
    }
    val hasOnlineSongs = onlineSongs.isNotEmpty()

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        downloading = true
        message = "Downloading queue..."
        scope.launch {
            val result = OnlineQueueDownloader.download(context, uri, onlineSongs)
            downloading = false
            message = "Downloaded ${result.downloaded}; skipped ${result.skipped}; failed ${result.failed}"
        }
    }

    suspend fun persistQueueItems() {
        val existingIds = library.map { it.id }.toHashSet()
        val newItems = controller.playlist.filter { existingIds.add(it.id) }
        if (newItems.isNotEmpty()) {
            library.addAll(newItems)
            PlayerLibraryStore.save(context, newItems)
        }
    }

    fun saveCurrentQueue(name: String) {
        val cleaned = name.trim()
        val items = controller.playlist.toList()
        if (cleaned.isBlank() || items.isEmpty()) return
        scope.launch {
            persistQueueItems()
            playlistRepo.insert(
                PlaylistEntity(
                    playlistId = UUID.randomUUID().toString(),
                    name = cleaned,
                    songIdsJson = items.joinToString(",") { it.id }
                )
            )
            message = "Playlist saved"
        }
    }

    fun addQueueToExisting(playlist: Playlist) {
        val items = controller.playlist.toList()
        if (items.isEmpty()) return
        scope.launch {
            persistQueueItems()
            val merged = (playlist.songIds + items.map { it.id }).distinct().joinToString(",")
            playlistRepo.updateSongs(playlist.id, merged)
            message = "Added to ${playlist.name}"
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("UPCOMING QUEUE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
                    Text("${controller.playlist.size} item(s)", style = MaterialTheme.typography.bodySmall)
                }
                if (hasOnlineSongs || downloading) {
                    Button(onClick = { if (!downloading) folderPicker.launch(null) }, enabled = !downloading) {
                        Icon(Icons.Filled.Download, null)
                        Spacer(Modifier.width(5.dp))
                        Text(if (downloading) "Downloading..." else "Download List")
                    }
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(controller.playlist, key = { it.id + it.uri }) { song ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(song) }.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (controller.currentSong?.id == song.id) Icons.Filled.PlayArrow else Icons.Filled.MusicNote, null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { playlistName = ""; showSaveDialog = true },
                    enabled = controller.playlist.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Save, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Save Playlist")
                }
                OutlinedButton(
                    onClick = { showExistingDialog = true },
                    enabled = controller.playlist.isNotEmpty() && playlists.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PlaylistAdd, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Add to Existing")
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Playlist") },
            text = { OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, label = { Text("Playlist name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val name = playlistName.trim()
                    if (name.isNotBlank()) { showSaveDialog = false; saveCurrentQueue(name) }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } }
        )
    }

    if (showExistingDialog) {
        AlertDialog(
            onDismissRequest = { showExistingDialog = false },
            title = { Text("Add to Existing Playlist") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        TextButton(onClick = { showExistingDialog = false; addQueueToExisting(playlist) }, modifier = Modifier.fillMaxWidth()) {
                            Text(playlist.name, modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showExistingDialog = false }) { Text("Cancel") } }
        )
    }
}
@Composable
private fun MixPlaylistsDialog(playlists: List<Playlist>, library: List<AudioItem>, onDismiss: () -> Unit, onPlay: (List<AudioItem>, Boolean) -> Unit) {
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var shuffle by remember { mutableStateOf(true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Mix playlists") }, text = { Column { Text("Choose playlists to combine into one queue.", style = MaterialTheme.typography.bodySmall); playlists.forEach { p -> Row(Modifier.fillMaxWidth().clickable { selected[p.id] = selected[p.id] != true }, verticalAlignment = Alignment.CenterVertically) { Checkbox(selected[p.id] == true, { selected[p.id] = it }); Text(p.name) } }; Row(verticalAlignment = Alignment.CenterVertically) { Switch(shuffle, { shuffle = it }); Spacer(Modifier.width(7.dp)); Text("Shuffle combined queue") } } }, confirmButton = { TextButton(onClick = { val songs = playlists.filter { selected[it.id] == true }.flatMap { p -> p.songIds.mapNotNull { id -> library.firstOrNull { it.id == id } } }.distinctBy { it.id }; onPlay(songs, shuffle) }) { Text("Play") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
