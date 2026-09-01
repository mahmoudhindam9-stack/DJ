package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
    var showPlaylistPicker by remember { mutableStateOf<AudioItem?>(null) }
    var showMixPlaylists by remember { mutableStateOf(false) }
    var showLibraryMenu by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val added = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    MusicScanner.parsePickedUri(context, uri)
                }
            }
            addToLibrary(audioLibrary, added, context)
            scanMessage = "Added ${added.size} song(s) to the library"
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
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
            scanMessage = "Added ${songs.size} song(s) from folder"
        }
    }

    val deviceScan = {
        scope.launch {
            val songs = withContext(Dispatchers.IO) { MusicScanner.scanMediaStoreAudio(context) }
            addToLibrary(audioLibrary, songs, context)
            scanMessage = "Scanned ${songs.size} device song(s)"
        }
    }

    if (showNowPlaying && playerController.currentSong != null) {
        NowPlayingFullScreenV2(
            playerController = playerController,
            onBack = { showNowPlaying = false },
            onQueue = { showQueue = true },
            onPauseDJ = onPauseDJ
        )
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Music Player", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Library, playlists and queue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { showLibraryMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Library menu")
                    }
                    DropdownMenu(expanded = showLibraryMenu, onDismissRequest = { showLibraryMenu = false }) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Filled.LibraryMusic, null) },
                            text = { Text("Scan device music") },
                            onClick = { showLibraryMenu = false; deviceScan() }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Filled.Add, null) },
                            text = { Text("Add audio files") },
                            onClick = { showLibraryMenu = false; filePicker.launch(arrayOf("audio/*")) }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Filled.Folder, null) },
                            text = { Text("Add music folder") },
                            onClick = { showLibraryMenu = false; folderPicker.launch(null) }
                        )
                    }
                }
            }

            scanMessage?.let {
                AssistChip(onClick = { scanMessage = null }, label = { Text(it) }, leadingIcon = { Icon(Icons.Filled.Check, null) })
                Spacer(Modifier.height(8.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showCreatePlaylist = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("New playlist")
                }
                OutlinedButton(onClick = { showMixPlaylists = true }, modifier = Modifier.weight(1f), enabled = playlists.size >= 2) {
                    Icon(Icons.Filled.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Mix playlists")
                }
            }

            Spacer(Modifier.height(10.dp))

            if (playlists.isNotEmpty()) {
                Text("Playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(0.95f)) {
                    items(playlists, key = { it.id }) { playlist ->
                        val songs = playlist.songIds.mapNotNull { id -> audioLibrary.firstOrNull { it.id == id } }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.PlaylistPlay, null, Modifier.size(30.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(playlist.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${songs.size} song(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = {
                                        if (songs.isNotEmpty()) {
                                            onPauseDJ(); playerController.playSong(songs.first(), songs); showNowPlaying = true
                                        }
                                    }) { Icon(Icons.Filled.PlayArrow, "Play") }
                                    IconButton(onClick = {
                                        if (songs.isNotEmpty()) {
                                            onPauseDJ(); val q = songs.shuffled(); playerController.playSong(q.first(), q); showNowPlaying = true
                                        }
                                    }) { Icon(Icons.Filled.Shuffle, "Shuffle") }
                                    IconButton(onClick = {
                                        scope.launch { repo.delete(playlist.id) }
                                    }) { Icon(Icons.Filled.Delete, "Delete") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        showPlaylistPicker = null
                                        scope.launch { /* no-op anchor for consistent state */ }
                                        // add-from-library uses the song picker below
                                        showPlaylistPickerForPlaylist = playlist.id
                                    }) {
                                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Add songs")
                                    }
                                    OutlinedButton(onClick = {
                                        showFolderPlaylistTarget = playlist.id
                                    }) {
                                        Icon(Icons.Filled.Folder, null); Spacer(Modifier.width(4.dp)); Text("Add folder")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (audioLibrary.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.LibraryMusic, null, Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Your library is empty", fontWeight = FontWeight.SemiBold)
                        Text("Add files, scan the device, or import a folder.", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { filePicker.launch(arrayOf("audio/*")) }) { Text("Add songs") }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    items(audioLibrary, key = { it.id }) { song ->
                        LibrarySongRow(
                            song = song,
                            isCurrent = playerController.currentSong?.id == song.id,
                            onPlay = {
                                onPauseDJ(); playerController.playSong(song, audioLibrary); showNowPlaying = true
                            },
                            onAddToPlaylist = { showPlaylistPicker = song }
                        )
                    }
                }
            }
        }
    }

    if (showCreatePlaylist) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylist = false },
            onCreate = { name ->
                scope.launch {
                    repo.insert(PlaylistEntity(playlistId = UUID.randomUUID().toString(), name = name, songIdsJson = ""))
                    showCreatePlaylist = false
                }
            }
        )
    }

    showPlaylistPicker?.let { song ->
        PlaylistPickerDialog(
            song = song,
            playlists = playlists,
            onDismiss = { showPlaylistPicker = null },
            onSelect = { playlist ->
                scope.launch {
                    val ids = (playlist.songIds + song.id).distinct()
                    repo.updateSongs(playlist.id, ids.joinToString(","))
                    showPlaylistPicker = null
                }
            },
            onCreatePlaylist = { name ->
                scope.launch {
                    repo.insert(PlaylistEntity(playlistId = UUID.randomUUID().toString(), name = name, songIdsJson = song.id))
                    showPlaylistPicker = null
                }
            }
        )
    }

    if (showQueue) {
        QueueSheet(
            controller = playerController,
            onDismiss = { showQueue = false },
            onSelect = { song ->
                playerController.playSong(song, playerController.playlist)
                showQueue = false
            }
        )
    }

    if (showMixPlaylists) {
        MixPlaylistsDialog(
            playlists = playlists,
            audioLibrary = audioLibrary,
            onDismiss = { showMixPlaylists = false },
            onPlay = { songs, shuffle ->
                if (songs.isNotEmpty()) {
                    val queue = if (shuffle) songs.shuffled() else songs
                    onPauseDJ(); playerController.playSong(queue.first(), queue); showNowPlaying = true
                }
                showMixPlaylists = false
            }
        )
    }
}

