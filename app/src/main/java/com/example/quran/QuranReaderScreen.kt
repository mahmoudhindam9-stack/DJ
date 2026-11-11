package com.example.quran

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.player.AudioPlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val AmiriQuranFontFamily = FontFamily(
    Font(R.font.amiri_quran, FontWeight.Normal),
    Font(R.font.amiri, FontWeight.Bold)
)

private val MushafPaperBg = Color(0xFFFAF2DF)
private val MushafPaperGrain1 = Color(0xFFF1E5CA)
private val MushafPaperGrain2 = Color(0xFFEADBB9)
private val MushafInk = Color(0xFF14110E)
private val MushafDivineRed = Color(0xFFC82424)
private val MushafBronzeDark = Color(0xFF433018)
private val MushafBronzeMid = Color(0xFF6B4F28)
private val MushafGoldLight = Color(0xFFDDC797)
private val MushafGoldWarm = Color(0xFFC5A467)
private val ActiveAyahFill = Color(0x48E8B938)
private val ActiveAyahStroke = Color(0xFFC82424)

private data class PolygonPoint(val x: Float, val y: Float)

private data class AyahTimingEntry(
    val surahNumber: Int,
    val ayahInSurah: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val page: Int,
    val markerX: Float?,
    val markerY: Float?,
    val polygon: List<PolygonPoint>
)

private data class ReaderVerse(
    val number: Int,
    val numberInSurah: Int,
    val text: String,
    val phoneticWeight: Int,
    val page: Int,
    val juz: Int,
    val surahNumber: Int,
    val surahName: String,
    val surahVerseCount: Int
)

private data class LineWordSpan(
    val lineNumber: Int,
    val xStart343: Float,
    val xEnd343: Float,
    val isDivineName: Boolean
)

private data class SurahBannerBounds(
    val surahNumber: Int,
    val surahName: String,
    val ayahCount: Int,
    val topNorm: Float,
    val bottomNorm: Float,
    val hasBismillahBelow: Boolean
)

private data class ReaderPageData(
    val pageNumber: Int,
    val juz: Int,
    val verses: List<ReaderVerse>,
    val ayahGeometries: List<AyahTimingEntry>,
    val divineWordSpans: List<LineWordSpan>,
    val surahBanners: List<SurahBannerBounds>,
    val pageBitmap: Bitmap?,
    val redTintedBitmap: Bitmap?
)

private val surahTotalAyahs = intArrayOf(
    0,
    7, 286, 200, 176, 120, 165, 206, 75, 129, 109,
    123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
    112, 78, 118, 64, 77, 227, 93, 88, 69, 60,
    34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
    54, 53, 89, 59, 37, 35, 38, 29, 18, 45,
    60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
    14, 11, 11, 18, 12, 12, 30, 52, 52, 44,
    28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
    29, 19, 36, 25, 22, 17, 19, 26, 30, 20,
    15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
    11, 8, 3, 9, 5, 4, 7, 3, 6, 3,
    5, 4, 5, 6
)

