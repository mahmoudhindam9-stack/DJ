package com.example.quran

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.example.QueueSheet
import com.example.model.AudioItem
import com.example.model.Playlist
import com.example.player.AudioPlayerController
import com.example.onlinemusic.enqueueOnlineSong
import com.example.room.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private data class QuranMoshaf(
    val id: String,
    val name: String,
    val server: String,
    val surahs: List<Int>
)
private data class QuranReciter(val id: String, val name: String, val moshafs: List<QuranMoshaf>)

private class QuranRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun reciters(): List<QuranReciter> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://mp3quran.net/api/v3/reciters?language=ar")
            .header("Accept", "application/json")
            .header("User-Agent", "DJ-Music-Player/3.9")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("MP3Quran HTTP " + response.code)
            val root = JSONObject(response.body?.string().orEmpty())
            val array = root.optJSONArray("reciters") ?: error("لم تصل قائمة القراء")
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val reads = item.optJSONArray("moshaf") ?: continue
                    val moshafs = buildList {
                        for (j in 0 until reads.length()) {
                            val read = reads.optJSONObject(j) ?: continue
                            val server = read.optString("server").trim().let { if (it.endsWith("/")) it else it + "/" }
                            if (!server.startsWith("https://")) continue
                            val rawSurahs = read.optString("surah_list")
                            val ids = if (rawSurahs.isNotBlank()) {
                                rawSurahs.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..114 }.distinct().sorted()
                            } else {
                                (1..read.optInt("surah_total", 114).coerceIn(1, 114)).toList()
                            }
                            if (ids.isNotEmpty()) add(QuranMoshaf(read.optString("id"), read.optString("name").ifBlank { "رواية غير محددة" }, server, ids))
                        }
                    }
                    if (moshafs.isNotEmpty()) add(QuranReciter(item.optString("id"), item.optString("name").ifBlank { "قارئ" }, moshafs))
                }
            }.sortedBy { it.name }
        }
    }

    suspend fun download(url: String, resolver: android.content.ContentResolver, destination: Uri) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", "DJ-Music-Player/3.9").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("فشل التنزيل: HTTP " + response.code)
            val body = response.body ?: error("التسجيل فارغ")
            resolver.openOutputStream(destination)?.use { output -> body.byteStream().use { it.copyTo(output) } }
                ?: error("تعذر فتح مكان الحفظ")
        }
    }
}

