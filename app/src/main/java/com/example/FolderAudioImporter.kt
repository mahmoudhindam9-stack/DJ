package com.example

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.model.AudioItem
import com.example.utils.MusicScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object FolderAudioImporter {
    private val supportedExtensions = setOf(
        "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "3gp", "mid", "midi", "wma", "weba"
    )

    suspend fun scan(
        context: Context,
        treeUri: Uri,
        maxFiles: Int = 5000,
        includeSubfolders: Boolean = true
    ): List<AudioItem> = withContext(Dispatchers.IO) {
        val result = ArrayList<AudioItem>()
        val seenUris = HashSet<String>()
        val resolver = context.contentResolver
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return@withContext result
        val rootChildren = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId)
        }.getOrNull() ?: return@withContext result

        val rootFolderName = rootId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Local Folder" }

        fun walk(childrenUri: Uri, currentFolderName: String) {
            if (result.size >= maxFiles) return
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                ),
                null,
                null,
                "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idCol = 0
                val nameCol = 1
                val mimeCol = 2
                val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                while (cursor.moveToNext() && result.size < maxFiles) {
                    val docId = cursor.getString(idCol) ?: continue
                    val name = cursor.getString(nameCol) ?: ""
                    val mime = cursor.getString(mimeCol) ?: ""
                    val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
                    if (name.startsWith(".")) continue
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (includeSubfolders) {
                            val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                            walk(childUri, name.ifBlank { currentFolderName })
                        }
                    } else if (
                        !mime.startsWith("image/", ignoreCase = true) &&
                        !mime.startsWith("video/", ignoreCase = true) &&
                        (mime.startsWith("audio/", ignoreCase = true) ||
                            mime.equals("application/ogg", ignoreCase = true) ||
                            mime.equals("application/x-ogg", ignoreCase = true) ||
                            mime.equals("application/flac", ignoreCase = true) ||
                            isAudioName(name))
                    ) {
                        val uriStr = documentUri.toString()
                        if (seenUris.add(uriStr)) {
                            val baseName = name.substringBeforeLast('.').trim().ifBlank { name.ifBlank { "Audio Track" } }
                            val cleanBase = baseName.replace(Regex("^[0-9]+[\\s.\\-_)]+"), "").trim().ifBlank { baseName }
                            val artist: String
                            val title: String
                            if (cleanBase.contains(" - ")) {
                                artist = cleanBase.substringBefore(" - ").trim().ifBlank { currentFolderName }
                                title = cleanBase.substringAfter(" - ").trim().ifBlank { cleanBase }
                            } else {
                                artist = currentFolderName
                                title = cleanBase
                            }
                            result.add(
                                AudioItem(
                                    id = uriStr.hashCode().toString(),
                                    title = title,
                                    artist = artist,
                                    album = currentFolderName,
                                    durationMs = 0L,
                                    uri = documentUri,
                                    sizeBytes = size.coerceAtLeast(0L)
                                )
                            )
                        }
                    }
                }
            }
        }

        walk(rootChildren, rootFolderName)
        result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    private fun isAudioName(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in supportedExtensions ||
            MusicScanner.isPlayableAudioName(name)
}
