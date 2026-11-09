package com.example.quran

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
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

private data class ReaderVerse(
    val number: Int,
    val numberInSurah: Int,
    val text: String,
    val weight: Int,
    val page: Int,
    val juz: Int,
    val surahNumber: Int,
    val surahName: String
)

private data class ReaderPage(val number: Int, val juz: Int, val verses: List<ReaderVerse>)

private class QuranReaderRepository {
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    private fun parseVerse(ayah: JSONObject, fallbackSurahNumber: Int, fallbackSurahName: String): ReaderVerse? {
        val text = ayah.optString("text").trim()
        if (text.isBlank()) return null
        val surah = ayah.optJSONObject("surah")
        val surahNumber = surah?.optInt("number", fallbackSurahNumber) ?: fallbackSurahNumber
        val surahName = surah?.optString("name").orEmpty().ifBlank { fallbackSurahName }
        return ReaderVerse(
            number = ayah.optInt("number"),
            numberInSurah = ayah.optInt("numberInSurah"),
            text = text,
            weight = text.split(Regex("\\s+")).count { it.isNotBlank() }.coerceAtLeast(1),
            page = ayah.optInt("page"),
            juz = ayah.optInt("juz"),
            surahNumber = surahNumber,
            surahName = surahName
        )
    }

    suspend fun loadSurah(number: Int): List<ReaderVerse> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.alquran.cloud/v1/surah/" + number).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("تعذر تحميل بيانات السورة (HTTP " + response.code + ")")
            val data = JSONObject(response.body?.string().orEmpty()).optJSONObject("data") ?: error("استجابة المصحف غير مكتملة")
            val surahName = data.optString("name", "سورة")
            val ayahs = data.optJSONArray("ayahs") ?: error("لم يتم العثور على آيات السورة")
            buildList {
                for (i in 0 until ayahs.length()) {
                    val ayah = ayahs.optJSONObject(i) ?: continue
                    parseVerse(ayah, number, surahName)?.let(::add)
                }
            }
        }
    }

    suspend fun loadPage(number: Int): ReaderPage = withContext(Dispatchers.IO) {
        val pageNumber = number.coerceIn(1, 604)
        val request = Request.Builder().url("https://api.alquran.cloud/v1/page/" + pageNumber + "/quran-uthmani").header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("تعذر تحميل صفحة المصحف (HTTP " + response.code + ")")
            val data = JSONObject(response.body?.string().orEmpty()).optJSONObject("data") ?: error("استجابة صفحة المصحف غير مكتملة")
            val ayahs = data.optJSONArray("ayahs") ?: error("لا توجد آيات في هذه الصفحة")
            val verses = buildList {
                for (i in 0 until ayahs.length()) {
                    val ayah = ayahs.optJSONObject(i) ?: continue
                    parseVerse(ayah, 1, "")?.let(::add)
                }
            }
            if (verses.isEmpty()) error("لم يتم العثور على نص الصفحة")
            ReaderPage(pageNumber, verses.first().juz, verses)
        }
    }
}

private fun arabicDigits(value: Int): String = value.toString().map { ch ->
    if (ch in '0'..'9') ('٠'.code + (ch - '0')).toChar() else ch
}.joinToString("")

private val mushafPaper = Color(0xFFFFF8E8)
private val mushafInk = Color(0xFF211B14)
private val mushafGold = Color(0xFF9A7136)
private val mushafGreen = Color(0xFF193D31)

