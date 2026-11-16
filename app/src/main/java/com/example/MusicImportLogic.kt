package com.example

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.example.model.AudioItem
import com.example.utils.MusicScanner
import java.io.File
import java.util.Locale

data class ImportFile(val audio: AudioItem, val path: String)

class ImportFolder(val name: String, val path: String) {
    val subfolders = mutableMapOf<String, ImportFolder>()
    val files = mutableListOf<ImportFile>()
}

object MusicImportLogic {

    fun isPlayableAudioFile(pathOrName: String, mimeType: String?, sizeBytes: Long): Boolean {
        if (sizeBytes <= 0L) return false
        val name = pathOrName.substringAfterLast('/')
        if (name.startsWith(".")) return false
        val mime = mimeType.orEmpty().lowercase(Locale.ROOT)
        if (mime.startsWith("image/") || mime.startsWith("video/")) return false
        if (mime.startsWith("audio/") || mime == "application/ogg" || mime == "application/x-ogg" || mime == "application/flac") {
            return true
        }
        return MusicScanner.isPlayableAudioName(name)
    }

    fun directPlayableFolderItems(folder: ImportFolder): List<AudioItem> =
        folder.files
            .sortedWith(
                compareBy<ImportFile, String>(String.CASE_INSENSITIVE_ORDER) { it.audio.title }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.path }
            )
            .map { it.audio }
            .distinctBy { it.id.ifBlank { it.uri.toString() } }

    fun siblingFolderQueueForFile(allFiles: List<ImportFile>, target: ImportFile): Pair<List<AudioItem>, Int> {
        val parent = File(target.path).parent ?: return listOf(target.audio) to 0
        val siblings = allFiles
            .filter { File(it.path).parent == parent }
            .sortedWith(
                compareBy<ImportFile, String>(String.CASE_INSENSITIVE_ORDER) { it.audio.title }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.path }
            )
            .map { it.audio }
            .distinctBy { it.id.ifBlank { it.uri.toString() } }
        if (siblings.isEmpty()) return listOf(target.audio) to 0
        val idx = siblings.indexOfFirst { it.id == target.audio.id || it.uri == target.audio.uri }.coerceAtLeast(0)
        return siblings to idx
    }

    private fun normalizeDisplayPath(rawPath: String, relativePath: String, album: String, displayName: String, id: Long): String {
        val baseCandidate = when {
            rawPath.isNotBlank() -> rawPath.replace('\\', '/')
            relativePath.isNotBlank() -> "/" + relativePath.trim('/') + "/" + displayName.ifBlank { "Track_$id.mp3" }
            else -> "/${album.ifBlank { "Music" }}/${displayName.ifBlank { "Track_$id.mp3" }}"
        }
        val stripped = baseCandidate
            .replaceFirst(Regex("^/storage/emulated/\\d+"), "")
            .replaceFirst(Regex("^/sdcard"), "")
            .trim()
        val withLeadingSlash = if (stripped.startsWith("/")) stripped else "/$stripped"
        return if (withLeadingSlash == "/" || withLeadingSlash.endsWith("/")) {
            "${withLeadingSlash.trimEnd('/')}/Track_$id.mp3"
        } else {
            withLeadingSlash
        }
    }

    fun scanDeviceFiles(context: Context): List<ImportFile> {
        val list = mutableListOf<ImportFile>()
        val seenUris = HashSet<String>()
        val usedPaths = HashSet<String>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projectionList.add(MediaStore.Audio.Media.RELATIVE_PATH)
        }

        try {
            context.contentResolver.query(
                collection,
                projectionList.toTypedArray(),
                null,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val dispCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = if (titleCol >= 0) cursor.getString(titleCol).orEmpty() else ""
                    val rawArtist = if (artistCol >= 0) cursor.getString(artistCol).orEmpty() else ""
                    val rawAlbum = if (albumCol >= 0) cursor.getString(albumCol).orEmpty() else ""
                    val duration = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val data = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                    val disp = if (dispCol >= 0) cursor.getString(dispCol).orEmpty() else ""
                    val relPath = if (relCol >= 0) cursor.getString(relCol).orEmpty() else ""

                    val nameForCheck = disp.ifBlank { data.ifBlank { rawTitle } }
                    val effectiveSize = when {
                        size > 0L -> size
                        data.isNotBlank() -> {
                            val fileLen = runCatching { File(data).takeIf { it.exists() }?.length() ?: 4096L }.getOrDefault(4096L)
                            if (fileLen > 0L) fileLen else 0L
                        }
                        else -> 4096L
                    }
                    if (!isPlayableAudioFile(nameForCheck, mime, effectiveSize)) continue

                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    if (!seenUris.add(contentUri.toString())) continue

                    val cleanFileName = disp.substringBeforeLast('.').ifBlank {
                        data.substringAfterLast('/').substringBeforeLast('.')
                    }
                    val resolvedTitle = when {
                        rawTitle.isNotBlank() && rawTitle != "<unknown>" -> rawTitle
                        cleanFileName.isNotBlank() -> cleanFileName
                        else -> "Track $id"
                    }
                    val resolvedArtist = when {
                        rawArtist.isNotBlank() && rawArtist != "<unknown>" -> rawArtist
                        cleanFileName.contains(" - ") -> cleanFileName.substringBefore(" - ").trim().ifBlank { "Unknown Artist" }
                        else -> "Unknown Artist"
                    }
                    val resolvedAlbum = when {
                        rawAlbum.isNotBlank() && rawAlbum != "<unknown>" -> rawAlbum
                        relPath.isNotBlank() -> relPath.trim('/').substringAfterLast('/').ifBlank { "Music" }
                        else -> "Unknown Album"
                    }

                    var normalizedPath = normalizeDisplayPath(data, relPath, resolvedAlbum, disp, id)
                    if (!usedPaths.add(normalizedPath.lowercase(Locale.ROOT))) {
                        val ext = normalizedPath.substringAfterLast('.', "")
                        val stem = if (ext.isNotBlank()) normalizedPath.substringBeforeLast('.') else normalizedPath
                        normalizedPath = if (ext.isNotBlank()) "${stem}_$id.$ext" else "${stem}_$id"
                        usedPaths.add(normalizedPath.lowercase(Locale.ROOT))
                    }

                    val item = AudioItem(
                        id = id.toString(),
                        title = resolvedTitle,
                        artist = resolvedArtist,
                        album = resolvedAlbum,
                        durationMs = duration.coerceAtLeast(0L),
                        uri = contentUri,
                        sizeBytes = size.coerceAtLeast(0L)
                    )
                    list.add(ImportFile(item, normalizedPath))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedWith(
            compareBy<ImportFile, String>(String.CASE_INSENSITIVE_ORDER) { it.audio.title }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.path }
        )
    }

    fun buildTree(files: List<ImportFile>): ImportFolder {
        val root = ImportFolder("Device", "/")
        for (f in files) {
            val parts = f.path.split("/").filter { it.isNotEmpty() }
            if (parts.isEmpty()) continue

            var current = root
            for (i in 0 until parts.size - 1) {
                val part = parts[i]
                current = current.subfolders.getOrPut(part) {
                    val newPath = current.path + if (current.path.endsWith("/")) part else "/$part"
                    ImportFolder(part, newPath)
                }
            }
            current.files.add(f)
        }
        return root
    }

    private fun normalizeText(text: String): String {
        var s = text.lowercase().trim()
        s = s.replace(Regex("[\\p{Punct}]"), "")
        s = s.replace(Regex("\\s+"), " ")
        s = s.replace(Regex("[أإآ]"), "ا")
        s = s.replace("ة", "ه")
        s = s.replace("ى", "ي")
        s = s.replace("ؤ", "و")
        s = s.replace("ئ", "ي")
        return s
    }

    private fun englishToArabic(text: String): String {
        return text.map { char ->
            when (char) {
                'a' -> "ا"; 'b' -> "ب"; 'c', 'k' -> "ك"; 'd' -> "د"; 'e', 'i', 'y' -> "ي"; 'f', 'v' -> "ف"; 'g', 'j' -> "ج"; 'h' -> "ه"; 'l' -> "ل"; 'm' -> "م"; 'n' -> "ن"; 'o', 'u', 'w' -> "و"; 'p' -> "ب"; 'q' -> "ق"; 'r' -> "ر"; 's' -> "س"; 't' -> "ت"; 'z' -> "ز"; 'x' -> "كس"
                else -> char.toString()
            }
        }.joinToString("")
    }

    private fun arabicToEnglish(text: String): String {
        val map = mapOf('ا' to "a", 'ب' to "b", 'ت' to "t", 'ث' to "th", 'ج' to "j", 'ح' to "h", 'خ' to "kh", 'د' to "d", 'ذ' to "z", 'ر' to "r", 'ز' to "z", 'س' to "s", 'ش' to "sh", 'ص' to "s", 'ض' to "d", 'ط' to "t", 'ظ' to "z", 'ع' to "a", 'غ' to "gh", 'ف' to "f", 'ق' to "k", 'ك' to "k", 'ل' to "l", 'م' to "m", 'ن' to "n", 'ه' to "h", 'و' to "w", 'ي' to "y")
        return text.map { map[it] ?: it.toString() }.joinToString("")
    }

    fun getScore(item: ImportFile, query: String): Int {
        val qNorm = normalizeText(query)
        if (qNorm.isEmpty()) return 1

        val title = normalizeText(item.audio.title)
        val artist = normalizeText(item.audio.artist)
        val path = normalizeText(item.path)

        val textToSearch = "$title $artist $path"

        if (textToSearch.contains(qNorm)) return 100

        val qEngToArabic = englishToArabic(qNorm)
        if (textToSearch.contains(qEngToArabic)) return 90

        val qArabicToEng = arabicToEnglish(qNorm)
        if (textToSearch.contains(qArabicToEng)) return 90

        val qWords = qNorm.split(" ")
        if (qWords.all { textToSearch.contains(it) }) return 80
        if (qWords.all { textToSearch.contains(englishToArabic(it)) || textToSearch.contains(arabicToEnglish(it)) }) return 70

        var score = 0
        for (word in qWords) {
            if (textToSearch.contains(word)) score += 10
            else if (textToSearch.contains(englishToArabic(word))) score += 5
            else if (textToSearch.contains(arabicToEnglish(word))) score += 5
        }
        return score
    }
}
