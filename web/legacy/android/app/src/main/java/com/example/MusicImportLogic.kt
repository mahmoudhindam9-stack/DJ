package com.example

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.example.model.AudioItem

data class ImportFile(val audio: AudioItem, val path: String)

class ImportFolder(val name: String, val path: String) {
    val subfolders = mutableMapOf<String, ImportFolder>()
    val files = mutableListOf<ImportFile>()
}

object MusicImportLogic {

    fun scanDeviceFiles(context: Context): List<ImportFile> {
        val list = mutableListOf<ImportFile>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
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
            MediaStore.Audio.Media.DATA
        )
        
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        
        try {
            context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown Track"
                    val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val album = cursor.getString(albumCol) ?: "Unknown Album"
                    val duration = cursor.getLong(durationCol)
                    val size = cursor.getLong(sizeCol)
                    val data = cursor.getString(dataCol) ?: ""
                    
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    
                    val item = AudioItem(
                        id = id.toString(),
                        title = if (title == "<unknown>") "Track $id" else title,
                        artist = if (artist == "<unknown>") "Unknown Artist" else artist,
                        album = if (album == "<unknown>") "Unknown Album" else album,
                        durationMs = duration,
                        uri = contentUri,
                        sizeBytes = size
                    )
                    list.add(ImportFile(item, data))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
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
            when(char) {
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
