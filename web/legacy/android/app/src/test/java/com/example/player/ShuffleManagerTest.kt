package com.example.player

import android.net.Uri
import com.example.model.AudioItem
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShuffleManagerTest {

    private fun createDummySong(id: String, title: String): AudioItem {
        return AudioItem(
            id = id,
            title = title,
            artist = "Artist",
            album = "Album",
            durationMs = 180000L,
            uri = Uri.parse("content://media/audio/$id"),
            sizeBytes = 1024L
        )
    }

    private fun createDummySongs(count: Int): List<AudioItem> {
        return (1..count).map { createDummySong("song_$it", "Song $it") }
    }

    @Test
    fun `every cycle plays every song exactly once without repetition`() {
        val songs = createDummySongs(20)
        var state = ShuffleManager.startNewCycle(songs)

        assertEquals(20, state.currentOrder.size)
        assertEquals(0, state.currentIndex)
        assertEquals(1, state.cycleNumber)

        val playedSongsInCycle = mutableListOf<AudioItem>()
        state.currentSong?.let { playedSongsInCycle.add(it) }

        while (state.hasNext) {
            state = ShuffleManager.next(state, songs)
            assertEquals(1, state.cycleNumber)
            state.currentSong?.let { playedSongsInCycle.add(it) }
        }

        // Must have played all 20 songs
        assertEquals(20, playedSongsInCycle.size)
        // No duplicates within cycle
        val uniqueIds = playedSongsInCycle.map { it.id }.toSet()
        assertEquals(20, uniqueIds.size)
    }

    @Test
    fun `next cycle produces fresh permutation and avoids immediate repeat`() {
        val songs = createDummySongs(15)
        var state = ShuffleManager.startNewCycle(songs)

        val cycle1Order = state.currentOrder.map { it.id }

        // Exhaust cycle 1
        while (state.hasNext) {
            state = ShuffleManager.next(state, songs)
        }
        val lastSongCycle1 = state.currentSong

        // Transition to cycle 2
        state = ShuffleManager.next(state, songs)
        assertEquals(2, state.cycleNumber)
        assertEquals(0, state.currentIndex)
        val firstSongCycle2 = state.currentSong

        val cycle2Order = state.currentOrder.map { it.id }

        // Orders must not be identical
        assertNotEquals("Cycle 1 and 2 should have different random orders", cycle1Order, cycle2Order)
        // First song of cycle 2 must not be the same as the last song of cycle 1
        assertNotEquals(lastSongCycle1?.id, firstSongCycle2?.id)
        // Cycle 2 must contain all songs without duplicates
        assertEquals(15, cycle2Order.distinct().size)
    }

    @Test
    fun `state persistence preserves cycle, position, and order across restarts`() {
        val songs = createDummySongs(20)
        var state = ShuffleManager.startNewCycle(songs)

        // Advance 7 songs
        repeat(7) {
            state = ShuffleManager.next(state, songs)
        }
        assertEquals(7, state.currentIndex)
        val currentSongBeforeSave = state.currentSong
        val remainingBeforeSave = state.unplayedSongs.map { it.id }
        val orderBeforeSave = state.currentOrder.map { it.id }

        // Persist to JSON
        val json = state.toJson()

        // Restore from JSON (simulating app restart)
        val restoredState = ShuffleState.fromJson(JSONObject(json.toString()))

        assertEquals(state.cycleNumber, restoredState.cycleNumber)
        assertEquals(7, restoredState.currentIndex)
        assertEquals(currentSongBeforeSave?.id, restoredState.currentSong?.id)
        assertEquals(orderBeforeSave, restoredState.currentOrder.map { it.id })
        assertEquals(remainingBeforeSave, restoredState.unplayedSongs.map { it.id })
        assertEquals(12, restoredState.unplayedSongs.size) // 20 total - 7 played - 1 current = 12

        // Continuing playback from restored state does not generate a new cycle
        var resumedState = restoredState
        resumedState = ShuffleManager.next(resumedState, songs)
        assertEquals(8, resumedState.currentIndex)
        assertEquals(state.cycleNumber, resumedState.cycleNumber)
        assertEquals(orderBeforeSave[8], resumedState.currentSong?.id)
    }

    @Test
    fun `previous traverses playback history without reshuffling`() {
        val songs = createDummySongs(10)
        var state = ShuffleManager.startNewCycle(songs)

        val track0 = state.currentSong
        state = ShuffleManager.next(state, songs)
        val track1 = state.currentSong
        state = ShuffleManager.next(state, songs)
        val track2 = state.currentSong

        assertEquals(2, state.currentIndex)
        assertEquals(track2?.id, state.currentSong?.id)

        // Previous
        state = ShuffleManager.previous(state)
        assertEquals(1, state.currentIndex)
        assertEquals(track1?.id, state.currentSong?.id)

        state = ShuffleManager.previous(state)
        assertEquals(0, state.currentIndex)
        assertEquals(track0?.id, state.currentSong?.id)

        // Previous at 0 should stay at 0
        assertFalse(state.hasPrevious)
        state = ShuffleManager.previous(state)
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun `library modifications update cycle cleanly`() {
        val initialSongs = createDummySongs(5)
        var state = ShuffleManager.startNewCycle(initialSongs)

        // Advance 2 songs
        state = ShuffleManager.next(state, initialSongs)
        state = ShuffleManager.next(state, initialSongs)
        val curSong = state.currentSong
        assertNotNull(curSong)

        // Library updated: song_1 deleted, song_6 and song_7 added
        val updatedLibrary = initialSongs.filter { it.id != "song_1" } + listOf(
            createDummySong("song_6", "Song 6"),
            createDummySong("song_7", "Song 7")
        )

        val syncedState = ShuffleManager.onLibraryChanged(state, updatedLibrary)

        // Current song must still be the active one
        assertEquals(curSong?.id, syncedState.currentSong?.id)
        // Deleted song_1 must no longer be in currentOrder
        assertFalse(syncedState.currentOrder.any { it.id == "song_1" })
        // New songs must be included in currentOrder
        assertTrue(syncedState.currentOrder.any { it.id == "song_6" })
        assertTrue(syncedState.currentOrder.any { it.id == "song_7" })
        // Total songs should now be 5 - 1 + 2 = 6
        assertEquals(6, syncedState.currentOrder.size)
    }
}