private val quranSurahNames = listOf(
    "الفاتحة","البقرة","آل عمران","النساء","المائدة","الأنعام","الأعراف","الأنفال","التوبة","يونس",
    "هود","يوسف","الرعد","إبراهيم","الحجر","النحل","الإسراء","الكهف","مريم","طه",
    "الأنبياء","الحج","المؤمنون","النور","الفرقان","الشعراء","النمل","القصص","العنكبوت","الروم",
    "لقمان","السجدة","الأحزاب","سبأ","فاطر","يس","الصافات","ص","الزمر","غافر",
    "فصلت","الشورى","الزخرف","الدخان","الجاثية","الأحقاف","محمد","الفتح","الحجرات","ق",
    "الذاريات","الطور","النجم","القمر","الرحمن","الواقعة","الحديد","المجادلة","الحشر","الممتحنة",
    "الصف","الجمعة","المنافقون","التغابن","الطلاق","التحريم","الملك","القلم","الحاقة","المعارج",
    "نوح","الجن","المزمل","المدثر","القيامة","الإنسان","المرسلات","النبأ","النازعات","عبس",
    "التكوير","الانفطار","المطففين","الانشقاق","البروج","الطارق","الأعلى","الغاشية","الفجر","البلد",
    "الشمس","الليل","الضحى","الشرح","التين","العلق","القدر","البينة","الزلزلة","العاديات",
    "القارعة","التكاثر","العصر","الهمزة","الفيل","قريش","الماعون","الكوثر","الكافرون","النصر",
    "المسد","الإخلاص","الفلق","الناس"
)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun QuranScreen(
    playerController: AudioPlayerController,
    playlists: List<Playlist>,
    audioLibrary: androidx.compose.runtime.snapshots.SnapshotStateList<AudioItem>,
    playlistRepo: PlaylistRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { QuranRepository() }
    var reciters by remember { mutableStateOf<List<QuranReciter>>(emptyList()) }
    var selectedReciter by remember { mutableStateOf<QuranReciter?>(null) }
    var selectedMoshaf by remember { mutableStateOf<QuranMoshaf?>(null) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingDownload by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val downloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mpeg")) { uri ->
        val pending = pendingDownload
        pendingDownload = null
        if (uri != null && pending != null) scope.launch {
            message = "جاري تنزيل " + pending.first
            runCatching { repository.download(pending.second, context.contentResolver, uri) }
                .onSuccess { message = "تم تنزيل السورة بنجاح" }
                .onFailure { message = it.message ?: "فشل تنزيل السورة" }
        }
    }

    suspend fun loadReciters() {
        loading = true
        error = null
        runCatching { repository.reciters() }
            .onSuccess { reciters = it }
            .onFailure { error = it.message ?: "تعذر تحميل القراء" }
        loading = false
    }
    LaunchedEffect(Unit) { loadReciters() }

    fun audioUrl(moshaf: QuranMoshaf, surah: Int): String =
        moshaf.server + surah.toString().padStart(3, '0') + ".mp3"

    fun audioItem(reciter: QuranReciter, moshaf: QuranMoshaf, surah: Int): AudioItem =
        AudioItem(
            id = "quran:" + reciter.id + ":" + moshaf.id + ":" + surah,
            title = quranSurahNames[surah - 1],
            artist = reciter.name,
            album = moshaf.name,
            durationMs = 0L,
            uri = Uri.parse(audioUrl(moshaf, surah))
        )

    fun playSurah(reciter: QuranReciter, moshaf: QuranMoshaf, surah: Int) {
        val queue = moshaf.surahs.map { audioItem(reciter, moshaf, it) }
        val index = moshaf.surahs.indexOf(surah).coerceAtLeast(0)
        playerController.play(queue[index], queue)
        message = "تشغيل " + quranSurahNames[surah - 1] + " بصوت " + reciter.name
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectedMoshaf != null || selectedReciter != null) {
                IconButton(onClick = {
                    if (selectedMoshaf != null) selectedMoshaf = null else selectedReciter = null
                    message = null
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
            } else {
                Icon(Icons.Filled.MenuBook, null, Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("القرآن الكريم", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    selectedMoshaf?.name ?: selectedReciter?.name ?: "اختر القارئ ثم الرواية ثم السورة",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { showQueue = true }) { Icon(Icons.Filled.QueueMusic, "قائمة التشغيل") }
            IconButton(onClick = { scope.launch { loadReciters() } }) { Icon(Icons.Filled.Refresh, "تحديث") }
        }

        when {
            loading && reciters.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null && reciters.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "حدث خطأ", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { scope.launch { loadReciters() } }) { Text("إعادة المحاولة") }
                }
            }
            selectedMoshaf != null && selectedReciter != null -> {
                val reciter = selectedReciter!!
                val moshaf = selectedMoshaf!!
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(moshaf.surahs, key = { it }) { surah ->
                        val item = audioItem(reciter, moshaf, surah)
                        val active = playerController.currentSong?.id == item.id
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(surah.toString().padStart(3, '0'), style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(quranSurahNames[surah - 1], fontWeight = FontWeight.SemiBold)
                                    Text(reciter.name + " • " + moshaf.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { playSurah(reciter, moshaf, surah) }) {
                                    Icon(if (active && playerController.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "تشغيل")
                                }
                                IconButton(onClick = { playerController.enqueueOnlineSong(item); message = "أضيفت السورة لقائمة التشغيل" }) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "إضافة للقائمة")
                                }
                                IconButton(onClick = {
                                    pendingDownload = quranSurahNames[surah - 1] to audioUrl(moshaf, surah)
                                    downloadLauncher.launch(quranSurahNames[surah - 1] + ".mp3")
                                }) { Icon(Icons.Filled.Download, "تنزيل") }
                            }
                        }
                    }
                }
            }
            selectedReciter != null -> {
                val reciter = selectedReciter!!
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reciter.moshafs, key = { it.id }) { moshaf ->
                        Card(Modifier.fillMaxWidth().clickable { selectedMoshaf = moshaf }) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.MenuBook, null)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(moshaf.name, fontWeight = FontWeight.SemiBold)
                                    Text(moshaf.surahs.size.toString() + " سورة متاحة", style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(Icons.Filled.ChevronRight, null)
                            }
                        }
                    }
                }
            }
            else -> {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    singleLine = true,
                    label = { Text("ابحث عن قارئ") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(reciters.filter { it.name.contains(query.trim(), true) }, key = { it.id }) { reciter ->
                        Card(Modifier.fillMaxWidth().clickable { selectedReciter = reciter }) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.RecordVoiceOver, null)
                                Spacer(Modifier.width(12.dp))
                                Text(reciter.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text(reciter.moshafs.size.toString() + " رواية", style = MaterialTheme.typography.bodySmall)
                                Icon(Icons.Filled.ChevronRight, null)
                            }
                        }
                    }
                }
            }
        }
        message?.let { Text(it, Modifier.fillMaxWidth().padding(10.dp), style = MaterialTheme.typography.bodySmall) }
    }

    if (showQueue) {
        QueueSheet(
            controller = playerController,
            playlists = playlists,
            library = audioLibrary,
            playlistRepo = playlistRepo,
            onDismiss = { showQueue = false },
            onSelect = { song -> playerController.play(song, null); showQueue = false }
        )
    }
}
