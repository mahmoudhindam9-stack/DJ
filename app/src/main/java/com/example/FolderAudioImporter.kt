package com.example

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.model.AudioItem
import com.example.utils.MusicScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FolderAudioImporter {
    private val supportedExtensions = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "3gp", "mid", "midi")

    suspend fun scan(
        context: Context,
        treeUri: Uri,
        maxFiles: Int = 5000,
        includeSubfolders: Boolean = true
    ): List<AudioItem> = withContext(Dispatchers.IO) {
        val result = ArrayList<AudioItem>()
        val seenKeys = HashSet<String>()
        val resolver = context.contentResolver
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return@withContext result
        val rootChildren = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId)
        }.getOrNull() ?: return@withContext result

        fun walk(childrenUri: Uri) {
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
                    val id = cursor.getString(idCol) ?: continue
                    val name = cursor.getString(nameCol) ?: ""
                    val mime = cursor.getString(mimeCol) ?: ""
                    val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else -1L
                    if (name.startsWith(".")) continue
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (includeSubfolders) {
                            val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, id)
                            walk(childUri)
                        }
                    } else if (
                        size != 0L &&
                        !mime.startsWith("image/", ignoreCase = true) &&
                        !mime.startsWith("video/", ignoreCase = true) &&
                        (mime.startsWith("audio/", ignoreCase = true) ||
                            mime.equals("application/ogg", ignoreCase = true) ||
                            isAudioName(name))
                    ) {
                        runCatching {
                            val item = MusicScanner.parsePickedUri(context, documentUri)
                            val key = "${item.id}|${item.uri}"
                            if (seenKeys.add(key)) {
                                result.add(item)
                            }
                        }
                    }
                }
            }
        }

        walk(rootChildren)
        result
            .distinctBy { it.id.ifBlank { it.uri.toString() } }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    private fun isAudioName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in supportedExtensions
}
