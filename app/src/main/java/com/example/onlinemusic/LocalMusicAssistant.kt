package com.example.onlinemusic

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Instant, zero-credit, zero-latency semantic music assistant.
 * Runs 100% on the user's device without consuming any developer cloud credits or requiring
 * any external model downloads, mapping musical vibes/moods/dialects to authentic repertoire
 * rather than searching literal category names.
 */
class LocalMusicAssistant(context: Context) : AutoCloseable {

    val isReady: Boolean
        get() = true

    suspend fun initializeIfDownloaded(): Boolean = true

    suspend fun downloadAndInitialize(onProgress: suspend (Float) -> Unit) {
        withContext(Dispatchers.Main.immediate) {
            onProgress(1f)
        }
    }

    suspend fun createSearchQueries(request: String, seedTerms: List<String>): List<String> =
        withContext(Dispatchers.Default) {
            buildSmartVibeQueries(request, seedTerms)
        }

    suspend fun rankTracks(request: String, candidates: List<AlbumatyLink>): List<AlbumatyLink> =
        withContext(Dispatchers.Default) {
            if (candidates.isEmpty()) return@withContext emptyList()
            rankAndDiversifyTracks(request, candidates)
        }

    internal fun buildSmartVibeQueries(request: String, seedTerms: List<String>): List<String> {
        val normalized = normalizeArabic(request)
        val matchedPool = mutableListOf<String>()

        // If seedTerms are provided (from a MoodCategory), shuffle them so each refresh brings fresh artists/songs
        if (seedTerms.isNotEmpty()) {
            matchedPool.addAll(seedTerms.shuffled())
        }

        // Semantic mood & vibe repertoire pools (actual songs & artists with that musical character, NEVER literal category labels)
        val semanticVibePools: List<Pair<List<String>, List<String>>> = listOf(
            listOf("حزين", "حزينه", "فراق", "دموع", "وجع", "جرح", "كئيب", "زعلان", "وداع", "خذلان", "موجوع", "بكي", "sad") to listOf(
                "تامر عاشور", "هيجيلي موجوع", "شيرين كده يا قلبي", "رامي جمال",
                "مسلم اتنسيت", "حمزة نمرة فاضي شوية", "أحمد كامل", "وائل جسار خليني ذكرى",
                "أنغام ياريتك فاهمني", "تامر عاشور بقول عادي", "شيرين جرح تاني",
                "مصطفى كامل", "هاني شاكر", "تامر حسني نسيانك صعب", "أصالة", "آدم"
            ),
            listOf("فرح", "سعاده", "بهجه", "مبهجه", "مبسوط", "فرحان", "انبساط", "happy", "joy") to listOf(
                "أحمد سعد اليوم الحلو", "عمرو دياب", "حماقي", "تامر حسني حلو المكان",
                "نانسي عجرم", "حسين الجسمي", "راغب علامة", "أحمد سعد اختياراتي",
                "سعد لمجرد", "محمود العسيلي", "بهاء سلطان", "روبي"
            ),
            listOf("حماس", "حماسيه", "جيم", "رياضه", "طاقه", "نشاط", "تمرين", "باور", "تشجيع", "workout", "gym", "sport") to listOf(
                "ويجز", "مروان بابلو", "مروان موسى", "عفروتو",
                "أحمد سعد", "كايروكي", "شارموفرز", "عصام صاصا",
                "حسن شاكوش", "تامر حسني", "ديسكو مصر"
            ),
            listOf("رقص", "افراح", "زفه", "شعبي", "مهرجان", "مهرجانات", "حفله", "خطوبه", "دخله", "حنه", "party", "dance") to listOf(
                "محمود الليثي", "حكيم", "أحمد سعد", "رضا البحراوي",
                "حسن شاكوش", "عمر كمال", "بوسي", "عبد الباسط حمودة",
                "عصام صاصا", "سعد الصغير", "فارس كرم", "حمو بيكا"
            ),
            listOf("حب", "رومانسي", "رومانسيه", "عشق", "غزل", "حبيبي", "حبيبتي", "مشاعر", "love", "romance") to listOf(
                "إليسا", "عمرو دياب", "تامر حسني", "وائل جسار",
                "فضل شاكر", "ماجد المهندس", "تامر عاشور", "شيرين",
                "حماقي", "نانسي عجرم", "رامي صبري", "كاظم الساهر", "وائل كفوري", "أنغام"
            ),
            listOf("هادئ", "هادئه", "هدوء", "ليل", "ليليه", "روقان", "استرخاء", "نوم", "رايق", "رايقه", "سكون", "راحة", "chill", "relax", "sleep") to listOf(
                "فيروز", "عمر خيرت", "كاظم الساهر", "فضل شاكر",
                "مروان خوري", "إليسا", "أنغام", "عبادي الجوهر",
                "ماجدة الرومي", "عبير نعمة", "فايا يونان", "حمزة نمرة"
            ),
            listOf("سفر", "طريق", "قياده", "عربيه", "سياره", "مشوار", "رحله", "drive", "road") to listOf(
                "كايروكي", "عمرو دياب", "ويجز", "حماقي",
                "شارموفرز", "مسار إجباري", "محمود العسيلي", "توليت",
                "مروان موسى", "تامر حسني", "أمير عيد"
            ),
            listOf("ميلاد", "عيد ميلاد", "بيرثداي", "birthday") to listOf(
                "تامر حسني كل سنة", "نانسي عجرم سنة حلوة", "وليد توفيق انزل يا جميل",
                "حماقي", "عمرو دياب", "راغب علامة", "حسين الجسمي"
            ),
            listOf("قديم", "طرب", "كلاسيك", "الزمن الجميل", "اصاله", "عمالقه", "تراث", "classic") to listOf(
                "أم كلثوم", "عبد الحليم حافظ", "وردة الجزائرية", "فريد الأطرش",
                "محمد عبد الوهاب", "فايزة أحمد", "نجاة الصغيرة", "شادية",
                "جورج وسوف", "ميادة الحناوي", "صباح فخري", "وديع الصافي"
            ),
            listOf("موال", "مواويل", "صعيدي", "شجن") to listOf(
                "رضا البحراوي", "عبد الباسط حمودة", "طارق الشيخ", "جورج وسوف",
                "أحمد عدوية", "حكيم", "وديع الصافي", "صباح فخري", "محمد رشدي"
            ),
            listOf("خليجي", "خليجيه", "سعودي", "سعوديه", "كويتي", "اماراتي", "قطري", "بحريني", "عماني", "يمني") to listOf(
                "محمد عبده", "راشد الماجد", "ماجد المهندس", "عبد المجيد عبد الله",
                "طلال مداح", "عبادي الجوهر", "حسين الجسمي", "أصالة", "أحلام", "نبيل شعيل"
            ),
            listOf("شامي", "شاميه", "لبناني", "لبنانيه", "سوري", "سوريه", "اردني", "فلسطيني", "دبكه") to listOf(
                "فيروز", "جورج وسوف", "وائل كفوري", "نانسي عجرم", "إليسا",
                "ناصيف زيتون", "وديع الصافي", "صباح فخري", "ملحم بركات", "الشامي", "عاصي الحلاني"
            ),
            listOf("عراقي", "عراقيه", "بغدادي") to listOf(
                "كاظم الساهر", "ماجد المهندس", "سيف نبيل", "رحمة رياض",
                "حاتم العراقي", "ياس خضر", "سعدون جابر", "نور الزين", "حسام الرسام"
            ),
            listOf("مغربي", "مغربيه", "مغاربي", "مغاربيه", "جزائري", "تونسي", "ليبي", "راي") to listOf(
                "سعد لمجرد", "الشاب خالد", "الشاب حسني", "وردة الجزائرية",
                "سميرة سعيد", "صابر الرباعي", "لطفي بوشناق", "ذكرى", "الشاب مامي"
            ),
            listOf("مصري", "مصريه", "مصر") to listOf(
                "عمرو دياب", "تامر حسني", "شيرين", "أحمد سعد",
                "تامر عاشور", "أنغام", "حماقي", "كايروكي", "بهاء سلطان"
            ),
            listOf("راب", "تراب", "هيب هوب", "اندرجراوند", "rap", "trap") to listOf(
                "ويجز", "مروان بابلو", "مروان موسى", "عفروتو", "أبيوسف", "كايروكي", "الجوكر"
            ),
            listOf("جديد", "جديده", "احدث", "ترند", "2026", "2025", "حصري", "new", "trending") to listOf(
                "أحمد سعد", "عمرو دياب", "تامر عاشور", "توليت", "تامر حسني", "ويجز", "شيرين", "أنغام"
            )
        )

        for ((triggers, repertoire) in semanticVibePools) {
            if (triggers.any { normalized.contains(normalizeArabic(it)) }) {
                matchedPool.addAll(repertoire.shuffled())
            }
        }

        // Check if the user typed a specific artist or song name (excluding generic command/mood words)
        val genericMoodAndCommandWords = setOf(
            "شغل", "شغلي", "لي", "عايز", "عاوز", "اريد", "ابحث", "سمعني", "ممكن", "هات", "افتح",
            "اغاني", "اغنيه", "مزيكا", "موسيقي", "قائمه", "قائمه تشغيل", "بلاي", "ليست",
            "حزين", "حزينه", "حزن", "فراق", "دموع", "وجع", "سعاده", "بهجه", "مبهجه", "فرح",
            "قياده", "سفر", "طريق", "هدوء", "منتصف", "الليل", "ليليه", "هادئه", "هادئ",
            "نوم", "استرخاء", "راحه", "افراح", "رقص", "زفاف", "عيد", "الميلاد", "ميلاد",
            "رومانسيه", "رومانسي", "حب", "عشق", "رياضه", "حماس", "حماسيه", "تشجيع", "جيم",
            "احدث", "الاصدارات", "جديد", "جديده", "ترند", "الان", "قديم", "قديمه", "طرب", "اصيل",
            "مواويل", "موال", "مصري", "مصريه", "خليجي", "خليجيه", "شامي", "شاميه", "عراقي", "عراقيه",
            "مغاربي", "مغاربيه", "مغربي", "مغربيه", "عربي", "عربيه", "ذات", "طابع", "مود", "مزاج",
            "اللي", "على", "في", "من", "عن", "مع", "كل", "اي", "حاجه", "شيء", "شويه", "حلوه", "جميله"
        )

        val userSpecificTerms = request
            .replace("•", " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { token ->
                val norm = normalizeArabic(token)
                norm.length >= 2 && norm !in genericMoodAndCommandWords
            }

        val specificPhrase = userSpecificTerms.joinToString(" ").trim()
        val finalQueries = mutableListOf<String>()

        // If the user explicitly named an artist or specific song title (not just a mood word), put it first
        if (specificPhrase.isNotBlank()) {
            finalQueries.add(specificPhrase)
        }

        finalQueries.addAll(matchedPool)

        if (finalQueries.isEmpty()) {
            finalQueries.addAll(
                listOf("عمرو دياب", "تامر عاشور", "أحمد سعد", "شيرين", "تامر حسني", "أنغام", "كاظم الساهر", "فيروز").shuffled()
            )
        }

        return finalQueries
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
    }

