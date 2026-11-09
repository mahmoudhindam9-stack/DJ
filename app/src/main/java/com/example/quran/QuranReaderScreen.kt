package com.example.quran

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.AudioPlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private data class ReaderVerse(val number: Int, val text: String, val weight: Int)

private class QuranReaderRepository {
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    suspend fun loadSurah(number: Int): List<ReaderVerse> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.alquran.cloud/v1/surah/$number").header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("تعذر تحميل نص السورة (HTTP " + response.code + ")")
            val data = JSONObject(response.body?.string().orEmpty()).optJSONObject("data") ?: error("استجابة المصحف غير مكتملة")
            val ayahs = data.optJSONArray("ayahs") ?: error("لم يتم العثور على آيات السورة")
            buildList {
                for (i in 0 until ayahs.length()) {
                    val ayah = ayahs.optJSONObject(i) ?: continue
                    val text = ayah.optString("text").trim()
                    if (text.isNotBlank()) add(ReaderVerse(ayah.optInt("numberInSurah", i + 1), text, text.split(Regex("\\s+")).count { it.isNotBlank() }.coerceAtLeast(1)))
                }
            }
        }
    }
}

private val readerSurahNames = listOf(
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

@Composable
fun QuranReaderScreen(playerController: AudioPlayerController, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val song = playerController.currentSong
    val surahNumber = remember(song?.id) { song?.id?.takeIf { it.startsWith("quran:") }?.substringAfterLast(":")?.toIntOrNull()?.takeIf { it in 1..114 } }
    val repository = remember { QuranReaderRepository() }
    val listState = rememberLazyListState()
    var verses by remember(surahNumber) { mutableStateOf<List<ReaderVerse>>(emptyList()) }
    var loading by remember(surahNumber) { mutableStateOf(true) }
    var error by remember(surahNumber) { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableLongStateOf(playerController.currentPositionMs) }
    var durationMs by remember { mutableLongStateOf(playerController.durationMs) }

    LaunchedEffect(surahNumber) {
        verses = emptyList()
        error = null
        loading = true
        if (surahNumber != null) runCatching { repository.loadSurah(surahNumber) }
            .onSuccess { verses = it }
            .onFailure { error = it.message ?: "تعذر تحميل المصحف" }
        else error = "لا توجد سورة من صفحة القرآن قيد التشغيل"
        loading = false
    }
    LaunchedEffect(playerController) {
        while (true) {
            positionMs = playerController.currentPositionMs
            durationMs = playerController.durationMs
            delay(250)
        }
    }

    // MP3Quran's full-surah stream has no verse timestamps. This weighted
    // progress guide is approximate, not exact word-level synchronization.
    val activeVerseIndex = remember(verses, positionMs, durationMs) {
        if (verses.isEmpty() || durationMs <= 0L) -1 else {
            val totalWeight = verses.sumOf { it.weight }.coerceAtLeast(1)
            val target = (positionMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0) * totalWeight
            var accumulated = 0
            verses.indexOfFirst { verse -> accumulated += verse.weight; accumulated >= target }.coerceIn(0, verses.lastIndex)
        }
    }
    LaunchedEffect(activeVerseIndex) { if (activeVerseIndex >= 0) listState.animateScrollToItem(activeVerseIndex + 1) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(surahNumber?.let { readerSurahNames[it - 1] } ?: "المصحف الشريف", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(song?.artist.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
            }
        }
        if (durationMs > 0L && verses.isNotEmpty()) LinearProgressIndicator(progress = { (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ", fontSize = 24.sp, fontWeight = FontWeight.Medium)
                            Text("استمع واقرأ مع التلاوة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                itemsIndexed(verses, key = { _, verse -> verse.number }) { index, verse ->
                    val active = index == activeVerseIndex
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), tonalElevation = if (active) 3.dp else 0.dp) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
                            Text(verse.text, modifier = Modifier.fillMaxWidth(), fontSize = 25.sp, lineHeight = 48.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, textDecoration = if (active) TextDecoration.Underline else TextDecoration.None)
                            Text("۞ ${verse.number}", modifier = Modifier.align(Alignment.End).padding(top = 5.dp), style = MaterialTheme.typography.labelSmall, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
