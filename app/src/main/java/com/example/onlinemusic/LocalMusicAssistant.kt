package com.example.onlinemusic

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PlaylistGenerationRequest(
    val categoryId: String? = null,
    val prompt: String,
    val source: String = "albumaty",
    val playlistSize: Int = 50,
    val diversify: Boolean = true
) {
    val cacheKey: String
        get() {
            val normPrompt = LocalMusicAssistant.normalizeArabicText(prompt)
            return listOf(
                source.trim().lowercase(),
                categoryId?.trim()?.lowercase().orEmpty(),
                normPrompt,
                playlistSize.toString(),
                diversify.toString()
            ).joinToString("|")
        }
}

/**
 * Instant, zero-credit, zero-latency semantic music assistant.
 * Runs 100% on the user's device without consuming any developer cloud credits or requiring
 * any external model downloads, mapping musical vibes/moods/dialects to authentic repertoire
 * rather than searching literal category names.
 */
class LocalMusicAssistant(context: Context? = null) : AutoCloseable {

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

    suspend fun rankTracks(
        request: String,
        candidates: List<AlbumatyLink>,
        category: MoodCategory? = null,
        recentTrackSignatures: Set<String> = emptySet(),
        diversify: Boolean = true
    ): List<AlbumatyLink> =
        withContext(Dispatchers.Default) {
            if (candidates.isEmpty()) return@withContext emptyList()
            rankAndDiversifyTracks(
                request = request,
                candidates = candidates,
                category = category,
                recentTrackSignatures = recentTrackSignatures,
                diversify = diversify
            )
        }