@Composable
fun QuranReaderScreen(playerController: AudioPlayerController, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val song = playerController.currentSong
    val surahNumber = remember(song?.id) {
        song?.id?.takeIf { it.startsWith("quran:") }?.substringAfterLast(":")?.toIntOrNull()?.takeIf { it in 1..114 }
    }
    val repository = remember { QuranReaderRepository() }
    val scrollState = rememberScrollState()
    var surahVerses by remember(surahNumber) { mutableStateOf<List<ReaderVerse>>(emptyList()) }
    var page by remember(surahNumber) { mutableStateOf<Int?>(null) }
    var pageData by remember { mutableStateOf<ReaderPage?>(null) }
    var loading by remember(surahNumber) { mutableStateOf(true) }
    var pageLoading by remember { mutableStateOf(false) }
    var error by remember(surahNumber) { mutableStateOf<String?>(null) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableLongStateOf(playerController.currentPositionMs) }
    var durationMs by remember { mutableLongStateOf(playerController.durationMs) }

    LaunchedEffect(surahNumber) {
        surahVerses = emptyList()
        pageData = null
        page = null
        error = null
        loading = true
        if (surahNumber != null) {
            runCatching { repository.loadSurah(surahNumber) }
                .onSuccess { loaded ->
                    surahVerses = loaded
                    page = loaded.firstOrNull()?.page?.takeIf { it in 1..604 }
                }
                .onFailure { error = it.message ?: "تعذر تحميل المصحف" }
        } else error = "لا توجد سورة من صفحة القرآن قيد التشغيل"
        loading = false
    }

    LaunchedEffect(playerController) {
        while (true) {
            positionMs = playerController.currentPositionMs
            durationMs = playerController.durationMs
            delay(250)
        }
    }

    LaunchedEffect(page) {
        val requestedPage = page ?: return@LaunchedEffect
        pageLoading = true
        pageError = null
        runCatching { repository.loadPage(requestedPage) }
            .onSuccess { pageData = it }
            .onFailure {
                pageError = it.message ?: "تعذر تحميل الصفحة"
                pageData = surahVerses.filter { verse -> verse.page == requestedPage }
                    .takeIf { it.isNotEmpty() }
                    ?.let { ReaderPage(requestedPage, it.first().juz, it) }
            }
        pageLoading = false
        scrollState.scrollTo(0)
    }

    // MP3Quran full-surah streams do not provide verified ayah timestamps; highlighting is approximate.
    val activeVerse = remember(surahVerses, positionMs, durationMs) {
        if (surahVerses.isEmpty() || durationMs <= 0L) null else {
            val totalWeight = surahVerses.sumOf { it.weight }.coerceAtLeast(1)
            val target = (positionMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0) * totalWeight
            var accumulated = 0
            surahVerses.firstOrNull { verse ->
                accumulated += verse.weight
                accumulated >= target
            } ?: surahVerses.lastOrNull()
        }
    }

    LaunchedEffect(activeVerse?.page, playerController.isPlaying) {
        val playbackPage = activeVerse?.page?.takeIf { it in 1..604 }
        if (playerController.isPlaying && playbackPage != null && playbackPage != page) page = playbackPage
    }

    val displayedVerses = pageData?.verses.orEmpty()
    val pageText = remember(displayedVerses, activeVerse?.number) {
        buildAnnotatedString {
            var previousSurah = -1
            displayedVerses.forEachIndexed { index, verse ->
                if (verse.surahNumber != previousSurah) {
                    if (index > 0) append("\\n\\n")
                    if (verse.numberInSurah == 1) {
                        pushStyle(SpanStyle(color = mushafGreen, fontWeight = FontWeight.Bold, fontSize = 22.sp))
                        append("۞  " + verse.surahName + "  ۞")
                        pop()
                        append("\\n")
                        if (verse.surahNumber != 1 && verse.surahNumber != 9) {
                            pushStyle(SpanStyle(color = mushafGold, fontWeight = FontWeight.Medium, fontSize = 21.sp))
                            append("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ")
                            pop()
                            append("\\n")
                        }
                    }
                    previousSurah = verse.surahNumber
                } else if (index > 0) append(" ")
                if (verse.number == activeVerse?.number) {
                    pushStyle(SpanStyle(color = Color(0xFF9B2F22), fontWeight = FontWeight.Bold))
                    append(verse.text)
                    pop()
                } else append(verse.text)
                pushStyle(SpanStyle(color = mushafGold, fontWeight = FontWeight.Bold, fontSize = 20.sp))
                append("  ۞" + arabicDigits(verse.numberInSurah) + "۞  ")
                pop()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF17271F)).padding(horizontal = 9.dp, vertical = 8.dp)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = mushafPaper,
            shape = RoundedCornerShape(3.dp),
            shadowElevation = 5.dp
        ) {
            Column(Modifier.fillMaxSize().padding(7.dp).border(2.dp, mushafGold, RoundedCornerShape(2.dp)).padding(5.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = mushafGreen)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("الْمُصْحَفُ الشَّرِيف", color = mushafGreen, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(
                            pageData?.verses?.firstOrNull()?.surahName ?: surahNumber?.let { "سورة رقم " + arabicDigits(it) }.orEmpty(),
                            color = mushafGold,
                            fontSize = 12.sp
                        )
                    }
                    Icon(Icons.Filled.MenuBook, contentDescription = null, tint = mushafGold)
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f), color = mushafGold.copy(alpha = 0.65f))
                    Text("  ۞  " + (page?.let(::arabicDigits) ?: "—") + "  ۞  ", color = mushafGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    HorizontalDivider(Modifier.weight(1f), color = mushafGold.copy(alpha = 0.65f))
                }

                when {
                    loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = mushafGold)
                    }
                    error != null -> Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(error.orEmpty(), color = Color(0xFF8B1E16), textAlign = TextAlign.Center)
                    }
                    pageLoading && pageData == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = mushafGold)
                    }
                    else -> {
                        Box(
                            Modifier.weight(1f).fillMaxWidth()
                                .pointerInput(page) {
                                    var drag = 0f
                                    detectHorizontalDragGestures(
                                        onHorizontalDrag = { _, amount -> drag += amount },
                                        onDragEnd = {
                                            if (drag > 70f) page = ((page ?: 1) - 1).coerceAtLeast(1)
                                            else if (drag < -70f) page = ((page ?: 1) + 1).coerceAtMost(604)
                                            drag = 0f
                                        }
                                    )
                                }
                                .verticalScroll(scrollState)
                                .padding(horizontal = 13.dp, vertical = 12.dp)
                        ) {
                            BasicText(
                                text = pageText,
                                modifier = Modifier.fillMaxWidth(),
                                style = TextStyle(
                                    color = mushafInk,
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 24.sp,
                                    lineHeight = 47.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Justify,
                                    textDirection = TextDirection.ContentOrRtl
                                )
                            )
                        }
                        if (pageError != null) {
                            Text(pageError.orEmpty(), modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), color = Color(0xFF8B1E16), fontSize = 10.sp, textAlign = TextAlign.Center)
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { page = ((page ?: 1) - 1).coerceAtLeast(1) }, enabled = (page ?: 1) > 1 && !pageLoading) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "الصفحة السابقة", tint = mushafGreen)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("الجزء " + (pageData?.juz?.takeIf { it > 0 }?.let(::arabicDigits) ?: "—"), color = mushafGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("صفحة " + (page?.let(::arabicDigits) ?: "—") + " من ٦٠٤", color = mushafGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { page = ((page ?: 1) + 1).coerceAtMost(604) }, enabled = (page ?: 1) < 604 && !pageLoading) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "الصفحة التالية", tint = mushafGreen)
                    }
                }

                if (durationMs > 0L) {
                    LinearProgressIndicator(
                        progress = { (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        color = mushafGold,
                        trackColor = Color(0xFFE8DDBF)
                    )
                }
            }
        }
    }
}