private val arabicSurahNames = listOf(
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

private class QuranReaderRepository(private val appContext: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val timingCache = mutableMapOf<String, List<AyahTimingEntry>>()
    private val geometryBySurahCache = mutableMapOf<Int, List<AyahTimingEntry>>()
    private var timedReadsDirectory: List<Pair<Int, String>>? = null

    private fun computePhoneticWeight(text: String): Int {
        var score = 0
        for (ch in text) {
            score += when (ch) {
                'آ', 'ٱ', 'ٰ', 'ٓ', 'ـ' -> 4
                'ا', 'و', 'ي', 'ى' -> 2
                in '\u0621'..'\u064A' -> 1
                'ّ' -> 2
                'ۖ', 'ۗ', 'ۘ', 'ۙ', 'ۚ', 'ۛ' -> 3
                else -> 0
            }
        }
        return score.coerceAtLeast(6)
    }

    private fun parseVerse(ayah: JSONObject, fallbackSurahNumber: Int, fallbackSurahName: String): ReaderVerse? {
        val text = ayah.optString("text").trim()
        if (text.isBlank()) return null
        val surah = ayah.optJSONObject("surah")
        val surahNumber = (surah?.optInt("number", fallbackSurahNumber) ?: fallbackSurahNumber).coerceIn(1, 114)
        val surahName = surah?.optString("name").orEmpty().ifBlank {
            fallbackSurahName.ifBlank { "سُورَةُ " + arabicSurahNames[surahNumber - 1] }
        }
        val ayahCount = surah?.optInt("numberOfAyahs", surahTotalAyahs[surahNumber])
            ?.takeIf { it > 0 } ?: surahTotalAyahs[surahNumber]
        return ReaderVerse(
            number = ayah.optInt("number"),
            numberInSurah = ayah.optInt("numberInSurah"),
            text = text,
            phoneticWeight = computePhoneticWeight(text),
            page = ayah.optInt("page"),
            juz = ayah.optInt("juz"),
            surahNumber = surahNumber,
            surahName = surahName,
            surahVerseCount = ayahCount
        )
    }

    suspend fun loadSurahVerses(surahNumber: Int): List<ReaderVerse> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.alquran.cloud/v1/surah/$surahNumber")
            .header("Accept", "application/json")
            .header("User-Agent", "Mozilla/5.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("تعذر تحميل بيانات السورة (HTTP ${response.code})")
            val data = JSONObject(response.body?.string().orEmpty()).optJSONObject("data")
                ?: error("استجابة المصحف غير مكتملة")
            val surahName = data.optString("name", "سُورَةُ " + arabicSurahNames[(surahNumber - 1).coerceIn(0, 113)])
            val ayahs = data.optJSONArray("ayahs") ?: error("لم يتم العثور على آيات السورة")
            buildList {
                for (i in 0 until ayahs.length()) {
                    val ayah = ayahs.optJSONObject(i) ?: continue
                    parseVerse(ayah, surahNumber, surahName)?.let(::add)
                }
            }
        }
    }

    private suspend fun getTimedReadsDirectory(): List<Pair<Int, String>> = withContext(Dispatchers.IO) {
        timedReadsDirectory?.let { return@withContext it }
        val loaded = runCatching {
            val req = Request.Builder()
                .url("https://www.mp3quran.net/api/v3/ayat_timing/reads")
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val arr = JSONArray(resp.body?.string().orEmpty())
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val id = obj.optInt("id", -1)
                        val name = obj.optString("name").trim()
                        if (id > 0 && name.isNotBlank()) add(id to normalizeArabicName(name))
                    }
                }
            }
        }.getOrDefault(emptyList())
        if (loaded.isNotEmpty()) timedReadsDirectory = loaded
        loaded
    }

    private fun normalizeArabicName(value: String): String = value
        .replace(Regex("[أإآٱ]"), "ا")
        .replace("ة", "ه")
        .replace("ى", "ي")
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .replace("الشيخ", "")
        .replace("شيخ", "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun parsePolygon(polyRaw: String?): List<PolygonPoint> {
        if (polyRaw.isNullOrBlank() || polyRaw == "null") return emptyList()
        return polyRaw.trim().split(Regex("\\s+")).mapNotNull { pair ->
            val parts = pair.split(",")
            if (parts.size == 2) {
                val x = parts[0].toFloatOrNull()
                val y = parts[1].toFloatOrNull()
                if (x != null && y != null) PolygonPoint(x, y) else null
            } else null
        }
    }

    private fun parsePageNumberFromSvgUrl(pageUrl: String?): Int {
        if (pageUrl.isNullOrBlank()) return -1
        val match = Regex("(\\d{1,3})\\.svg").find(pageUrl) ?: return -1
        return match.groupValues[1].toIntOrNull() ?: -1
    }

    private suspend fun fetchRawTimingList(surahNumber: Int, readId: Int): List<AyahTimingEntry> = withContext(Dispatchers.IO) {
        val cacheKey = "$surahNumber:$readId"
        timingCache[cacheKey]?.let { return@withContext it }
        val result = runCatching {
            val req = Request.Builder()
                .url("https://www.mp3quran.net/api/v3/ayat_timing?surah=$surahNumber&read=$readId")
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val bodyStr = resp.body?.string().orEmpty().trim()
                if (!bodyStr.startsWith("[")) return@use emptyList()
                val arr = JSONArray(bodyStr)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val ayahNum = obj.optInt("ayah", -1)
                        if (ayahNum < 0) continue
                        val startMs = obj.optLong("start_time", -1L)
                        val endMs = obj.optLong("end_time", -1L)
                        val pageNum = parsePageNumberFromSvgUrl(obj.optString("page"))
                        val x = obj.optString("x").toFloatOrNull()
                        val y = obj.optString("y").toFloatOrNull()
                        val poly = parsePolygon(obj.optString("polygon"))
                        add(
                            AyahTimingEntry(
                                surahNumber = surahNumber,
                                ayahInSurah = ayahNum,
                                startTimeMs = startMs.coerceAtLeast(0L),
                                endTimeMs = endMs.coerceAtLeast(startMs.coerceAtLeast(0L)),
                                page = pageNum,
                                markerX = x,
                                markerY = y,
                                polygon = poly
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())

        if (result.isNotEmpty()) {
            timingCache[cacheKey] = result
            if (readId == 1) geometryBySurahCache[surahNumber] = result
        }
        result
    }

    /**
     * Loads verified millisecond ayah timings for the active surah & reciter/moshaf,
     * along with a boolean flag indicating if the timing comes from the exact moshaf stream.
     */
    suspend fun loadSurahTimings(
        surahNumber: Int,
        reciterId: Int?,
        moshafId: Int?,
        reciterName: String?
    ): Pair<List<AyahTimingEntry>, Boolean> = withContext(Dispatchers.IO) {
        val referenceGeometry = geometryBySurahCache[surahNumber]
            ?: fetchRawTimingList(surahNumber, 1).also { if (it.isNotEmpty()) geometryBySurahCache[surahNumber] = it }

        // 1. Try exact moshafId first
        if (moshafId != null && moshafId > 0) {
            val exact = fetchRawTimingList(surahNumber, moshafId)
            if (exact.count { it.ayahInSurah > 0 && it.endTimeMs > it.startTimeMs } >= 3) {
                return@withContext mergeGeometry(exact, referenceGeometry) to true
            }
        }

        // 2. Try reciterId or matching reciter name in ayat_timing/reads directory
        val dir = getTimedReadsDirectory()
        val normArtist = normalizeArabicName(reciterName.orEmpty())
        val matchedReadId = dir.firstOrNull { (id, name) ->
            (reciterId != null && id == reciterId) ||
                (normArtist.isNotBlank() && (name == normArtist || name.contains(normArtist) || normArtist.contains(name)))
        }?.first

        if (matchedReadId != null) {
            val byReciter = fetchRawTimingList(surahNumber, matchedReadId)
            if (byReciter.count { it.ayahInSurah > 0 && it.endTimeMs > it.startTimeMs } >= 3) {
                return@withContext mergeGeometry(byReciter, referenceGeometry) to false
            }
        }

        // 3. Fallback to reference timing (read=123 Mishary or read=1) with full geometry
        val fallback = fetchRawTimingList(surahNumber, 123).ifEmpty { referenceGeometry }
        mergeGeometry(fallback, referenceGeometry) to false
    }

    private fun mergeGeometry(
        timings: List<AyahTimingEntry>,
        geometryRef: List<AyahTimingEntry>
    ): List<AyahTimingEntry> {
        if (geometryRef.isEmpty()) return timings
        val geoByAyah = geometryRef.associateBy { it.ayahInSurah }
        return timings.map { entry ->
            val ref = geoByAyah[entry.ayahInSurah]
            if (ref != null) {
                entry.copy(
                    page = if (entry.page in 1..604) entry.page else ref.page,
                    markerX = entry.markerX ?: ref.markerX,
                    markerY = entry.markerY ?: ref.markerY,
                    polygon = entry.polygon.ifEmpty { ref.polygon }
                )
            } else entry
        }
    }

    private suspend fun loadPageBitmap(pageNumber: Int): Bitmap? = withContext(Dispatchers.IO) {
        val cacheDir = File(appContext.cacheDir, "mushaf_pages_v2").apply { mkdirs() }
        val padded = pageNumber.toString().padStart(3, '0')
        val cacheFile = File(cacheDir, "page_$padded.png")

        if (cacheFile.exists() && cacheFile.length() > 4000L) {
            BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { return@withContext it }
        }

        val urls = listOf(
            "https://android.quran.com/data/width_1260/page$padded.png",
            "https://android.quran.com/data/width_1024/page$padded.png"
        )
        for (url in urls) {
            val bytes = runCatching {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.bytes() else null
                }
            }.getOrNull()

            if (bytes != null && bytes.size > 4000) {
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (decoded != null) {
                    runCatching { FileOutputStream(cacheFile).use { it.write(bytes) } }
                    return@withContext decoded
                }
            }
        }
        null
    }

    private fun isDivineArabicWord(word: String): Boolean {
        val stripped = word
            .replace(Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640\\u06DF\\u06E0\\u06E2\\u06E5\\u06E6]"), "")
            .replace("ٱ", "ا")
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .trim()

        if (stripped in setOf(
                "الله", "لله", "بالله", "تالله", "والله", "فلله", "فالله", "ءالله", "ولله",
                "اللهم", "الرحمن", "رب", "ربك", "ربكم", "ربكما", "ربهم", "ربهن", "ربه", "ربها",
                "ربي", "ربنا", "رب العالمين", "فوربك", "وربك", "لربك", "بربك", "لربه", "بربه",
                "بربهم", "بربنا", "وربنا", "وربكم", "وربهم"
            )
        ) {
            return true
        }
        return stripped.contains("الله") || stripped.contains("لله")
    }

    private suspend fun loadPageDivineWordSpans(
        pageNumber: Int,
        ayahGeometries: List<AyahTimingEntry>
    ): List<LineWordSpan> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.quran.com/api/v4/verses/by_page/$pageNumber?words=true&word_fields=text_uthmani,line_number&per_page=50")
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JSONObject(resp.body?.string().orEmpty())
                val versesArr = root.optJSONArray("verses") ?: return@use emptyList()

                data class RawToken(
                    val surah: Int,
                    val ayah: Int,
                    val isEndMarker: Boolean,
                    val text: String,
                    val weight: Float
                )

                val lineTokens = mutableMapOf<Int, MutableList<RawToken>>()
                for (i in 0 until versesArr.length()) {
                    val vObj = versesArr.optJSONObject(i) ?: continue
                    val vKey = vObj.optString("verse_key")
                    val sNum = vKey.substringBefore(":").toIntOrNull() ?: 1
                    val aNum = vKey.substringAfter(":").toIntOrNull() ?: vObj.optInt("verse_number")
                    val wordsArr = vObj.optJSONArray("words") ?: continue
                    for (j in 0 until wordsArr.length()) {
                        val wObj = wordsArr.optJSONObject(j) ?: continue
                        val ln = wObj.optInt("line_number", -1)
                        if (ln !in 1..15) continue
                        val cType = wObj.optString("char_type_name")
                        val txt = wObj.optString("text_uthmani").ifBlank { wObj.optString("text") }
                        val cleanLen = txt.replace(Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]"), "").length.coerceAtLeast(1)
                        lineTokens.getOrPut(ln) { mutableListOf() }.add(
                            RawToken(
                                surah = sNum,
                                ayah = aNum,
                                isEndMarker = cType == "end",
                                text = txt,
                                weight = cleanLen.toFloat() + 0.8f
                            )
                        )
                    }
                }

                val geoMap = ayahGeometries.associateBy { it.surahNumber to it.ayahInSurah }
                val spans = mutableListOf<LineWordSpan>()

                for ((ln, tokens) in lineTokens) {
                    if (tokens.isEmpty()) continue
                    // Split line into segments bounded by known end-marker x coordinates (RTL: 343 down to 0)
                    var segStartX = 343f
                    val currentSegWords = mutableListOf<RawToken>()

                    fun flushSegment(segEndX: Float) {
                        if (currentSegWords.isEmpty()) return
                        val totalW = currentSegWords.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1f)
                        val width = (segStartX - segEndX).coerceAtLeast(1f)
                        var cursorRight = segStartX
                        for (w in currentSegWords) {
                            val wSpan = (w.weight / totalW) * width
                            val leftEdge = (cursorRight - wSpan).coerceAtLeast(segEndX)
                            if (isDivineArabicWord(w.text)) {
                                spans.add(
                                    LineWordSpan(
                                        lineNumber = ln,
                                        xStart343 = (leftEdge - 3.5f).coerceAtLeast(0f),
                                        xEnd343 = (cursorRight + 3.5f).coerceAtMost(343f),
                                        isDivineName = true
                                    )
                                )
                            }
                            cursorRight = leftEdge
                        }
                        currentSegWords.clear()
                    }

                    for (token in tokens) {
                        if (token.isEndMarker) {
                            val markerX = geoMap[token.surah to token.ayah]?.markerX
                            if (markerX != null) {
                                val rightOfMarker = (markerX + 12f).coerceIn(0f, segStartX)
                                flushSegment(rightOfMarker)
                                segStartX = (markerX - 12f).coerceIn(0f, 343f)
                            }
                        } else {
                            currentSegWords.add(token)
                        }
                    }
                    flushSegment(0f)
                }
                spans
            }
        }.getOrDefault(emptyList())
    }

    private fun detectSurahBanners(
        verses: List<ReaderVerse>,
        ayahGeometries: List<AyahTimingEntry>
    ): List<SurahBannerBounds> {
        val starts = verses.filter { it.numberInSurah == 1 }.distinctBy { it.surahNumber }
        if (starts.isEmpty()) return emptyList()

        val geoBySurah = ayahGeometries.groupBy { it.surahNumber }
        return starts.mapIndexed { idx, startVerse ->
            val prevSurahMaxY = ayahGeometries
                .filter { it.surahNumber != startVerse.surahNumber && it.ayahInSurah > 1 && (it.markerY ?: 0f) < 480f }
                .maxOfOrNull { it.markerY ?: 0f }

            val firstAyahGeo = geoBySurah[startVerse.surahNumber]?.firstOrNull { it.ayahInSurah == 1 }
            val firstAyahPolyMinY = firstAyahGeo?.polygon?.minOfOrNull { it.y }

            val hasBismillah = startVerse.surahNumber != 1 && startVerse.surahNumber != 9
            // In 550-space, each of the 15 lines is ~36.05 units high.
            // If there is a Bismillah line, the Surah Banner is 2 lines above Ayah 1; otherwise 1 line above.
            val bannerTop550 = when {
                firstAyahPolyMinY != null -> {
                    val offset = if (hasBismillah) 73.5f else 37.5f
                    (firstAyahPolyMinY - offset).coerceIn(4f, 505f)
                }
                prevSurahMaxY != null -> (prevSurahMaxY + 15f).coerceIn(4f, 505f)
                else -> if (idx == 0) 6f else 220f
            }
            val bannerBottom550 = (bannerTop550 + 35.5f).coerceAtMost(542f)

            val topImgY = (bannerTop550 / 550f) * 1620f
            val bottomImgY = (bannerBottom550 / 550f) * 1620f
            SurahBannerBounds(
                surahNumber = startVerse.surahNumber,
                surahName = startVerse.surahName,
                ayahCount = startVerse.surahVerseCount,
                topNorm = ((topImgY - 14f) / 1610f).coerceIn(0f, 1f),
                bottomNorm = ((bottomImgY - 14f) / 1610f).coerceIn(0f, 1f),
                hasBismillahBelow = hasBismillah
            )
        }
    }

    private fun createCrimsonBitmap(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = android.graphics.PorterDuffColorFilter(
                android.graphics.Color.rgb(200, 34, 34),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        }
        c.drawBitmap(source, 0f, 0f, p)
        return out
    }

    suspend fun loadPage(pageNumber: Int): ReaderPageData = withContext(Dispatchers.IO) {
        val clampedPage = pageNumber.coerceIn(1, 604)
        val verses = runCatching {
            val request = Request.Builder()
                .url("https://api.alquran.cloud/v1/page/$clampedPage/quran-uthmani")
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val data = JSONObject(response.body?.string().orEmpty()).optJSONObject("data")
                    ?: error("Empty page data")
                val ayahs = data.optJSONArray("ayahs") ?: error("No ayahs")
                buildList {
                    for (i in 0 until ayahs.length()) {
                        val ayah = ayahs.optJSONObject(i) ?: continue
                        parseVerse(ayah, 1, "")?.let(::add)
                    }
                }
            }
        }.getOrDefault(emptyList())

        val surahsOnPage = verses.map { it.surahNumber }.distinct()
        val pageGeometries = coroutineScope {
            surahsOnPage.map { sNum ->
                async {
                    val allSurahGeo = geometryBySurahCache[sNum]
                        ?: fetchRawTimingList(sNum, 1).also { if (it.isNotEmpty()) geometryBySurahCache[sNum] = it }
                    allSurahGeo.filter { it.page == clampedPage && it.ayahInSurah > 0 }
                }
            }.map { it.await() }.flatten()
        }

        val bitmapDeferred = coroutineScope {
            val bmpJob = async { loadPageBitmap(clampedPage) }
            val spansJob = async { loadPageDivineWordSpans(clampedPage, pageGeometries) }
            bmpJob.await() to spansJob.await()
        }

        val bmp = bitmapDeferred.first
        val redBmp = bmp?.let { runCatching { createCrimsonBitmap(it) }.getOrNull() }
        val banners = detectSurahBanners(verses, pageGeometries)

        ReaderPageData(
            pageNumber = clampedPage,
            juz = verses.firstOrNull()?.juz ?: 1,
            verses = verses,
            ayahGeometries = pageGeometries,
            divineWordSpans = bitmapDeferred.second,
            surahBanners = banners,
            pageBitmap = bmp,
            redTintedBitmap = redBmp
        )
    }
}

