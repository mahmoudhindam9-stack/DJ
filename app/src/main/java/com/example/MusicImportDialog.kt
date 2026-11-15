package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.model.AudioItem
import com.example.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicImportDialog(
    onDismiss: () -> Unit,
    playlists: List<Playlist>,
    onImportToPlaylist: (List<AudioItem>, String) -> Unit,
    onCreatePlaylistAndImport: (List<AudioItem>, String) -> Unit,
    onPlayNow: (List<AudioItem>, Int) -> Unit
) {
    val context = LocalContext.current
    val requiredPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
    }

    var allFiles by remember { mutableStateOf<List<ImportFile>>(emptyList()) }
    var rootFolder by remember { mutableStateOf<ImportFolder?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    var expandedFolders by remember { mutableStateOf(setOf<String>("/")) }
    
    var showPlaylistSelector by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    LaunchedEffect(hasAudioPermission) {
        if (!hasAudioPermission) {
            allFiles = emptyList()
            rootFolder = null
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        withContext(Dispatchers.IO) {
            val f = MusicImportLogic.scanDeviceFiles(context)
            allFiles = f
            rootFolder = MusicImportLogic.buildTree(f)
        }
        isLoading = false
    }

    val searchResults = remember(searchQuery, allFiles) {
        if (searchQuery.isBlank()) emptyList()
        else {
            allFiles.map { it to MusicImportLogic.getScore(it, searchQuery) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .map { it.first }
        }
    }

    fun getAllPaths(f: ImportFolder): Set<String> {
        val s = mutableSetOf<String>()
        f.files.forEach { s.add(it.path) }
        f.subfolders.values.forEach { s.addAll(getAllPaths(it)) }
        return s
    }

    fun isFolderFullySelected(f: ImportFolder): Boolean {
        val paths = getAllPaths(f)
        return paths.isNotEmpty() && selectedPaths.containsAll(paths)
    }
    
    fun isFolderPartiallySelected(f: ImportFolder): Boolean {
        val paths = getAllPaths(f)
        return paths.any { it in selectedPaths } && !selectedPaths.containsAll(paths)
    }

    fun toggleFolder(f: ImportFolder) {
        val paths = getAllPaths(f)
        selectedPaths = if (isFolderFullySelected(f)) {
            selectedPaths - paths
        } else {
            selectedPaths + paths
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                TopAppBar(
                    title = { Text("Import Music") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                    },
                    actions = {
                        TextButton(onClick = { selectedPaths = allFiles.map { it.path }.toSet() }) { Text("Select All") }
                        TextButton(onClick = { selectedPaths = emptySet() }) { Text("Clear") }
                    }
                )
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search title, artist, folder...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) }
                        }
                    },
                    singleLine = true
                )

                Box(Modifier.weight(1f)) {
                    if (!hasAudioPermission) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.FolderOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "Allow audio access to browse local folders and songs.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(onClick = { permissionLauncher.launch(requiredPermission) }) {
                                Text("Grant Audio Permission")
                            }
                        }
                    } else if (isLoading) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    } else if (allFiles.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.AudioFile, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("No playable audio files found on device.")
                        }
                    } else if (searchQuery.isNotBlank()) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(searchResults, key = { it.path }) { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        selectedPaths = if (item.path in selectedPaths) selectedPaths - item.path else selectedPaths + item.path
                                    }.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = item.path in selectedPaths, onCheckedChange = null)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.audio.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${item.audio.artist} • ${item.path}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(onClick = {
                                        val (folderQueue, startIdx) = MusicImportLogic.siblingFolderQueueForFile(allFiles, item)
                                        if (folderQueue.isNotEmpty()) {
                                            onPlayNow(folderQueue, startIdx)
                                            onDismiss()
                                        }
                                    }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play folder from song")
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            fun renderFolder(folder: ImportFolder, depth: Int) {
                                val directFiles = MusicImportLogic.directPlayableFolderItems(folder)
                                if (folder.path != "/") {
                                    item(key = "folder_${folder.path}") {
                                        val fully = isFolderFullySelected(folder)
                                        val partial = isFolderPartiallySelected(folder)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(start = (depth * 16).dp).clickable {
                                                expandedFolders = if (folder.path in expandedFolders) expandedFolders - folder.path else expandedFolders + folder.path
                                            }.padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(onClick = { toggleFolder(folder) }) {
                                                when {
                                                    fully -> Icon(Icons.Default.CheckBox, null, tint = MaterialTheme.colorScheme.primary)
                                                    partial -> Icon(Icons.Default.IndeterminateCheckBox, null, tint = MaterialTheme.colorScheme.primary)
                                                    else -> Icon(Icons.Default.CheckBoxOutlineBlank, null)
                                                }
                                            }
                                            val exp = folder.path in expandedFolders
                                            Icon(if (exp) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight, null, Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(8.dp))
                                            Text(folder.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (directFiles.isNotEmpty()) {
                                                IconButton(onClick = {
                                                    onPlayNow(directFiles, 0)
                                                    onDismiss()
                                                }) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play folder")
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                if (folder.path == "/" || folder.path in expandedFolders) {
                                    folder.subfolders.values.sortedBy { it.name.lowercase() }.forEach { renderFolder(it, depth + 1) }
                                    folder.files
                                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.audio.title })
                                        .forEach { file ->
                                            item(key = "file_${file.path}") {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(start = ((depth + 1) * 16).dp).clickable {
                                                        selectedPaths = if (file.path in selectedPaths) selectedPaths - file.path else selectedPaths + file.path
                                                    }.padding(vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Checkbox(checked = file.path in selectedPaths, onCheckedChange = null)
                                                    Spacer(Modifier.width(8.dp))
                                                    Icon(Icons.Default.AudioFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Spacer(Modifier.width(8.dp))
                                                    Column(Modifier.weight(1f)) {
                                                        Text(file.audio.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text(file.audio.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                    IconButton(onClick = {
                                                        val queue = directFiles.ifEmpty { listOf(file.audio) }
                                                        val startIdx = queue.indexOfFirst { it.id == file.audio.id || it.uri == file.audio.uri }.coerceAtLeast(0)
                                                        onPlayNow(queue, startIdx)
                                                        onDismiss()
                                                    }) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play from folder")
                                                    }
                                                }
                                            }
                                        }
                                }
                            }
                            rootFolder?.let { renderFolder(it, 0) }
                        }
                    }
                }

                Surface(tonalElevation = 8.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val selectedItems = allFiles
                            .filter { it.path in selectedPaths }
                            .map { it.audio }
                            .distinctBy { it.id.ifBlank { it.uri.toString() } }
                        val btnEnabled = selectedItems.isNotEmpty()
                        
                        Button(onClick = { onPlayNow(selectedItems, 0); onDismiss() }, enabled = btnEnabled) {
                            Text("Play Now")
                        }
                        Button(onClick = { showPlaylistSelector = true }, enabled = btnEnabled) {
                            Text("Add to Playlist")
                        }
                        Button(onClick = { showCreatePlaylist = true }, enabled = btnEnabled) {
                            Text("New Playlist")
                        }
                    }
                }
            }
            
            if (showPlaylistSelector) {
                val selectedItems = allFiles.filter { it.path in selectedPaths }.map { it.audio }.distinctBy { it.id.ifBlank { it.uri.toString() } }
                AlertDialog(
                    onDismissRequest = { showPlaylistSelector = false },
                    title = { Text("Select Playlist") },
                    text = {
                        LazyColumn {
                            items(playlists) { p ->
                                Text(p.name, modifier = Modifier.fillMaxWidth().clickable {
                                    onImportToPlaylist(selectedItems, p.id)
                                    showPlaylistSelector = false
                                    onDismiss()
                                }.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showPlaylistSelector = false }) { Text("Cancel") } }
                )
            }
            
            if (showCreatePlaylist) {
                val selectedItems = allFiles.filter { it.path in selectedPaths }.map { it.audio }
                AlertDialog(
                    onDismissRequest = { showCreatePlaylist = false },
                    title = { Text("Create Playlist") },
                    text = {
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            label = { Text("Playlist Name") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                onCreatePlaylistAndImport(selectedItems, newPlaylistName)
                                showCreatePlaylist = false
                                onDismiss()
                            }
                        }) { Text("Create") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreatePlaylist = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}