    internal fun buildSmartVibeQueries(request: String, seedTerms: List<String>): List<String> {
        val normalized = normalizeArabicText(request)
        val matchedPool = mutableListOf<String>()

        // Semantic mood & vibe repertoire pools (actual songs & artists curated for Albumaty search index)
        val semanticVibePools: List<Pair<List<String>, List<String>>> = listOf(
            listOf("حزين", "حزينه", "حزن", "فراق", "دموع", "وجع", "جرح", "كئيب", "زعلان", "وداع", "خذلان", "موجوع", "بكي", "sad") to listOf(
                "هيجيلي موجوع", "بقول عادي", "كده يا قلبي", "اتنسيت",
                "فاضي شوية", "خليني ذكرى", "ياريتك فاهمني", "اكتبلك تعهد",
                "تامر عاشور", "شيرين", "مسلم", "احمد كامل", "مصطفى كامل", "وائل جسار", "ادم"
            ),
            listOf("فرح", "سعاده", "بهجه", "مبهجه", "مبسوط", "فرحان", "انبساط", "happy", "joy") to listOf(
                "اليوم الحلو", "اختياراتي", "وسع وسع", "يا انا يا لا",
                "حلو المكان", "يا ستار", "ادرينالين", "بالبنط العريض",
                "احمد سعد", "عمرو دياب", "حماقي", "تامر حسني", "نانسي عجرم", "حسين الجسمي", "روبي"
            ),
            listOf("حماس", "حماسيه", "جيم", "رياضه", "طاقه", "نشاط", "تمرين", "باور", "تشجيع", "workout", "gym", "sport") to listOf(
                "دورك جاي", "بطل عالم", "ويجز", "مروان بابلو", "مروان موسى",
                "عفروتو", "كايروكي", "عصام صاصا", "حسن شاكوش", "ابيوسف"
            ),
            listOf("رقص", "افراح", "زفه", "شعبي", "مهرجان", "مهرجانات", "حفله", "خطوبه", "دخله", "حنه", "party", "dance") to listOf(
                "محمود الليثي", "حكيم", "رضا البحراوي", "حسن شاكوش",
                "عمر كمال", "بوسي اه يا دنيا", "عبد الباسط حمودة", "عصام صاصا",
                "سعد الصغير", "فارس كرم", "حلاوة روح"
            ),
            listOf("حب", "رومانسي", "رومانسيه", "عشق", "غزل", "حبيبي", "حبيبتي", "مشاعر", "love", "romance") to listOf(
                "اجمل احساس", "حالة حب", "تملي معاك", "مليون احبك",
                "تيجي نتراهن", "حبه جنة", "ما بلاش", "اليسا",
                "ماجد المهندس", "رامي صبري", "عمرو دياب", "وائل جسار", "تامر حسني"
            ),
            listOf("هادئ", "هادئه", "هدوء", "ليل", "ليليه", "منتصف الليل", "روقان", "رايق", "رايقه", "سكون", "chill", "midnight") to listOf(
                "انا لحبيبي", "زيديني عشقا", "كل القصايد", "اواخر الشتا",
                "فيروز", "كاظم الساهر", "مروان خوري", "فضل شاكر", "اليسا", "عبادي الجوهر", "وائل كفوري"
            ),
            listOf("نوم", "استرخاء", "راحه", "كلاسيك هادئ", "موسيقي هادئه", "relax", "sleep") to listOf(
                "عمر خيرت", "سكن الليل", "اعطني الناي", "ماجدة الرومي",
                "عبير نعمة", "فايا يونان", "جوليا بطرس", "فيروز"
            ),
            listOf("سفر", "طريق", "قياده", "عربيه", "سياره", "مشوار", "رحله", "drive", "road") to listOf(
                "كايروكي", "بسرح واتوه", "روما", "ديسكو مصر",
                "شارموفرز", "مسار اجباري", "توليت", "مروان موسى", "امير عيد", "البخت"
            ),
            listOf("ميلاد", "عيد ميلاد", "بيرثداي", "birthday") to listOf(
                "كل سنة وانت طيب", "سنة حلوة يا جميل", "انزل يا جميل", "عيد ميلاد",
                "وليد توفيق", "حمادة هلال", "نانسي عجرم", "تامر حسني"
            ),
            listOf("قديم", "طرب", "كلاسيك", "الزمن الجميل", "اصاله", "عمالقه", "تراث", "classic") to listOf(
                "ام كلثوم", "عبد الحليم حافظ", "وردة الجزائرية", "فريد الاطرش",
                "محمد عبد الوهاب", "فايزة احمد", "نجاة الصغيرة", "شادية",
                "صباح فخري", "وديع الصافي"
            ),
            listOf("موال", "مواويل", "صعيدي", "شجن") to listOf(
                "موال", "رضا البحراوي", "عبد الباسط حمودة", "طارق الشيخ", "جورج وسوف",
                "احمد عدوية", "حكيم", "وديع الصافي", "صباح فخري", "محمد رشدي"
            ),
            listOf("خليجي", "خليجيه", "سعودي", "سعوديه", "كويتي", "اماراتي", "قطري", "بحريني", "عماني", "يمني") to listOf(
                "محمد عبده", "راشد الماجد", "ماجد المهندس", "عبد المجيد عبد الله",
                "طلال مداح", "عبادي الجوهر", "حسين الجسمي", "ابو بكر سالم", "خالد عبد الرحمن", "نبيل شعيل"
            ),
            listOf("شامي", "شاميه", "لبناني", "لبنانيه", "سوري", "سوريه", "اردني", "فلسطيني", "دبكه") to listOf(
                "فيروز", "جورج وسوف", "وائل كفوري", "نانسي عجرم", "اليسا",
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
                "عمرو دياب", "تامر حسني", "شيرين", "احمد سعد",
                "تامر عاشور", "انغام", "حماقي", "كايروكي", "بهاء سلطان"
            ),
            listOf("راب", "تراب", "هيب هوب", "اندرجراوند", "rap", "trap") to listOf(
                "ويجز", "مروان بابلو", "مروان موسى", "عفروتو", "ابيوسف", "كايروكي", "الجوكر"
            ),
            listOf("جديد", "جديده", "احدث", "ترند", "2026", "2025", "حصري", "new", "trending") to listOf(
                "احمد سعد", "عمرو دياب", "تامر عاشور", "توليت", "تامر حسني", "ويجز", "شيرين", "انغام"
            )
        )

        // Interleave any matching semantic pools with seed terms for multi-facet prompts (e.g. "حزين + خليجي")
        val promptMatchedPools = mutableListOf<List<String>>()
        for ((triggers, repertoire) in semanticVibePools) {
            if (triggers.any { normalized.contains(normalizeArabicText(it)) }) {
                promptMatchedPools.add(repertoire.shuffled())
            }
        }

        if (seedTerms.isNotEmpty()) {
            matchedPool.addAll(seedTerms.shuffled())
        }
        if (promptMatchedPools.isNotEmpty()) {
            val maxSize = promptMatchedPools.maxOf { it.size }
            for (i in 0 until maxSize) {
                for (pool in promptMatchedPools) {
                    pool.getOrNull(i)?.let { matchedPool.add(it) }
                }
            }
        }

        val genericMoodAndCommandWords = setOf(
            "شغل", "شغلي", "لي", "عايز", "عاوز", "اريد", "ابحث", "سمعني", "ممكن", "هات", "افتح",
            "اغاني", "اغنيه", "مزيكا", "موسيقي", "قائمه", "قائمه تشغيل", "بلاي", "ليست",
            "حزين", "حزينه", "حزن", "فراق", "دموع", "وجع", "جرح", "سعاده", "بهجه", "مبهجه", "فرح",
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
                val norm = normalizeArabicText(token)
                norm.length >= 2 && norm !in genericMoodAndCommandWords
            }

        val specificPhrase = userSpecificTerms.joinToString(" ").trim()
        val finalQueries = mutableListOf<String>()

        if (specificPhrase.isNotBlank() && seedTerms.isEmpty()) {
            finalQueries.add(specificPhrase)
        }

        finalQueries.addAll(matchedPool)

        if (finalQueries.isEmpty()) {
            finalQueries.addAll(
                listOf("عمرو دياب", "تامر عاشور", "احمد سعد", "شيرين", "تامر حسني", "انغام", "كاظم الساهر", "فيروز").shuffled()
            )
        }

        return finalQueries
            .map { it.trim().replace(Regex("[أإآ]"), "ا") }
            .filter { it.isNotBlank() }
            .distinct()
            .take(10)
    }

