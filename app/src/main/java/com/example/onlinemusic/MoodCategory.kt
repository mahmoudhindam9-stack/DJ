package com.example.onlinemusic

data class MoodCategory(
    val id: String,
    val title: String,
    val searchTerms: List<String>,
    val evidence: List<String>,
    val requiredEvidence: List<String> = emptyList()
) {
    fun matches(title: String): Boolean {
        val normalized = title.lowercase()
        val hasMoodEvidence = evidence.any { normalized.contains(it.lowercase()) }
        val hasRequiredEvidence = requiredEvidence.isEmpty() ||
            requiredEvidence.any { normalized.contains(it.lowercase()) }
        return hasMoodEvidence && hasRequiredEvidence
    }

    companion object {
        private val egyptianTerms = listOf("مصري", "مصرية", "مصر", "صعيدي", "صعيدية")
        private val gulfTerms = listOf(
            "خليجي", "خليجية", "سعودي", "سعودية", "كويتي", "كويتية",
            "إماراتي", "إماراتية", "عماني", "عمانية", "بحريني", "بحرينية",
            "يمني", "يمنية"
        )
        private val levantTerms = listOf(
            "شامي", "شامية", "سوري", "سورية", "لبناني", "لبنانية",
            "أردني", "أردنية", "فلسطيني", "فلسطينية"
        )
        private val iraqiTerms = listOf("عراقي", "عراقية", "بغدادي", "بغدادية")
        private val maghrebTerms = listOf(
            "مغربي", "مغربية", "مغاربي", "مغاربية", "جزائري", "جزائرية",
            "تونسي", "تونسية", "ليبي", "ليبية", "موريتاني", "موريتانية"
        )

        val entries = listOf(
            MoodCategory("sad", "حزين", listOf("أغاني حزينة", "اغاني حزينه", "حزن وفراق"), listOf("حزين", "حزينة", "حزينه", "فراق", "وداع", "دموع", "جرح", "خذلان")),
            MoodCategory("happy", "سعادة وبهجة", listOf("أغاني فرح وسعادة", "اغاني مبهجة"), listOf("فرح", "سعادة", "مبسوط", "بهجة", "ضحكة", "فرحان")),
            MoodCategory("drive", "قيادة وسفر", listOf("أغاني طريق وسفر", "اغاني سفر"), listOf("سفر", "طريق", "مسافر", "رحلة")),
            MoodCategory("midnight", "هدوء منتصف الليل", listOf("أغاني هادئة ليلية", "موسيقى هادئة"), listOf("ليل", "ليلية", "هدوء", "سكون")),
            MoodCategory("sleep", "نوم واسترخاء", listOf("أغاني للنوم والاسترخاء", "موسيقى استرخاء"), listOf("نوم", "استرخاء", "راحة", "تهدئة", "هادئة")),
            MoodCategory("dance", "أفراح ورقص", listOf("أغاني أفراح ورقص", "اغاني زفاف"), listOf("زفة", "فرح", "رقص", "أفراح", "دخلة", "حنة")),
            MoodCategory("birthday", "عيد الميلاد", listOf("أغاني عيد ميلاد", "اغنية عيد ميلاد"), listOf("عيد ميلاد", "ميلاد", "هابي بيرثداي", "birthday")),
            MoodCategory("romance", "رومانسية", listOf("أغاني رومانسية وحب", "اغاني حب"), listOf("حب", "حبيبي", "حبيبتي", "عشق", "رومانسي", "قلبي")),
            MoodCategory("sport", "رياضة وحماس", listOf("أغاني حماس وتشجيع", "اغاني رياضية"), listOf("حماس", "تشجيع", "بطل", "انتصار", "قوة", "تحدي")),
            MoodCategory("new", "أحدث الإصدارات", listOf("أحدث الأغاني الجديدة", "أغاني 2026"), listOf("2026", "جديد", "جديدة", "حصريا", "حصري")),
            MoodCategory("trending", "ترند الآن", listOf("أغاني ترند", "الأكثر استماعا"), listOf("ترند", "trend", "تيك توك", "tiktok", "viral")),
            MoodCategory("classic", "قديم وطرب أصيل", listOf("أغاني الزمن الجميل", "طرب أصيل", "أغاني قديمة"), listOf("قديم", "الزمن الجميل", "طرب", "كلاسيك")),
            MoodCategory("mawawil", "مواويل", listOf("مواويل مصرية", "موال حزين", "مواويل عربية"), listOf("موال", "مواويل", "موال حزين", "موال صعيدي")),
            MoodCategory("classic-eg", "قديم • مصري", listOf("أغاني مصرية قديمة", "طرب مصري قديم"), listOf("قديم", "الزمن الجميل", "طرب", "كلاسيك"), egyptianTerms),
            MoodCategory("classic-gulf", "قديم • خليجي", listOf("أغاني خليجية قديمة", "طرب خليجي قديم"), listOf("قديم", "الزمن الجميل", "طرب", "كلاسيك"), gulfTerms),
            MoodCategory("classic-levant", "قديم • شامي", listOf("أغاني شامية قديمة", "طرب شامي قديم"), listOf("قديم", "الزمن الجميل", "طرب", "كلاسيك"), levantTerms),
            MoodCategory("classic-iraqi", "قديم • عراقي", listOf("أغاني عراقية قديمة", "طرب عراقي قديم"), listOf("قديم", "الزمن الجميل", "طرب", "كلاسيك"), iraqiTerms),
            MoodCategory("classic-maghreb", "قديم • مغاربي", listOf("أغاني مغاربية قديمة", "طرب مغاربي قديم"), listOf("قديم", "الزمن الجميل", "طرب", "كلاسيك"), maghrebTerms),
            MoodCategory("mawawil-eg", "مواويل • مصري", listOf("مواويل مصرية", "موال صعيدي"), listOf("موال", "مواويل"), egyptianTerms),
            MoodCategory("mawawil-gulf", "مواويل • خليجي", listOf("مواويل خليجية", "موال خليجي"), listOf("موال", "مواويل"), gulfTerms),
            MoodCategory("mawawil-levant", "مواويل • شامي", listOf("مواويل شامية", "موال شامي"), listOf("موال", "مواويل"), levantTerms),
            MoodCategory("mawawil-iraqi", "مواويل • عراقي", listOf("مواويل عراقية", "موال عراقي"), listOf("موال", "مواويل"), iraqiTerms),
            MoodCategory("mawawil-maghreb", "مواويل • مغاربي", listOf("مواويل مغاربية", "موال مغاربي"), listOf("موال", "مواويل"), maghrebTerms)
        )
    }
}