private fun arabicDigits(value: Int): String = value.toString().map { ch ->
    if (ch in '0'..'9') ('٠'.code + (ch - '0')).toChar() else ch
}.joinToString("")

@Composable
fun QuranReaderScreen(playerController: AudioPlayerController, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val view = LocalView.current

    // Requirement 2: Keep the screen awake continuously like watching a video until the user exits
    DisposableEffect(view, context) {
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            view.keepScreenOn = previousKeepScreenOn
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val amiriTypeface = remember(context) {
        runCatching { ResourcesCompat.getFont(context, R.font.amiri) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    }

    val song = playerController.currentSong
    val parsedSongMeta = remember(song?.id) {
        val id = song?.id.orEmpty()
        if (id.startsWith("quran:")) {
            val parts = id.split(":")
            val recId = parts.getOrNull(1)?.toIntOrNull()
            val mosId = parts.getOrNull(2)?.toIntOrNull()
            val surah = parts.lastOrNull()?.toIntOrNull()?.takeIf { it in 1..114 }
            Triple(recId, mosId, surah)
        } else {
            Triple(null, null, null)
        }
    }
    val reciterId = parsedSongMeta.first
    val moshafId = parsedSongMeta.second
    val surahNumber = parsedSongMeta.third

    val repository = remember { QuranReaderRepository(context.applicationContext) }
    var surahVerses by remember(surahNumber) { mutableStateOf<List<ReaderVerse>>(emptyList()) }
    var surahTimings by remember(surahNumber, moshafId, reciterId) { mutableStateOf<List<AyahTimingEntry>>(emptyList()) }
    var isExactMoshafTiming by remember(surahNumber, moshafId, reciterId) { mutableStateOf(false) }

    var page by remember(surahNumber) { mutableStateOf<Int?>(null) }
    var pageData by remember { mutableStateOf<ReaderPageData?>(null) }
    var loading by remember(surahNumber) { mutableStateOf(true) }
    var pageLoading by remember { mutableStateOf(false) }
    var error by remember(surahNumber) { mutableStateOf<String?>(null) }

    var positionMs by remember { mutableLongStateOf(playerController.currentPositionMs) }
    var durationMs by remember { mutableLongStateOf(playerController.durationMs) }

    // High-frequency 60ms position polling for crisp, lag-free ayah synchronization
    LaunchedEffect(playerController) {
        while (true) {
            positionMs = playerController.currentPositionMs
            durationMs = playerController.durationMs
            delay(60)
        }
    }

    // Load active Surah verses + verified millisecond Ayah timings in parallel
    LaunchedEffect(surahNumber, moshafId, reciterId, song?.artist) {
        surahVerses = emptyList()
        surahTimings = emptyList()
        pageData = null
        page = null
        error = null
        loading = true

        if (surahNumber != null) {
            coroutineScope {
                val versesDeferred = async { runCatching { repository.loadSurahVerses(surahNumber) } }
                val timingsDeferred = async {
                    runCatching {
                        repository.loadSurahTimings(
                            surahNumber = surahNumber,
                            reciterId = reciterId,
                            moshafId = moshafId,
                            reciterName = song?.artist
                        )
                    }
                }
                val versesResult = versesDeferred.await()
                val timingsResult = timingsDeferred.await()

                timingsResult.onSuccess { (timings, exact) ->
                    surahTimings = timings
                    isExactMoshafTiming = exact
                }

                versesResult.onSuccess { loaded ->
                    surahVerses = loaded
                    val initialPage = loaded.firstOrNull()?.page?.takeIf { it in 1..604 }
                        ?: surahTimings.firstOrNull { it.page in 1..604 }?.page
                    page = initialPage
                }.onFailure { err ->
                    val fallbackPage = surahTimings.firstOrNull { it.page in 1..604 }?.page
                    if (fallbackPage != null) {
                        page = fallbackPage
                    } else {
                        error = err.message ?: "تعذر تحميل المصحف"
                    }
                }
            }
        } else {
            error = "لا توجد سورة من صفحة القرآن قيد التشغيل"
        }
        loading = false
    }

    // Load the requested Mushaf page (1..604)
    LaunchedEffect(page) {
        val requestedPage = page ?: return@LaunchedEffect
        pageLoading = true
        runCatching { repository.loadPage(requestedPage) }
            .onSuccess { pageData = it }
            .onFailure {
                val fallbackVerses = surahVerses.filter { v -> v.page == requestedPage }
                if (fallbackVerses.isNotEmpty()) {
                    pageData = ReaderPageData(
                        pageNumber = requestedPage,
                        juz = fallbackVerses.first().juz,
                        verses = fallbackVerses,
                        ayahGeometries = surahTimings.filter { it.page == requestedPage && it.ayahInSurah > 0 },
                        divineWordSpans = emptyList(),
                        surahBanners = emptyList(),
                        pageBitmap = null,
                        redTintedBitmap = null
                    )
                }
            }
        pageLoading = false
    }

    // Requirement 1: Precision Ayah-by-Ayah Synchronization
    val activeAyahPair: Pair<Int, Int>? = remember(
        surahNumber,
        surahVerses,
        surahTimings,
        isExactMoshafTiming,
        positionMs,
        durationMs
    ) {
        val sNum = surahNumber ?: return@remember null
        val positiveTimings = surahTimings.filter { it.ayahInSurah > 0 && it.endTimeMs > it.startTimeMs }

        if (positiveTimings.isNotEmpty()) {
            val lastEndMs = positiveTimings.maxOf { it.endTimeMs }.coerceAtLeast(1L)
            val useDirectMs = isExactMoshafTiming && (durationMs <= 0L || abs(durationMs - lastEndMs) <= 4000L)
            val effectivePosMs = if (useDirectMs || durationMs <= 0L) {
                positionMs
            } else {
                ((positionMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0) * lastEndMs.toDouble()).toLong()
            }

            val firstAyahStart = positiveTimings.first().startTimeMs
            if (effectivePosMs < firstAyahStart) {
                // During Isti'adhah / Basmalah intro before Ayah 1 starts, anchor on Ayah 1
                return@remember sNum to 1
            }

            val matched = positiveTimings.firstOrNull { entry ->
                effectivePosMs >= entry.startTimeMs && effectivePosMs < entry.endTimeMs
            } ?: positiveTimings.lastOrNull { entry ->
                effectivePosMs >= entry.startTimeMs
            } ?: positiveTimings.first()

            return@remember sNum to matched.ayahInSurah
        }

        // Offline / phonetic fallback if timing API is unreachable
        if (surahVerses.isEmpty() || durationMs <= 0L) return@remember null
        val introMs = if (sNum == 1 || sNum == 9) 5500L else 10500L
        if (positionMs <= introMs || durationMs <= introMs + 2000L) {
            return@remember sNum to 1
        }
        val activeDuration = (durationMs - introMs).coerceAtLeast(1L)
        val activeProgress = ((positionMs - introMs).toDouble() / activeDuration.toDouble()).coerceIn(0.0, 1.0)
        val totalWeight = surahVerses.sumOf { it.phoneticWeight }.coerceAtLeast(1)
        val targetWeight = activeProgress * totalWeight
        var acc = 0
        val verse = surahVerses.firstOrNull { v ->
            acc += v.phoneticWeight
            acc >= targetWeight
        } ?: surahVerses.last()
        sNum to verse.numberInSurah
    }

    val activePageForPlayback = remember(activeAyahPair, surahTimings, surahVerses) {
        val (sNum, aNum) = activeAyahPair ?: return@remember null
        surahTimings.firstOrNull { it.surahNumber == sNum && it.ayahInSurah == aNum && it.page in 1..604 }?.page
            ?: surahVerses.firstOrNull { it.surahNumber == sNum && it.numberInSurah == aNum && it.page in 1..604 }?.page
    }

    // Automatically flip the Mushaf page when the reciter advances to a new page
    LaunchedEffect(activePageForPlayback, playerController.isPlaying) {
        val targetPage = activePageForPlayback
        if (playerController.isPlaying && targetPage != null && targetPage in 1..604 && targetPage != page) {
            page = targetPage
        }
    }

    // Full-screen Mushaf Page container (between status bar and navigation bar, ZERO vertical scroll)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        val currentPageData = pageData
        when {
            loading && currentPageData == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MushafPaperBg),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MushafBronzeMid)
                }
            }
            error != null && currentPageData == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MushafPaperBg)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = error.orEmpty(),
                        color = MushafDivineRed,
                        fontFamily = AmiriQuranFontFamily,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            currentPageData != null -> {
                FullPageMushafReplica(
                    pageData = currentPageData,
                    activeAyahPair = activeAyahPair,
                    surahTimings = surahTimings,
                    amiriTypeface = amiriTypeface,
                    onPrevPage = { page = ((page ?: 1) - 1).coerceAtLeast(1) },
                    onNextPage = { page = ((page ?: 1) + 1).coerceAtMost(604) },
                    onTapAyah = { tappedSurah, tappedAyah ->
                        if (tappedSurah == surahNumber) {
                            val timing = surahTimings.firstOrNull {
                                it.surahNumber == tappedSurah && it.ayahInSurah == tappedAyah
                            }
                            if (timing != null) {
                                val positiveTimings = surahTimings.filter { it.ayahInSurah > 0 && it.endTimeMs > it.startTimeMs }
                                val lastEndMs = positiveTimings.maxOfOrNull { it.endTimeMs } ?: 0L
                                val useDirectMs = isExactMoshafTiming && (durationMs <= 0L || abs(durationMs - lastEndMs) <= 4000L)
                                val seekTarget = if (useDirectMs || durationMs <= 0L || lastEndMs <= 0L) {
                                    timing.startTimeMs
                                } else {
                                    ((timing.startTimeMs.toDouble() / lastEndMs.toDouble()) * durationMs.toDouble()).toLong()
                                }
                                playerController.seekTo(seekTarget)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FullPageMushafReplica(
    pageData: ReaderPageData,
    activeAyahPair: Pair<Int, Int>?,
    surahTimings: List<AyahTimingEntry>,
    amiriTypeface: Typeface,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onTapAyah: (Int, Int) -> Unit
) {
    val bmp = pageData.pageBitmap
    val redBmp = pageData.redTintedBitmap

    // Coordinate conversion from 343x550 (mp3quran ayat_timing space) to normalized [0..1] screen space
    // In the 1024x1656 / 1260x2038 Madinah Mushaf page bitmap:
    // Left crop = 40/1024 (0.03906), Right crop = 978/1024 (0.95508), Width = 938/1024
    // Top crop = 14/1656 (0.00845), Bottom crop = 1624/1656 (0.98068), Height = 1610/1656
    fun x343ToNorm(x343: Float): Float {
        val imgX1024 = 51f + (x343 / 343f) * 915f
        return ((imgX1024 - 40f) / 938f).coerceIn(0f, 1f)
    }

    fun y550ToNorm(y550: Float): Float {
        val imgY1656 = (y550 / 550f) * 1620f - 1f
        return ((imgY1656 - 14f) / 1610f).coerceIn(0f, 1f)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MushafPaperBg)
            .pointerInput(pageData.pageNumber) {
                var horizontalDrag = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount -> horizontalDrag += dragAmount },
                    onDragEnd = {
                        // In RTL Mushaf reading, swiping right-to-left or left-to-right turns pages
                        if (horizontalDrag > 65f) {
                            onNextPage()
                        } else if (horizontalDrag < -65f) {
                            onPrevPage()
                        }
                        horizontalDrag = 0f
                    }
                )
            }
            .pointerInput(pageData.pageNumber, pageData.ayahGeometries, surahTimings) {
                detectTapGestures { tapOffset ->
                    if (size.width <= 0 || size.height <= 0) return@detectTapGestures
                    val normX = tapOffset.x / size.width.toFloat()
                    val normY = tapOffset.y / size.height.toFloat()
                    val x343 = (((normX * 938f + 40f) - 51f) / 915f) * 343f
                    val y550 = (((normY * 1610f + 14f) + 1f) / 1620f) * 550f

                    val hit = pageData.ayahGeometries.firstOrNull { geo ->
                        val poly = geo.polygon
                        if (poly.size >= 3) {
                            val minX = poly.minOf { it.x }
                            val maxX = poly.maxOf { it.x }
                            val minY = poly.minOf { it.y }
                            val maxY = poly.maxOf { it.y }
                            x343 in minX..maxX && y550 in minY..maxY
                        } else if (geo.markerX != null && geo.markerY != null) {
                            abs(x343 - geo.markerX) <= 24f && abs(y550 - geo.markerY) <= 22f
                        } else false
                    }
                    if (hit != null) {
                        onTapAyah(hit.surahNumber, hit.ayahInSurah)
                    }
                }
            }
    ) {
        if (bmp != null) {
            val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
            val redImageBitmap = remember(redBmp) { redBmp?.asImageBitmap() }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height

                // 1. Authentic Printed Mushaf Paper Texture Background
                drawPaperTexture(canvasW, canvasH)

                // 2. Ornate Brown & Gold Surah Header Banner Background Tint (under the calligraphy frame)
                for (banner in pageData.surahBanners) {
                    val topY = banner.topNorm * canvasH
                    val bottomY = banner.bottomNorm * canvasH
                    val bHeight = (bottomY - topY).coerceAtLeast(24f)
                    val leftX = x343ToNorm(0f) * canvasW
                    val rightX = x343ToNorm(343f) * canvasW
                    val bWidth = (rightX - leftX).coerceAtLeast(100f)

                    // Outer warm gold-parchment fill
                    drawRoundRect(
                        color = Color(0xFFF3E6C8),
                        topLeft = Offset(leftX, topY),
                        size = Size(bWidth, bHeight),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    // Left & Right arabesque side panel bronze-gold fill
                    val panelW = bWidth * 0.245f
                    drawRect(
                        color = Color(0xFFC8AA70),
                        topLeft = Offset(leftX + bWidth * 0.008f, topY + bHeight * 0.08f),
                        size = Size(panelW, bHeight * 0.84f)
                    )
                    drawRect(
                        color = Color(0xFFC8AA70),
                        topLeft = Offset(rightX - panelW - bWidth * 0.008f, topY + bHeight * 0.08f),
                        size = Size(panelW, bHeight * 0.84f)
                    )
                    // Central Surah Title Cartouche warm cream fill
                    val centerCartoucheLeft = leftX + bWidth * 0.26f
                    val centerCartoucheWidth = bWidth * 0.48f
                    drawRoundRect(
                        color = MushafPaperBg,
                        topLeft = Offset(centerCartoucheLeft, topY + bHeight * 0.10f),
                        size = Size(centerCartoucheWidth, bHeight * 0.80f),
                        cornerRadius = CornerRadius(bHeight * 0.35f, bHeight * 0.35f)
                    )
                }

                // 3. Synchronized Active Recited Ayah Highlight (exact polygon of the recited verse)
                val activeGeo = activeAyahPair?.let { (sNum, aNum) ->
                    pageData.ayahGeometries.firstOrNull { it.surahNumber == sNum && it.ayahInSurah == aNum }
                }
                if (activeGeo != null && activeGeo.polygon.size >= 3) {
                    val polyPath = Path().apply {
                        activeGeo.polygon.forEachIndexed { idx, pt ->
                            val sx = x343ToNorm(pt.x) * canvasW
                            val sy = y550ToNorm(pt.y) * canvasH
                            if (idx == 0) moveTo(sx, sy) else lineTo(sx, sy)
                        }
                        close()
                    }
                    drawPath(path = polyPath, color = ActiveAyahFill)
                    drawPath(
                        path = polyPath,
                        color = ActiveAyahStroke.copy(alpha = 0.55f),
                        style = Stroke(width = 2.2f)
                    )
                }

                // 4. Draw the High-Resolution 15-Line Madinah Mushaf Page Calligraphy (Cropped to Full Screen)
                val srcLeft = (bmp.width * 40) / 1024
                val srcRight = (bmp.width * 978) / 1024
                val srcTop = (bmp.height * 14) / 1656
                val srcBottom = (bmp.height * 1624) / 1656
                val srcOffset = IntOffset(srcLeft, srcTop)
                val srcSize = IntSize((srcRight - srcLeft).coerceAtLeast(1), (srcBottom - srcTop).coerceAtLeast(1))
                val dstSize = IntSize(canvasW.toInt().coerceAtLeast(1), canvasH.toInt().coerceAtLeast(1))

                drawImage(
                    image = imageBitmap,
                    srcOffset = srcOffset,
                    srcSize = srcSize,
                    dstOffset = IntOffset.Zero,
                    dstSize = dstSize
                )

                // 5. Color Divine Names (لفظ الجلالة الله / ربك / ربه) in Crimson Red like the attached screenshots
                if (redImageBitmap != null) {
                    val redClipPath = Path()
                    // 5a. Word-level Divine Name spans on each of the 15 lines
                    for (span in pageData.divineWordSpans) {
                        val lineCenter550 = 24.70f + (span.lineNumber - 1) * 36.04f
                        val top550 = (lineCenter550 - 17.5f).coerceAtLeast(0f)
                        val bottom550 = (lineCenter550 + 17.5f).coerceAtMost(550f)
                        val leftPx = x343ToNorm(span.xStart343) * canvasW
                        val rightPx = x343ToNorm(span.xEnd343) * canvasW
                        val topPx = y550ToNorm(top550) * canvasH
                        val bottomPx = y550ToNorm(bottom550) * canvasH
                        redClipPath.addRect(Rect(leftPx, topPx, rightPx, bottomPx))
                    }
                    // 5b. "اللَّهِ" inside every Bismillah line below a Surah banner
                    for (banner in pageData.surahBanners) {
                        if (banner.hasBismillahBelow) {
                            val bismTop = banner.bottomNorm * canvasH + canvasH * 0.004f
                            val bismBottom = bismTop + (35.5f / 550f) * canvasH
                            // In the centered Bismillah calligraphy, "الله" sits just right of center (x343 = 176..226)
                            val allahLeft = x343ToNorm(176f) * canvasW
                            val allahRight = x343ToNorm(226f) * canvasW
                            redClipPath.addRect(Rect(allahLeft, bismTop, allahRight, bismBottom))
                        }
                    }
                    clipPath(redClipPath) {
                        drawImage(
                            image = redImageBitmap,
                            srcOffset = srcOffset,
                            srcSize = srcSize,
                            dstOffset = IntOffset.Zero,
                            dstSize = dstSize
                        )
                    }
                }

                // 6. Draw Surah Banner Side Medallions ("ترتيبها ١٦" on right, "آياتها ١٢٨" on left)
                for (banner in pageData.surahBanners) {
                    val centerY = ((banner.topNorm + banner.bottomNorm) * 0.5f) * canvasH
                    val bHeight = (banner.bottomNorm - banner.topNorm) * canvasH
                    val circleRadius = (bHeight * 0.36f).coerceIn(16f, 34f)
                    val leftCircleX = ((241f - 40f) / 938f) * canvasW
                    val rightCircleX = ((776f - 40f) / 938f) * canvasW

                    drawSurahSideCircle(
                        center = Offset(rightCircleX, centerY),
                        radius = circleRadius,
                        topLabel = "ترتيبها",
                        bottomLabel = arabicDigits(banner.surahNumber),
                        typeface = amiriTypeface
                    )
                    drawSurahSideCircle(
                        center = Offset(leftCircleX, centerY),
                        radius = circleRadius,
                        topLabel = "آياتها",
                        bottomLabel = arabicDigits(banner.ayahCount),
                        typeface = amiriTypeface
                    )
                }

                // 7. Draw Ornate Pointed-Arch Brown & Gold Ayah Medallions (فواصل الآيات) at every Ayah position
                val medallionRadius = (canvasW * 0.033f).coerceIn(15f, 28f)
                for (geo in pageData.ayahGeometries) {
                    val mx = geo.markerX ?: continue
                    val my = geo.markerY ?: continue
                    val cx = x343ToNorm(mx) * canvasW
                    val cy = y550ToNorm(my) * canvasH
                    val isActive = activeAyahPair?.first == geo.surahNumber && activeAyahPair.second == geo.ayahInSurah

                    drawOrnateAyahMedallion(
                        center = Offset(cx, cy),
                        radius = medallionRadius,
                        ayahNumber = geo.ayahInSurah,
                        isActive = isActive,
                        typeface = amiriTypeface
                    )
                }
            }
        } else {
            // Offline / Fallback Full-Page Auto-Fitted Uthmanic Text Replica (Zero Vertical Scroll)
            AutoFitTextMushafFallback(
                pageData = pageData,
                activeAyahPair = activeAyahPair
            )
        }
    }
}

private fun DrawScope.drawPaperTexture(canvasW: Float, canvasH: Float) {
    drawRect(color = MushafPaperBg, size = Size(canvasW, canvasH))
    // Subtle warm paper speckles deterministic across the page
    val cols = 18
    val rows = 32
    val stepX = canvasW / cols
    val stepY = canvasH / rows
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val hash = ((r * 73 + c * 37) and 0xFF)
            val ox = (c + (hash and 0x0F) / 16f) * stepX
            val oy = (r + ((hash shr 4) and 0x0F) / 16f) * stepY
            drawCircle(
                color = if ((hash and 1) == 0) MushafPaperGrain1 else MushafPaperGrain2,
                radius = (1.5f + (hash % 3)),
                center = Offset(ox, oy),
                alpha = 0.35f
            )
        }
    }
}