    /**
     * Ranks, deduplicates by URL + normalized (title, artist), excludes contradictory tracks,
     * de-prioritizes tracks in [recentTrackSignatures], and interleaves across artists.
     */
    internal fun rankAndDiversifyTracks(
        request: String,
        candidates: List<AlbumatyLink>,
        category: MoodCategory? = null,
        recentTrackSignatures: Set<String> = emptySet(),
        diversify: Boolean = true
    ): List<AlbumatyLink> {
        val normRequest = normalizeArabicText(request)
        val matchedCategory = category ?: MoodCategory.entries.firstOrNull {
            normalizeArabicText(it.title) == normRequest || normRequest.contains(normalizeArabicText(it.title))
        }

        // 1. Deduplicate by BOTH exact URL and normalized (title, artist) signature
        val seenUrls = HashSet<String>()
        val seenSignatures = HashSet<String>()
        val deduped = candidates.filter { track ->
            val urlKey = track.url.trim().trimEnd('/').lowercase()
            val sigKey = normalizeTrackSignature(track)
            seenUrls.add(urlKey) && (sigKey.isBlank() || seenSignatures.add(sigKey))
        }

        // 2. Enforce strict mood exclusion rules
        val filteredCandidates = deduped.filter { track ->
            val catAllowed = matchedCategory?.isAllowed(track.title) ?: true
            val freeFormAllowed = isFreeFormTrackAllowed(normRequest, track.title)
            catAllowed && freeFormAllowed
        }
        if (filteredCandidates.isEmpty()) return emptyList()

        // 3. Score tracks using preferred artists, novelty (not in recent history), and literal-word penalty
        val literalMoodWords = setOf("حزين", "حزينه", "سفر", "نوم", "استرخاء", "هدوء", "رياضه", "سعاده", "بهجه", "رومانسيه")
        val preferredNorms = matchedCategory?.preferredArtists?.map { normalizeArabicText(it) }.orEmpty()

        val scored = filteredCandidates.map { track ->
            val sig = normalizeTrackSignature(track)
            val normTitle = normalizeArabicText(track.title)
            var score = 0
            if (sig !in recentTrackSignatures) score += 100
            if (preferredNorms.any { artist -> normTitle.contains(artist) }) score += 25
            val isGenericMoodRequest = literalMoodWords.any { normRequest == it || normRequest.contains(it) }
            if (isGenericMoodRequest && normTitle.contains(normRequest) && normRequest.length <= 8) {
                score -= 40
            }
            track to score
        }

        // Keep unseen fresh tracks first, then previously heard tracks, preserving relative ranking
        val orderedCandidates = scored
            .sortedByDescending { it.second }
            .map { it.first }

        if (!diversify) {
            return orderedCandidates
        }

        // 4. Group candidates by normalized artist/bucket so no single singer dominates
        val buckets = linkedMapOf<String, MutableList<AlbumatyLink>>()
        for (track in orderedCandidates) {
            val bucketKey = extractArtistBucket(track)
            buckets.getOrPut(bucketKey) { mutableListOf() }.add(track)
        }

        val interleaved = mutableListOf<AlbumatyLink>()
        val perArtistCounts = HashMap<String, Int>()
        val overflow = mutableListOf<AlbumatyLink>()
        val maxPerArtistPass = 4

        val iterators = buckets.entries.map { it.key to it.value.iterator() }.toMutableList()
        while (iterators.isNotEmpty()) {
            val outer = iterators.iterator()
            while (outer.hasNext()) {
                val (artistKey, bucketIt) = outer.next()
                if (bucketIt.hasNext()) {
                    val item = bucketIt.next()
                    val count = perArtistCounts[artistKey] ?: 0
                    if (count < maxPerArtistPass) {
                        interleaved.add(item)
                        perArtistCounts[artistKey] = count + 1
                    } else {
                        overflow.add(item)
                    }
                } else {
                    outer.remove()
                }
            }
        }

        return interleaved + overflow
    }

