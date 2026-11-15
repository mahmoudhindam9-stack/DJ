package com.example.utils

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.model.AudioItem
import java.io.File
import java.util.Locale

object MusicScanner {

    private val playableExtensions = setOf(
        "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "3gp", "mid", "midi"
    )

    fun isPlayableAudioName(nameOrPath: String): Boolean {
        val ext = nameOrPath.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in playableExtensions
    }

    fun scanMediaStoreAudio(context: Context): List<AudioItem> {
        val audioList = mutableListOf<AudioItem>()
        val seenUris = HashSet<String>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.SIZE} > 0"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val displayCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown Track"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val album = cursor.getString(albumColumn) ?: "Unknown Album"
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)
                    val displayName = if (displayCol >= 0) cursor.getString(displayCol).orEmpty() else ""
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else ""

                    if (size <= 0L) continue
                    val playable = mime.startsWith("audio/", true) ||
                        mime.equals("application/ogg", true) ||
                        isPlayableAudioName(displayName)
                    if (!playable) continue

                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    if (!seenUris.add(contentUri.toString())) continue

                    audioList.add(
                        AudioItem(
                            id = id.toString(),
                            title = if (title == "<unknown>") displayName.substringBeforeLast('.').ifBlank { "Track $id" } else title,
                            artist = if (artist == "<unknown>") "Unknown Artist" else artist,
                            album = if (album == "<unknown>") "Unknown Album" else album,
                            durationMs = duration,
                            uri = contentUri,
                            sizeBytes = size
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return audioList
    }

    /**
     * Resolves playable sibling audio files in the same folder as [uri] cleanly for folder queueing.
     * Falls back gracefully to listOf(fallbackItem) if scoped storage restricts directory inspection.
     */
    fun buildFolderQueueForUri(
        context: Context,
        uri: Uri,
        fallbackItem: AudioItem
    ): Pair<List<AudioItem>, Int> {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val filePath = uri.path
            if (!filePath.isNullOrBlank()) {
                val targetFile = File(filePath)
                val parentDir = targetFile.parentFile
                val siblings = runCatching {
                    parentDir?.listFiles()
                        ?.filter { it.isFile && it.length() > 0L && isPlayableAudioName(it.name) }
                        ?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                        ?.map { sibling ->
                            val siblingUri = Uri.fromFile(sibling)
                            if (sibling.absolutePath == targetFile.absolutePath) {
                                fallbackItem
                            } else {
                                AudioItem(
                                    id = siblingUri.hashCode().toString(),
                                    title = sibling.nameWithoutExtension.ifBlank { sibling.name },
                                    artist = parentDir?.name ?: "Local Folder",
                                    album = parentDir?.name ?: "Folder",
                                    durationMs = 0L,
                                    uri = siblingUri,
                                    sizeBytes = sibling.length()
                                )
                            }
                        }
                        ?.distinctBy { it.uri.toString() }
                        .orEmpty()
                }.getOrDefault(emptyList())

                if (siblings.isNotEmpty()) {
                    val idx = siblings.indexOfFirst { it.uri == fallbackItem.uri }.coerceAtLeast(0)
                    return siblings to idx
                }
            }
            return listOf(fallbackItem) to 0
        }

        val siblings = runCatching {
            val resolver = context.contentResolver
            val volumeUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            val bucketCol = MediaStore.Audio.Media.BUCKET_ID
            val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.RELATIVE_PATH else null

            var bucketId: String? = null
            var relativePath: String? = null
            var mediaId: Long? = runCatching { ContentUris.parseId(uri) }.getOrNull()

            val probeProjection = buildList {
                add(MediaStore.Audio.Media._ID)
                add(bucketCol)
                if (relCol != null) add(relCol)
            }.toTypedArray()

            resolver.query(uri, probeProjection, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idIdx = c.getColumnIndex(MediaStore.Audio.Media._ID)
                    if (idIdx >= 0 && mediaId == null) mediaId = c.getLong(idIdx)
                    val bIdx = c.getColumnIndex(bucketCol)
                    if (bIdx >= 0) bucketId = c.getString(bIdx)
                    if (relCol != null) {
                        val rIdx = c.getColumnIndex(relCol)
                        if (rIdx >= 0) relativePath = c.getString(rIdx)
                    }
                }
            }

            if (bucketId.isNullOrBlank() && relativePath.isNullOrBlank() && mediaId != null) {
                resolver.query(
                    volumeUri,
                    probeProjection,
                    "${MediaStore.Audio.Media._ID} = ?",
                    arrayOf(mediaId.toString()),
                    null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val bIdx = c.getColumnIndex(bucketCol)
                        if (bIdx >= 0) bucketId = c.getString(bIdx)
                        if (relCol != null) {
                            val rIdx = c.getColumnIndex(relCol)
                            if (rIdx >= 0) relativePath = c.getString(rIdx)
                        }
                    }
                }
            }

            val folderWhere = when {
                relCol != null && !relativePath.isNullOrBlank() ->
                    "$relCol = ? AND ${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.SIZE} > 0"
                !bucketId.isNullOrBlank() ->
                    "$bucketCol = ? AND ${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.SIZE} > 0"
                else -> null
            }
            val folderArg = when {
                relCol != null && !relativePath.isNullOrBlank() -> relativePath!!
                !bucketId.isNullOrBlank() -> bucketId!!
                else -> null
            }

            if (folderWhere != null && folderArg != null) {
                val results = mutableListOf<AudioItem>()
                val seen = HashSet<String>()
                val queryProjection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.MIME_TYPE
                )
                resolver.query(
                    volumeUri,
                    queryProjection,
                    folderWhere,
                    arrayOf(folderArg),
                    "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
                )?.use { cursor ->
                    val idC = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleC = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistC = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumC = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durC = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val sizeC = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val dispC = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val mimeC = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val rowId = cursor.getLong(idC)
                        val size = cursor.getLong(sizeC)
                        if (size <= 0L) continue
                        val disp = if (dispC >= 0) cursor.getString(dispC).orEmpty() else ""
                        val mime = if (mimeC >= 0) cursor.getString(mimeC).orEmpty() else ""
                        if (!mime.startsWith("audio/", true) && !mime.equals("application/ogg", true) && !isPlayableAudioName(disp)) {
                            continue
                        }
                        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, rowId)
                        if (!seen.add(contentUri.toString())) continue

                        val isTarget = (mediaId != null && rowId == mediaId) || contentUri == fallbackItem.uri
                        if (isTarget) {
                            results.add(fallbackItem)
                        } else {
                            val title = cursor.getString(titleC) ?: "Unknown Track"
                            val artist = cursor.getString(artistC) ?: "Unknown Artist"
                            val album = cursor.getString(albumC) ?: "Unknown Album"
                            results.add(
                                AudioItem(
                                    id = rowId.toString(),
                                    title = if (title == "<unknown>") disp.substringBeforeLast('.').ifBlank { "Track $rowId" } else title,
                                    artist = if (artist == "<unknown>") "Unknown Artist" else artist,
                                    album = if (album == "<unknown>") "Unknown Album" else album,
                                    durationMs = cursor.getLong(durC),
                                    uri = contentUri,
                                    sizeBytes = size
                                )
                            )
                        }
                    }
                }
                results
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())

        if (siblings.isNotEmpty()) {
            val idx = siblings.indexOfFirst {
                it.id == fallbackItem.id || it.uri == fallbackItem.uri
            }
            if (idx >= 0) return siblings to idx
            val merged = (listOf(fallbackItem) + siblings).distinctBy { it.uri.toString() }
            return merged to 0
        }
        return listOf(fallbackItem) to 0
    }

    fun parsePickedUri(context: Context, uri: Uri): AudioItem {
        var title = "Imported Audio"
        var artist = "Local File"
        var album = "Imports"
        var duration = 0L

        // Attempt to extract display name
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        val displayName = cursor.getString(nameIdx)
                        if (!displayName.isNullOrBlank()) {
                            title = displayName.substringBeforeLast(".")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Extract metadata using MediaMetadataRetriever
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val extractedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val extractedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val extractedAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val extractedDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

            if (!extractedTitle.isNullOrBlank()) title = extractedTitle
            if (!extractedArtist.isNullOrBlank()) artist = extractedArtist
            if (!extractedAlbum.isNullOrBlank()) album = extractedAlbum
            if (!extractedDuration.isNullOrBlank()) duration = extractedDuration.toLongOrNull() ?: 0L

            retriever.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val id = uri.hashCode().toString()
        return AudioItem(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            uri = uri
        )
    }

    fun formatMs(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSecs = ms / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format("%02d:%02d", mins, secs)
    }
}