private fun DrawScope.drawSurahSideCircle(
    center: Offset,
    radius: Float,
    topLabel: String,
    bottomLabel: String,
    typeface: Typeface
) {
    drawCircle(color = MushafBronzeDark, radius = radius * 1.12f, center = center)
    drawCircle(color = MushafGoldLight, radius = radius * 1.02f, center = center)
    drawCircle(color = MushafPaperBg, radius = radius * 0.88f, center = center)
    drawCircle(
        color = MushafBronzeMid,
        radius = radius * 0.88f,
        center = center,
        style = Stroke(width = 1.6f)
    )

    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(35, 25, 14)
            textAlign = Paint.Align.CENTER
            this.typeface = typeface
            isFakeBoldText = true
            textSize = radius * 0.56f
        }
        canvas.nativeCanvas.drawText(topLabel, center.x, center.y - radius * 0.08f, paint)
        paint.textSize = radius * 0.62f
        canvas.nativeCanvas.drawText(bottomLabel, center.x, center.y + radius * 0.54f, paint)
    }
}

private fun DrawScope.drawOrnateAyahMedallion(
    center: Offset,
    radius: Float,
    ayahNumber: Int,
    isActive: Boolean,
    typeface: Typeface
) {
    val outerColor = if (isActive) MushafDivineRed else MushafBronzeDark
    val midFill = if (isActive) Color(0xFFFFDF80) else MushafGoldLight
    val innerFill = if (isActive) Color(0xFFFFF3C4) else MushafPaperBg

    // Pointed top & bottom arch finials (matching the classic Mushaf medallion in the attached screenshot)
    val archPath = Path().apply {
        val topTip = Offset(center.x, center.y - radius * 1.42f)
        val bottomTip = Offset(center.x, center.y + radius * 1.42f)
        val leftMid = Offset(center.x - radius * 1.08f, center.y)
        val rightMid = Offset(center.x + radius * 1.08f, center.y)

        moveTo(topTip.x, topTip.y)
        quadraticTo(center.x + radius * 0.55f, center.y - radius * 1.05f, rightMid.x, rightMid.y)
        quadraticTo(center.x + radius * 0.55f, center.y + radius * 1.05f, bottomTip.x, bottomTip.y)
        quadraticTo(center.x - radius * 0.55f, center.y + radius * 1.05f, leftMid.x, leftMid.y)
        quadraticTo(center.x - radius * 0.55f, center.y - radius * 1.05f, topTip.x, topTip.y)
        close()
    }
    drawPath(path = archPath, color = outerColor)

    // 8-lobed rosette scallops
    for (i in 0 until 8) {
        val angle = (i * 45.0) * (Math.PI / 180.0)
        val lobeCenter = Offset(
            center.x + (cos(angle) * radius * 0.78f).toFloat(),
            center.y + (sin(angle) * radius * 0.78f).toFloat()
        )
        drawCircle(color = outerColor, radius = radius * 0.34f, center = lobeCenter)
        drawCircle(color = midFill, radius = radius * 0.25f, center = lobeCenter)
    }

    // Concentric Gold & Bronze Medallion Rings
    drawCircle(color = outerColor, radius = radius * 0.96f, center = center)
    drawCircle(color = midFill, radius = radius * 0.86f, center = center)
    drawCircle(color = outerColor, radius = radius * 0.72f, center = center)
    drawCircle(color = innerFill, radius = radius * 0.64f, center = center)

    // Decorative top & bottom dots inside the pointed tips
    drawCircle(
        color = midFill,
        radius = radius * 0.11f,
        center = Offset(center.x, center.y - radius * 1.12f)
    )
    drawCircle(
        color = midFill,
        radius = radius * 0.11f,
        center = Offset(center.x, center.y + radius * 1.12f)
    )

    // Crisp Arabic-Indic Verse Number centered inside the medallion
    val numText = arabicDigits(ayahNumber)
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isActive) android.graphics.Color.rgb(180, 20, 20) else android.graphics.Color.rgb(22, 18, 14)
            textAlign = Paint.Align.CENTER
            this.typeface = typeface
            isFakeBoldText = true
            textSize = when {
                numText.length >= 3 -> radius * 0.66f
                numText.length == 2 -> radius * 0.76f
                else -> radius * 0.84f
            }
        }
        val fm = paint.fontMetrics
        val baselineY = center.y - (fm.ascent + fm.descent) * 0.5f
        canvas.nativeCanvas.drawText(numText, center.x, baselineY, paint)
    }
}

