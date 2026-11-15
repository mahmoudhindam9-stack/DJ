package com.example.onlinemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPlaylistGeneratorTest {

    @Test
    fun `cacheKey varies across every request parameter`() {
        val base = PlaylistGenerationRequest(
            categoryId = "sad",
            prompt = "حزين",
            source = "albumaty",
            playlistSize = 50,
            diversify = true
        )
        val differentCat = base.copy(categoryId = "happy")
        val differentPrompt = base.copy(prompt = "حزين خليجي")
        val differentSource = base.copy(source = "audius")
        val differentSize = base.copy(playlistSize = 25)
        val differentDiversify = base.copy(diversify = false)

        val keys = setOf(
            base.cacheKey,
            differentCat.cacheKey,
            differentPrompt.cacheKey,
            differentSource.cacheKey,
            differentSize.cacheKey,
            differentDiversify.cacheKey
        )
        assertEquals(6, keys.size)
    }

    @Test
    fun `normalizeTrackSignature deduplicates repeated songs across formatting variations`() {
        val a = AlbumatyLink("أغنية هيجيلي موجوع - تامر عاشور MP3", "https://albumaty.com/song/1")
        val b = AlbumatyLink("تحميل هيجيلي موجوع - تامر عاشور (كاملة)", "https://albumaty.com/song/mirror-1")

        assertEquals(
            LocalMusicAssistant.normalizeTrackSignature(a),
            LocalMusicAssistant.normalizeTrackSignature(b)
        )
    }

    @Test
    fun `rankAndDiversifyTracks filters excluded terms, prioritizes unseen tracks, and interleaves artists`() {
        val assistant = LocalMusicAssistant()
        val sadCategory = MoodCategory.entries.first { it.id == "sad" }

        val candidates = listOf(
            AlbumatyLink("هيجيلي موجوع - تامر عاشور", "https://albumaty.com/1"),
            AlbumatyLink("مهرجان فرح وزفة - حمو بيكا", "https://albumaty.com/2"), // excluded by sad
            AlbumatyLink("ياريتك فاهمني - تامر عاشور", "https://albumaty.com/3"),
            AlbumatyLink("كده يا قلبي - شيرين", "https://albumaty.com/4"),
            AlbumatyLink("اتنسيت - مسلم", "https://albumaty.com/5"),
            // Duplicate of #1 under different mirror URL & prefix
            AlbumatyLink("أغنية هيجيلي موجوع - تامر عاشور MP3", "https://albumaty.com/1-dup")
        )

        val recentSig = LocalMusicAssistant.normalizeTrackSignature(candidates[0])
        val ranked = assistant.rankAndDiversifyTracks(
            request = "حزين",
            candidates = candidates,
            category = sadCategory,
            recentTrackSignatures = setOf(recentSig),
            diversify = true
        )

        // Excluded festive track must be removed; duplicate mirror must be removed -> 4 unique allowed tracks
        assertEquals(4, ranked.size)
        assertFalse(ranked.any { it.url == "https://albumaty.com/2" })
        assertFalse(ranked.any { it.url == "https://albumaty.com/1-dup" })

        // Unseen tracks (3, 4, 5) must appear before previously heard track (1)
        val heardIdx = ranked.indexOfFirst { it.url == "https://albumaty.com/1" }
        assertEquals(3, heardIdx)
    }

    @Test
    fun `distinct mood categories yield non-identical semantic query pools`() {
        val assistant = LocalMusicAssistant()
        val sad = MoodCategory.entries.first { it.id == "sad" }
        val happy = MoodCategory.entries.first { it.id == "happy" }
        val gulfClassic = MoodCategory.entries.first { it.id == "classic-gulf" }

        val sadQueries = assistant.buildSmartVibeQueries(sad.title, sad.searchTerms).toSet()
        val happyQueries = assistant.buildSmartVibeQueries(happy.title, happy.searchTerms).toSet()
        val gulfQueries = assistant.buildSmartVibeQueries(gulfClassic.title, gulfClassic.searchTerms).toSet()

        assertNotEquals(sadQueries, happyQueries)
        assertNotEquals(sadQueries, gulfQueries)
        assertNotEquals(happyQueries, gulfQueries)
        assertTrue(sadQueries.isNotEmpty() && happyQueries.isNotEmpty() && gulfQueries.isNotEmpty())
    }
}
