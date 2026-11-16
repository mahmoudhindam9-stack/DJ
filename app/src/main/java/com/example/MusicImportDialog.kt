package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.model.AudioItem
import com.example.model.Playlist
import com.example.utils.MusicScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicImportDialog(
    onDismiss: () -> Unit,
    playlists: List<Playlist>,
    onImportToLibrary: (List<AudioItem>) -> Unit = {},
    onImportToPlaylist: (List<AudioItem>, String) -> Unit,
    onCreatePlaylistAndImport: (List<AudioItem>, String) -> Unit,
    onPlayNow: (List<AudioItem>, Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    // Automatically request audio permission when the dialog opens if not yet granted
    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            runCatching { permissionLauncher.launch(requiredPermission) }
        }
    }

    val safFilesPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    runCatching { MusicScanner.parsePickedUri(context, uri) }.getOrNull()
                }
            }
            if (picked.isNotEmpty()) {
                onImportToLibrary(picked)
                onDismiss()
            }
        }
    }

    val safFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val scanned = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                FolderAudioImporter.scan(context, treeUri)
            }
            if (scanned.isNotEmpty()) {
                onImportToLibrary(scanned)
                onDismiss()
            }
        }
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
            val tree = MusicImportLogic.buildTree(f)
            allFiles = f
            rootFolder = tree
            // Auto-expand top-level folders so the user sees their music folders immediately
            expandedFolders = setOf("/") + tree.subfolders.values.map { it.path }.toSet()
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

    fun toggleFilePath(path: String) {
        selectedPaths = if (path in selectedPaths) {
            selectedPaths - path
        } else {
            selectedPaths + path
        }
    }

    val isArabic by com.example.ui.locale.AppLanguageManager.isArabicFlow.collectAsState()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(if (isArabic) "استيراد الموسيقى" else "Import Music") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, if (isArabic) "إغلاق" else "Close") }
                    },
                    actions = {
                        TextButton(onClick = { safFilesPicker.launch(arrayOf("audio/*")) }) {
                            Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isArabic) "ملفات" else "Files")
                        }
                        TextButton(onClick = { safFolderPicker.launch(null) }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isArabic) "مجلد" else "Folder")
                        }
                        TextButton(onClick = { selectedPaths = allFiles.map { it.path }.toSet() }) {
                            Text(if (isArabic) "تحديد الكل" else "Select All")
                        }
                        TextButton(onClick = { selectedPaths = emptySet() }) {
                            Text(if (isArabic) "مسح" else "Clear")
                        }
                    }
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(if (isArabic) "ابحث بالعنوان، الفنان، أو المجلد..." else "Search title, artist, folder...") },
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
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.FolderOff,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                if (isArabic) "اسمح بالوصول للصوتيات لفحص مجلدات الجهاز، أو اختر الملفات/المجلدات مباشرة:"
                                else "Allow audio access to scan device folders, or pick files/folders directly:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { permissionLauncher.launch(requiredPermission) }) {
                                    Text(if (isArabic) "منح الإذن" else "Grant Permission")
                                }
                                OutlinedButton(onClick = { safFilesPicker.launch(arrayOf("audio/*")) }) {
                                    Text(if (isArabic) "اختيار ملفات" else "Pick Audio Files")
                                }
                                OutlinedButton(onClick = { safFolderPicker.launch(null) }) {
                                    Text(if (isArabic) "اختيار مجلد" else "Pick Folder")
                                }
                            }
                        }
                    } else if (isLoading) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    } else if (allFiles.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.AudioFile,
                                null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(if (isArabic) "لم يتم العثور على ملفات صوتية مفهرسة." else "No indexed audio files found in MediaStore.")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { safFilesPicker.launch(arrayOf("audio/*")) }) {
                                    Text(if (isArabic) "تصفح الملفات الصوتية" else "Browse Audio Files")
                                }
                                OutlinedButton(onClick = { safFolderPicker.launch(null) }) {
                                    Text(if (isArabic) "تصفح مجلد" else "Browse Folder")
                                }
                            }
                        }
                    } else if (searchQuery.isNotBlank()) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(searchResults, key = { "search_${it.audio.id}_${it.path}" }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { toggleFilePath(item.path) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = item.path in selectedPaths,
                                        onCheckedChange = { toggleFilePath(item.path) }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            item.audio.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${item.audio.artist} • ${item.path}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = {
                                        val (folderQueue, startIdx) = MusicImportLogic.siblingFolderQueueForFile(allFiles, item)
                                        if (folderQueue.isNotEmpty()) {
                                            onPlayNow(folderQueue, startIdx)
                                            onDismiss()
                                        }
                                    }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = if (isArabic) "تشغيل المجلد من هذه الأغنية" else "Play folder from song")
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
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = (depth * 14).dp)
                                                .clickable {
                                                    expandedFolders = if (folder.path in expandedFolders) {
                                                        expandedFolders - folder.path
                                                    } else {
                                                        expandedFolders + folder.path
                                                    }
                                                }
                                                .padding(vertical = 4.dp),
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
                                            Icon(
                                                if (exp) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                                null,
                                                Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "${folder.name} (${getAllPaths(folder).size})",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (directFiles.isNotEmpty()) {
                                                IconButton(onClick = {
                                                    onPlayNow(directFiles, 0)
                                                    onDismiss()
                                                }) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = if (isArabic) "تشغيل المجلد" else "Play folder")
                                                }
                                            }
                                        }
                                    }
                                }

                                if (folder.path == "/" || folder.path in expandedFolders) {
                                    folder.subfolders.values
                                        .sortedBy { it.name.lowercase() }
                                        .forEach { renderFolder(it, if (folder.path == "/") 0 else depth + 1) }
                                    folder.files
                                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.audio.title })
                                        .forEach { file ->
                                            item(key = "file_${file.audio.id}_${file.path}") {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = ((if (folder.path == "/") 0 else depth + 1) * 14).dp)
                                                        .clickable { toggleFilePath(file.path) }
                                                        .padding(vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Checkbox(
                                                        checked = file.path in selectedPaths,
                                                        onCheckedChange = { toggleFilePath(file.path) }
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Icon(
                                                        Icons.Default.AudioFile,
                                                        null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Column(Modifier.weight(1f)) {
                                                        Text(
                                                            file.audio.title,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            file.audio.artist,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    IconButton(onClick = {
                                                        val queue = directFiles.ifEmpty { listOf(file.audio) }
                                                        val startIdx = queue.indexOfFirst {
                                                            it.id == file.audio.id || it.uri == file.audio.uri
                                                        }.coerceAtLeast(0)
                                                        onPlayNow(queue, startIdx)
                                                        onDismiss()
                                                    }) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = if (isArabic) "تشغيل من المجلد" else "Play from folder")
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
                    val selectedItems = remember(allFiles, selectedPaths) {
                        allFiles
                            .filter { it.path in selectedPaths }
                            .map { it.audio }
                            .distinctBy { it.id.ifBlank { it.uri.toString() } }
                    }
                    val btnEnabled = selectedItems.isNotEmpty()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onImportToLibrary(selectedItems)
                                    onDismiss()
                                },
                                enabled = btnEnabled,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.LibraryAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isArabic) "استيراد (${selectedItems.size})" else "Import (${selectedItems.size})")
                            }
                            FilledTonalButton(
                                onClick = {
                                    onPlayNow(selectedItems, 0)
                                    onDismiss()
                                },
                                enabled = btnEnabled,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isArabic) "تشغيل الآن" else "Play Now")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (playlists.isEmpty()) {
                                        showCreatePlaylist = true
                                    } else {
                                        showPlaylistSelector = true
                                    }
                                },
                                enabled = btnEnabled,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isArabic) "إضافة لقائمة" else "Add to Playlist")
                            }
                            OutlinedButton(
                                onClick = { showCreatePlaylist = true },
                                enabled = btnEnabled,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isArabic) "قائمة جديدة" else "New Playlist")
                            }
                        }
                    }
                }
            }

            if (showPlaylistSelector) {
                val selectedItems = allFiles
                    .filter { it.path in selectedPaths }
                    .map { it.audio }
                    .distinctBy { it.id.ifBlank { it.uri.toString() } }
                AlertDialog(
                    onDismissRequest = { showPlaylistSelector = false },
                    title = { Text(if (isArabic) "اختر قائمة التشغيل" else "Select Playlist") },
                    text = {
                        LazyColumn {
                            items(playlists) { p ->
                                Text(
                                    p.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onImportToPlaylist(selectedItems, p.id)
                                            showPlaylistSelector = false
                                            onDismiss()
                                        }
                                        .padding(16.dp),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showPlaylistSelector = false
                            showCreatePlaylist = true
                        }) { Text(if (isArabic) "قائمة جديدة" else "New Playlist") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPlaylistSelector = false }) { Text(if (isArabic) "إلغاء" else "Cancel") }
                    }
                )
            }

            if (showCreatePlaylist) {
                val selectedItems = allFiles
                    .filter { it.path in selectedPaths }
                    .map { it.audio }
                    .distinctBy { it.id.ifBlank { it.uri.toString() } }
                AlertDialog(
                    onDismissRequest = { showCreatePlaylist = false },
                    title = { Text(if (isArabic) "إنشاء قائمة تشغيل" else "Create Playlist") },
                    text = {
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            label = { Text(if (isArabic) "اسم قائمة التشغيل" else "Playlist Name") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                onCreatePlaylistAndImport(selectedItems, newPlaylistName.trim())
                                showCreatePlaylist = false
                                onDismiss()
                            }
                        }) { Text(if (isArabic) "إنشاء" else "Create") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreatePlaylist = false }) { Text(if (isArabic) "إلغاء" else "Cancel") }
                    }
                )
            }
        }
    }
}