@Composable
private fun BoxWithConstraintsScope.AutoFitTextMushafFallback(
    pageData: ReaderPageData,
    activeAyahPair: Pair<Int, Int>?
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val maxW = constraints.maxWidth
    val maxH = constraints.maxHeight

    val annotatedPage = remember(pageData.verses, activeAyahPair) {
        buildAnnotatedString {
            var prevSurah = -1
            pageData.verses.forEachIndexed { idx, verse ->
                if (verse.surahNumber != prevSurah) {
                    if (idx > 0) append("\n")
                    if (verse.numberInSurah == 1) {
                        pushStyle(SpanStyle(color = MushafBronzeDark, fontWeight = FontWeight.Bold))
                        append("﴿ " + verse.surahName + " ﴾\n")
                        pop()
                        if (verse.surahNumber != 1 && verse.surahNumber != 9) {
                            pushStyle(SpanStyle(color = MushafInk, fontWeight = FontWeight.Bold))
                            append("بِسْمِ ")
                            pop()
                            pushStyle(SpanStyle(color = MushafDivineRed, fontWeight = FontWeight.Bold))
                            append("اللَّهِ")
                            pop()
                            pushStyle(SpanStyle(color = MushafInk, fontWeight = FontWeight.Bold))
                            append(" الرَّحْمَٰنِ الرَّحِيمِ\n")
                            pop()
                        }
                    }
                    prevSurah = verse.surahNumber
                } else if (idx > 0) {
                    append(" ")
                }

                val isActive = activeAyahPair?.first == verse.surahNumber && activeAyahPair.second == verse.numberInSurah
                if (isActive) {
                    pushStyle(SpanStyle(background = ActiveAyahFill, color = MushafDivineRed, fontWeight = FontWeight.Bold))
                    append(verse.text)
                    pop()
                } else {
                    appendWithRedDivineNames(verse.text)
                }
                pushStyle(SpanStyle(color = MushafBronzeMid, fontWeight = FontWeight.Bold))
                append(" ﴿" + arabicDigits(verse.numberInSurah) + "﴾ ")
                pop()
            }
        }
    }

    val optimalFontSizeSp = remember(annotatedPage, maxW, maxH) {
        val horizontalPadPx = with(density) { 16.dp.roundToPx() }
        val verticalPadPx = with(density) { 12.dp.roundToPx() }
        val availW = (maxW - horizontalPadPx).coerceAtLeast(100)
        val availH = (maxH - verticalPadPx).coerceAtLeast(100)

        var low = 13f
        var high = 30f
        var best = 18f
        repeat(10) {
            val mid = (low + high) * 0.5f
            val res = textMeasurer.measure(
                text = annotatedPage,
                style = TextStyle(
                    fontFamily = AmiriQuranFontFamily,
                    fontSize = mid.sp,
                    lineHeight = (mid * 1.72f).sp,
                    textAlign = TextAlign.Justify,
                    textDirection = TextDirection.Rtl
                ),
                constraints = Constraints(maxWidth = availW)
            )
            if (res.size.height <= availH) {
                best = mid
                low = mid
            } else {
                high = mid
            }
        }
        best
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = annotatedPage,
            modifier = Modifier.fillMaxSize(),
            style = TextStyle(
                color = MushafInk,
                fontFamily = AmiriQuranFontFamily,
                fontSize = optimalFontSizeSp.sp,
                lineHeight = (optimalFontSizeSp * 1.72f).sp,
                textAlign = TextAlign.Justify,
                textDirection = TextDirection.Rtl
            )
        )
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendWithRedDivineNames(text: String) {
    val words = text.split(" ")
    words.forEachIndexed { index, word ->
        if (index > 0) append(" ")
        val stripped = word
            .replace(Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]"), "")
            .replace("ٱ", "ا")
        val isDivine = stripped.contains("الله") || stripped.contains("لله") ||
            stripped in setOf("ربك", "ربه", "ربنا", "ربي", "ربكم", "ربهم", "فوربك", "وربك")
        if (isDivine) {
            pushStyle(SpanStyle(color = MushafDivineRed))
            append(word)
            pop()
        } else {
            append(word)
        }
    }
}