private var showPlaylistPickerForPlaylist: String? by mutableStateOf(null)
private var showFolderPlaylistTarget: String? by mutableStateOf(null)

@Composable
private fun LibrarySongRow(song: AudioItem, isCurrent: Boolean, onPlay: () -> Unit, onAddToPlaylist: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MusicNote, null, Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onAddToPlaylist) { Icon(Icons.Filled.Add, "Add to playlist") }
            IconButton(onClick = onPlay) { Icon(if (isCurrent) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingFullScreenV2(
    playerController: AudioPlayerController,
    onBack: () -> Unit,
    onQueue: () -> Unit,
    onPauseDJ: () -> Unit
) {
    val song = playerController.currentSong ?: return
    val maxPos = playerController.durationMs.coerceAtLeast(1L).toFloat()
    val current = playerController.currentPositionMs.coerceIn(0L, maxPos.toLong()).toFloat()
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
            Text("Now Playing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = onQueue) { Icon(Icons.Filled.QueueMusic, "Queue") }
        }
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier.fillMaxWidth().weight(0.8f).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Album, null, Modifier.size(180.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(22.dp))
        Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(song.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(18.dp))
        Slider(current, { playerController.seekTo(it.toLong()) }, valueRange = 0f..maxPos)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(MusicScanner.formatMs(playerController.currentPositionMs), style = MaterialTheme.typography.labelSmall)
            Text(MusicScanner.formatMs(playerController.durationMs), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { playerController.playPrevious() }) { Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(32.dp)) }
            FilledIconButton(onClick = { onPauseDJ(); playerController.togglePlayPause() }, modifier = Modifier.size(68.dp)) {
                Icon(if (playerController.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", Modifier.size(38.dp))
            }
            IconButton(onClick = { playerController.playNext() }) { Icon(Icons.Filled.SkipNext, "Next", Modifier.size(32.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { playerController.toggleShuffle() }) {
                Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (playerController.isShuffle) MaterialTheme.colorScheme.primary else LocalContentColor.current)
            }
            IconButton(onClick = { playerController.toggleRepeat() }) {
                Icon(Icons.Filled.Repeat, "Repeat", tint = if (playerController.repeatOption != RepeatOption.OFF) MaterialTheme.colorScheme.primary else LocalContentColor.current)
            }
            OutlinedButton(onClick = onQueue) { Icon(Icons.Filled.QueueMusic, null); Spacer(Modifier.width(6.dp)); Text("Queue") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(controller: AudioPlayerController, onDismiss: () -> Unit, onSelect: (AudioItem) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Queue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${controller.playlist.size} song(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 500.dp)) {
                items(controller.playlist, key = { it.id + it.uri }) { song ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSelect(song) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val selected = controller.currentSong?.id == song.id
                        Icon(if (selected) Icons.Filled.PlayArrow else Icons.Filled.MusicNote, null)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(MusicScanner.formatMs(song.durationMs), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create playlist") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Playlist name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onCreate(name.trim()) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PlaylistPickerDialog(
    song: AudioItem,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelect: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${song.title} to playlist") },
        text = {
            Column {
                if (playlists.isEmpty()) Text("No playlists yet. Create one below.")
                playlists.forEach { playlist ->
                    TextButton(onClick = { onSelect(playlist) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PlaylistPlay, null); Spacer(Modifier.width(8.dp)); Text(playlist.name, Modifier.weight(1f))
                            if (song.id in playlist.songIds) Icon(Icons.Filled.Check, null)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Create new playlist") }, singleLine = true)
                if (newName.isNotBlank()) TextButton(onClick = { onCreatePlaylist(newName.trim()) }) { Text("Create and add") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun MixPlaylistsDialog(
    playlists: List<Playlist>,
    audioLibrary: List<AudioItem>,
    onDismiss: () -> Unit,
    onPlay: (List<AudioItem>, Boolean) -> Unit
) {
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var shuffle by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mix playlists") },
        text = {
            Column {
                Text("Choose two or more playlists. Their songs will become one queue.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                playlists.forEach { playlist ->
                    Row(Modifier.fillMaxWidth().clickable { selected[playlist.id] = !(selected[playlist.id] ?: false) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selected[playlist.id] == true, onCheckedChange = { selected[playlist.id] = it })
                        Spacer(Modifier.width(6.dp)); Text(playlist.name)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(shuffle, { shuffle = it }); Spacer(Modifier.width(8.dp)); Text("Shuffle combined queue")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val songs = playlists.filter { selected[it.id] == true }
                    .flatMap { p -> p.songIds.mapNotNull { id -> audioLibrary.firstOrNull { it.id == id } } }
                    .distinctBy { it.id }
                onPlay(songs, shuffle)
            }) { Text("Play") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun addToLibrary(target: SnapshotStateList<AudioItem>, songs: Collection<AudioItem>, context: Context) {
    val existing = target.associateBy { it.id }.toMutableMap()
    songs.forEach { existing[it.id] = it }
    target.clear()
    target.addAll(existing.values.sortedBy { it.title.lowercase() })
    PlayerLibraryStore.save(context, target)
}
