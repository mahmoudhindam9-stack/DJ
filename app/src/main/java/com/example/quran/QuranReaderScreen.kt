package com.example.quran

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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

private fun arabicDigits(value: Int): String = value.toString().map { ch ->
    if (ch in '0'..'9') ('٠'.code + (ch - '0')).toChar() else ch
}.joinToString("")

@Composable
fun QuranReaderScreen(playerController: AudioPlayerController, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val song = playerController.currentSong
    val surahNumber = remember(song?.id) { song?.id?.takeIf { it.startsWith("quran:") }?.substringAfterLast(":")?.toIntOrNull()?.takeIf { it in 1..114 } }
    val repository = remember { QuranReaderRepository() }
    val scrollState = rememberScrollState()
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

    // Full-surah streams do not provide verified ayah timestamps; highlighting remains approximate.
    val activeVerseIndex = remember(verses, positionMs, durationMs) {
        if (verses.isEmpty() || durationMs <= 0L) -1 else {
            val totalWeight = verses.sumOf { it.weight }.coerceAtLeast(1)
            val target = (positionMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0) * totalWeight
            var accumulated = 0
            verses.indexOfFirst { verse -> accumulated += verse.weight; accumulated >= target }.coerceIn(0, verses.lastIndex)
        }
    }

    val pageText = remember(verses, activeVerseIndex) {
        buildAnnotatedString {
            verses.forEachIndexed { index, verse ->
                if (index > 0) append("  ")
                val verseText = verse.text + " "
                if (index == activeVerseIndex) {
                    pushStyle(SpanStyle(color = Color(0xFFB3261E), fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline))
                    append(verseText)
                    pop()
                } else append(verseText)
                pushStyle(SpanStyle(color = Color(0xFF9A7136), fontWeight = FontWeight.Bold))
                append("﴿" + arabicDigits(verse.number) + "﴾")
                pop()
                append("  ")
            }
        }
    }

    LaunchedEffect(activeVerseIndex, verses, scrollState.maxValue) {
        if (activeVerseIndex >= 0 && verses.isNotEmpty()) {
            val offset = verses.take(activeVerseIndex).sumOf { it.text.length + 9 }.coerceAtMost((pageText.length - 1).coerceAtLeast(0))
            scrollState.animateScrollTo((offset * 1.2f).toInt().coerceIn(0, scrollState.maxValue))
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).padding(vertical = 10.dp)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFFFFBEA), shape = RoundedCornerShape(2.dp)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF9A7136)) }
                error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(error.orEmpty(), color = Color(0xFF8B1E16), textAlign = TextAlign.Center) }
                else -> Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 14.dp)) {
                    Text(
                        surahNumber?.let { readerSurahNames[it - 1] } ?: "المصحف الشريف",
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        color = Color(0xFF8C672F),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState)) {
                        BasicText(
                            text = pageText,
                            modifier = Modifier.fillMaxWidth(),
                            style = TextStyle(
                                color = Color(0xFF17130F),
                                fontFamily = FontFamily.Serif,
                                fontSize = 28.sp,
                                lineHeight = 58.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Justify,
                                textDirection = TextDirection.ContentOrRtl
                            )
                        )
                    }
                    if (durationMs > 0L) {
                        LinearProgressIndicator(
                            progress = { (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            color = Color(0xFF9A7136),
                            trackColor = Color(0xFFE7DCC0)
                        )
                    }
                }
            }
        }
    }
}
