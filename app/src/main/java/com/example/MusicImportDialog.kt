package com.example

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    onPlayNow: (List<AudioItem>) -> Unit
) {
    val context = LocalContext.current
    var allFiles by remember { mutableStateOf<List<ImportFile>>(emptyList()) }
    var rootFolder by remember { mutableStateOf<ImportFolder?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    var expandedFolders by remember { mutableStateOf(setOf<String>("/")) }
    
    var showPlaylistSelector by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
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
                    if (isLoading) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    } else if (searchQuery.isNotBlank()) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(searchResults) { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        selectedPaths = if (item.path in selectedPaths) selectedPaths - item.path else selectedPaths + item.path
                                    }.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = item.path in selectedPaths, onCheckedChange = null)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(item.audio.title, style = MaterialTheme.typography.bodyLarge)
                                        Text("${item.audio.artist} • ${item.path}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            fun renderFolder(folder: ImportFolder, depth: Int) {
                                if (folder.path != "/") {
                                    item {
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
                                            Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                }
                                
                                if (folder.path == "/" || folder.path in expandedFolders) {
                                    folder.subfolders.values.sortedBy { it.name }.forEach { renderFolder(it, depth + 1) }
                                    folder.files.sortedBy { it.audio.title }.forEach { file ->
                                        item {
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
                                                Column {
                                                    Text(file.audio.title, style = MaterialTheme.typography.bodyMedium)
                                                    Text(file.audio.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        val selectedItems = allFiles.filter { it.path in selectedPaths }.map { it.audio }
                        val btnEnabled = selectedItems.isNotEmpty()
                        
                        Button(onClick = { onPlayNow(selectedItems); onDismiss() }, enabled = btnEnabled) {
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
                val selectedItems = allFiles.filter { it.path in selectedPaths }.map { it.audio }
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