    private fun extractArtistBucket(track: AlbumatyLink): String {
        val cleanTitle = track.title
            .replace(Regex("^(?:اغنية|أغنية|تحميل|استماع)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*MP3\\s*$", RegexOption.IGNORE_CASE), "")
            .trim()
        val rawArtist = when {
            cleanTitle.contains(" - ") -> cleanTitle.substringAfterLast(" - ").trim()
            cleanTitle.contains("-") -> cleanTitle.substringAfterLast("-").trim()
            else -> cleanTitle.split(Regex("\\s+")).take(2).joinToString(" ")
        }
        return normalizeArabicText(rawArtist).ifBlank { track.url }
    }

    private fun isFreeFormTrackAllowed(normRequest: String, title: String): Boolean {
        val normTitle = normalizeArabicText(title)
        val isSadQuery = listOf("حزين", "حزينه", "فراق", "دموع", "وجع", "جرح").any { normRequest.contains(it) }
        if (isSadQuery && listOf("مهرجان", "فرح", "رقص", "زفه", "سنه حلوه", "اليوم الحلو").any { normTitle.contains(it) }) {
            return false
        }
        val isHappyOrPartyQuery = listOf("فرح", "سعاده", "بهجه", "رقص", "ميلاد").any { normRequest.contains(it) }
        if (isHappyOrPartyQuery && listOf("حزين", "دموع", "فراق", "جرح", "موجوع", "وداع").any { normTitle.contains(it) }) {
            return false
        }
        val isCalmQuery = listOf("هدوء", "نوم", "استرخاء", "ليل", "هادئ").any { normRequest.contains(it) }
        if (isCalmQuery && listOf("مهرجان", "رقص", "زفه", "وسع وسع", "شاكوش", "بيكا", "صاصا").any { normTitle.contains(it) }) {
            return false
        }
        return true
    }

    override fun close() = Unit

    companion object {
        fun normalizeArabicText(text: String): String {
            return text.lowercase()
                .replace(Regex("[أإآ]"), "ا")
                .replace("ة", "ه")
                .replace("ى", "ي")
                .replace("ؤ", "و")
                .replace("ئ", "ي")
                .replace("ـ", "")
                .replace(Regex("[ًٌٍَُِّْ]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun normalizeTrackSignature(link: AlbumatyLink): String {
            var cleaned = link.title
                .replace(Regex("^(?:اغنية|أغنية|تحميل|استماع)\\s+", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*(?:MP3|كاملة|حصريا|بالكلمات)\\s*", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
            cleaned = normalizeArabicText(cleaned)
                .replace(Regex("[|•_]+"), " ")
                .replace(Regex("\\s*-\\s*"), " - ")
                .replace(Regex("[^\\p{L}\\p{Nd}\\s-]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (cleaned.isBlank()) return ""
            return if (cleaned.contains(" - ")) {
                val titlePart = cleaned.substringBeforeLast(" - ").trim()
                val artistPart = cleaned.substringAfterLast(" - ").trim()
                "$titlePart|$artistPart"
            } else {
                cleaned
            }
        }
    }
}
