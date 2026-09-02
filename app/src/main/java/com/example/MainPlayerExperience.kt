package com.example
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.model.AudioItem
import com.example.model.Playlist
import com.example.player.AudioPlayerController
import com.example.player.RepeatOption
import com.example.room.AppDatabase
import com.example.room.PlaylistEntity
import com.example.room.PlaylistRepository
import com.example.utils.MusicScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreenV2(
    playerController: AudioPlayerController,
    audioLibrary: SnapshotStateList<AudioItem>,
    playlists: SnapshotStateList<Playlist>,
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

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val target = addFolderPlaylistId
        addFolderPlaylistId = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val songs = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
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
                    Text("Music Player", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Library • Playlists • Queue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showLibraryMenu = true }) { Icon(Icons.Filled.MoreVert, "Library menu") }
                DropdownMenu(expanded = showLibraryMenu, onDismissRequest = { showLibraryMenu = false }) {
                    DropdownMenuItem(text = { Text("Scan device music") }, onClick = { showLibraryMenu = false; scanDevice() }, leadingIcon = { Icon(Icons.Filled.LibraryMusic, null) })
                    DropdownMenuItem(text = { Text("Add audio files") }, onClick = { showLibraryMenu = false; filePicker.launch(arrayOf("audio/*")) }, leadingIcon = { Icon(Icons.Filled.Add, null) })
                    DropdownMenuItem(text = { Text("Add music folder") }, onClick = { showLibraryMenu = false; folderPicker.launch(null) }, leadingIcon = { Icon(Icons.Filled.Folder, null) })
                }
            }
            infoMessage?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showCreatePlaylist = true }, Modifier.weight(1f)) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(5.dp)); Text("New playlist") }
                OutlinedButton(onClick = { showMixPlaylists = true }, Modifier.weight(1f), enabled = playlists.size >= 2) { Icon(Icons.Filled.Shuffle, null); Spacer(Modifier.width(5.dp)); Text("Mix playlists") }
            }
            Spacer(Modifier.height(10.dp))
            if (playlists.isNotEmpty()) {
                Text("Playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                LazyColumn(Modifier.weight(0.75f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        val songs = playlist.songIds.mapNotNull { id -> audioLibrary.firstOrNull { it.id == id } }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.PlaylistPlay, null, Modifier.size(28.dp)); Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(playlist.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${songs.size} song(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { if (songs.isNotEmpty()) { onPauseDJ(); playerController.playSong(songs.first(), songs); showNowPlaying = true } }) { Icon(Icons.Filled.PlayArrow, "Play") }
                                    IconButton(onClick = { if (songs.isNotEmpty()) { onPauseDJ(); val q = songs.shuffled(); playerController.playSong(q.first(), q); showNowPlaying = true } }) { Icon(Icons.Filled.Shuffle, "Shuffle") }
                                    IconButton(onClick = { scope.launch { repo.delete(playlist.id) } }) { Icon(Icons.Filled.Delete, "Delete") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    OutlinedButton(onClick = { addSongsPlaylistId = playlist.id }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Add songs") }
                                    OutlinedButton(onClick = { addFolderPlaylistId = playlist.id; folderPicker.launch(null) }) { Icon(Icons.Filled.Folder, null); Spacer(Modifier.width(4.dp)); Text("Add folder") }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            if (audioLibrary.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.LibraryMusic, null, Modifier.size(48.dp)); Spacer(Modifier.height(8.dp))
                        Text("Your library is empty", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp))
                        Button(onClick = { filePicker.launch(arrayOf("audio/*")) }) { Text("Add songs") }
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(audioLibrary, key = { it.id }) { song ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.MusicNote, null); Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                IconButton(onClick = { addSongToPlaylist = song }) { Icon(Icons.Filled.Add, "Add to playlist") }
                                FilledIconButton(onClick = { onPauseDJ(); playerController.playSong(song, audioLibrary); showNowPlaying = true }) { Icon(Icons.Filled.PlayArrow, "Play") }
                            }
                        }
                    }
                }
            }
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
    if (showQueue) QueueSheet(playerController, { showQueue = false }) { song -> playerController.playSong(song, playerController.playlist); showQueue = false }
    if (showMixPlaylists) MixPlaylistsDialog(playlists, audioLibrary, { showMixPlaylists = false }) { songs, shuffle ->
        if (songs.isNotEmpty()) { val queue = if (shuffle) songs.shuffled() else songs; onPauseDJ(); playerController.playSong(queue.first(), queue); showNowPlaying = true }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }; Text("Now Playing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); IconButton(onClick = onQueue) { Icon(Icons.Filled.QueueMusic, "Queue") } }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().weight(0.85f).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Album, null, Modifier.size(180.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(18.dp))
        Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(song.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Slider(current, { playerController.seekTo(it.toLong()) }, valueRange = 0f..maxPos)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(MusicScanner.formatMs(playerController.currentPositionMs), style = MaterialTheme.typography.labelSmall); Text(MusicScanner.formatMs(playerController.durationMs), style = MaterialTheme.typography.labelSmall) }
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { playerController.playPrevious() }) { Icon(Icons.Filled.SkipPrevious, "Previous") }; FilledIconButton(onClick = { onPauseDJ(); playerController.togglePlayPause() }, Modifier.size(66.dp)) { Icon(if (playerController.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", Modifier.size(36.dp)) }; IconButton(onClick = { playerController.playNext() }) { Icon(Icons.Filled.SkipNext, "Next") } }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { playerController.toggleShuffle() }) { Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (playerController.isShuffle) MaterialTheme.colorScheme.primary else LocalContentColor.current) }; IconButton(onClick = { playerController.toggleRepeat() }) { Icon(Icons.Filled.Repeat, "Repeat", tint = if (playerController.repeatOption != RepeatOption.OFF) MaterialTheme.colorScheme.primary else LocalContentColor.current) }; OutlinedButton(onClick = onQueue) { Icon(Icons.Filled.QueueMusic, null); Spacer(Modifier.width(5.dp)); Text("Queue") } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(controller: AudioPlayerController, onDismiss: () -> Unit, onSelect: (AudioItem) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) { Column(Modifier.fillMaxWidth().padding(16.dp)) { Text("Queue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("${controller.playlist.size} song(s)", style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(8.dp)); LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { items(controller.playlist, key = { it.id + it.uri }) { song -> Row(Modifier.fillMaxWidth().clickable { onSelect(song) }.padding(9.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (controller.currentSong?.id == song.id) Icons.Filled.PlayArrow else Icons.Filled.MusicNote, null); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) } } } } } }
}

@Composable
private fun MixPlaylistsDialog(playlists: List<Playlist>, library: List<AudioItem>, onDismiss: () -> Unit, onPlay: (List<AudioItem>, Boolean) -> Unit) {
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var shuffle by remember { mutableStateOf(true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Mix playlists") }, text = { Column { Text("Choose playlists to combine into one queue.", style = MaterialTheme.typography.bodySmall); playlists.forEach { p -> Row(Modifier.fillMaxWidth().clickable { selected[p.id] = selected[p.id] != true }, verticalAlignment = Alignment.CenterVertically) { Checkbox(selected[p.id] == true, { selected[p.id] = it }); Text(p.name) } }; Row(verticalAlignment = Alignment.CenterVertically) { Switch(shuffle, { shuffle = it }); Spacer(Modifier.width(7.dp)); Text("Shuffle combined queue") } } }, confirmButton = { TextButton(onClick = { val songs = playlists.filter { selected[it.id] == true }.flatMap { p -> p.songIds.mapNotNull { id -> library.firstOrNull { it.id == id } } }.distinctBy { it.id }; onPlay(songs, shuffle) }) { Text("Play") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
