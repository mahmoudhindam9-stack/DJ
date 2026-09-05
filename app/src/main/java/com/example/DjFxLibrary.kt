package com.example

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class ImportedDjFx(val name: String, val file: File)

private class DjFxLibraryEngine(private val context: Context) {
    private val root = File(context.filesDir, "dj_fx_library").apply { mkdirs() }
    private var player: MediaPlayer? = null

    fun list(): List<ImportedDjFx> = root.listFiles()
        ?.filter { it.isFile && it.length() > 128 }
        ?.sortedBy { it.name.lowercase() }
        ?.map { ImportedDjFx(it.nameWithoutExtension, it) }
        ?: emptyList()

    suspend fun importUris(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var count = 0
        val resolver = context.contentResolver
        uris.forEachIndexed { index, uri ->
            runCatching {
                val displayName = resolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: "dj_fx_${System.currentTimeMillis()}_$index.wav"
                val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val target = uniqueFile(File(root, safeName))
                resolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching
                if (target.length() > 128) count++ else target.delete()
            }
        }
        count
    }

    private fun uniqueFile(file: File): File {
        if (!file.exists()) return file
        val base = file.nameWithoutExtension
        val ext = file.extension
        var i = 2
        while (true) {
            val candidate = File(root, "$base-$i.$ext")
            if (!candidate.exists()) return candidate
            i++
        }
    }

    fun play(sample: ImportedDjFx) {
        player?.release()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(sample.file.absolutePath)
            setOnCompletionListener { mp -> mp.release(); if (player === mp) player = null }
            setOnErrorListener { mp, _, _ -> mp.release(); if (player === mp) player = null; true }
            prepareAsync()
            setOnPreparedListener { it.start() }
        }
    }

    fun release() {
        player?.release()
        player = null
    }
}

@Composable
fun DjFxLibraryCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { DjFxLibraryEngine(context) }
    val sounds = remember { mutableStateListOf<ImportedDjFx>().apply { addAll(engine.list()) } }
    var status by remember { mutableStateOf("حمّل مؤثرات قانونية ثم استوردها هنا") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uris.forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        scope.launch {
            status = "جاري استيراد ${uris.size} ملف..."
            val count = engine.importUris(uris)
            sounds.clear()
            sounds.addAll(engine.list())
            status = if (count > 0) "تمت إضافة $count مؤثرات DJ" else "لم يتم استيراد أي ملف صوتي صالح"
        }
    }

    DisposableEffect(engine) {
        onDispose { engine.release() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("DJ FX LIBRARY", fontWeight = FontWeight.Bold)
                    Text(
                        "Pixabay للاستخدام التجاري • Freesound بشرط CC0 • استيراد وتشغيل محلي",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { picker.launch(arrayOf("audio/*")) }) {
                    Text("استيراد FX", fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://pixabay.com/sound-effects/search/dj/")))
                }) { Text("Pixabay") }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://freesound.org/search/?q=dj&f=license:%22Creative+Commons+0%22")))
                }) { Text("Freesound CC0") }
            }
            Spacer(Modifier.height(6.dp))
            Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sounds.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sounds, key = { it.file.absolutePath }) { sound ->
                        OutlinedButton(onClick = { engine.play(sound) }) {
                            Text(sound.name, maxLines = 1, fontSize = 10.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "ملاحظة: اختر ملفات CC0 من Freesound، أو ملفات مسموحة بالاستخدام من Pixabay. لا تعِد توزيع الملف الخام كمكتبة مستقلة.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