    /**
     * Ranks and interleaves candidate songs so the playlist has rich artist diversity
     * and avoids clumping songs that merely contain the literal category label.
     */
    internal fun rankAndDiversifyTracks(request: String, candidates: List<AlbumatyLink>): List<AlbumatyLink> {
        val normRequest = normalizeArabic(request)

        // Group candidates by inferred artist/bucket so we interleave artists smoothly
        val buckets = linkedMapOf<String, MutableList<AlbumatyLink>>()
        for (track in candidates.distinctBy { it.url }) {
            val cleanTitle = track.title
                .replace(Regex("^(?:اغنية|أغنية|تحميل|استماع)\\s+"), "")
                .trim()
            val bucketKey = when {
                cleanTitle.contains("-") -> cleanTitle.substringAfterLast("-").trim().take(18)
                else -> cleanTitle.split(Regex("\\s+")).take(2).joinToString(" ")
            }.ifBlank { track.url }
            buckets.getOrPut(bucketKey) { mutableListOf() }.add(track)
        }

        // Round-robin interleave across buckets for maximum artist & musical variety
        val interleaved = mutableListOf<AlbumatyLink>()
        val iterators = buckets.values.map { it.iterator() }.toMutableList()
        while (iterators.isNotEmpty()) {
            val it = iterators.iterator()
            while (it.hasNext()) {
                val bucketIt = it.next()
                if (bucketIt.hasNext()) {
                    interleaved.add(bucketIt.next())
                } else {
                    it.remove()
                }
            }
        }

        // De-prioritize tracks whose title ONLY matches a literal mood word like "حزين"
        val literalMoodWords = setOf("حزين", "حزينه", "سفر", "نوم", "استرخاء", "هدوء", "رياضه")
        val isGenericMoodRequest = literalMoodWords.any { normRequest == it || normRequest.contains(it) }

        return if (isGenericMoodRequest) {
            val authentic = mutableListOf<AlbumatyLink>()
            val literalTitleOnly = mutableListOf<AlbumatyLink>()
            for (track in interleaved) {
                val normTitle = normalizeArabic(track.title)
                if (normTitle.contains(normRequest) && normRequest.length <= 6) {
                    literalTitleOnly.add(track)
                } else {
                    authentic.add(track)
                }
            }
            authentic + literalTitleOnly
        } else {
            interleaved
        }
    }

    private fun normalizeArabic(text: String): String {
        return text.lowercase()
            .replace(Regex("[أإآ]"), "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace(Regex("[ًٌٍَُِّْ]"), "")
            .trim()
    }

    override fun close() = Unit
}
