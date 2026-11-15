package com.example.player

import android.net.Uri
import com.example.ImportFile
import com.example.ImportFolder
import com.example.MusicImportLogic
import com.example.model.AudioItem
import com.example.onlinemusic.AlbumatyLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UnifiedQueueAndFolderTest {

    private fun song(id: String, uri: String, title: String = id) = AudioItem(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = 120_000L,
        uri = Uri.parse(uri),
        sizeBytes = 10_000L
    )

    @Test
    fun `deduplicate removes duplicate stable keys preserving order`() {
        val a = song("track_1", "https://example.com/1.mp3")
        val b = song("track_2", "https://example.com/2.mp3")
        val aDup = song("track_1", "https://cdn.example.com/1.mp3")

        val result = UnifiedPlaybackQueue.deduplicate(listOf(a, b, aDup))
        assertEquals(listOf("track_1", "track_2"), result.map { it.id })
    }

    @Test
    fun `isSameQueue stays true when lazy stream URI resolves while stable id stays identical`() {
        val unresolved = song("https://albumaty.com/song/123", "https://albumaty.com/song/123")
        val resolved = unresolved.copy(uri = Uri.parse("https://cdn.albumaty.com/stream/123.mp3"))

        assertTrue(UnifiedPlaybackQueue.isSameQueue(listOf(unresolved), listOf(resolved)))
    }

    @Test
    fun `albumatyLinkToAudioItem extracts clean title and artist`() {
        val link = AlbumatyLink(
            title = "أغنية هيجيلي موجوع - تامر عاشور MP3",
            url = "https://www.albumaty.com/song/hegeely"
        )
        val item = UnifiedPlaybackQueue.albumatyLinkToAudioItem(link, fallbackAlbum = "حزين")
        assertEquals("هيجيلي موجوع", item.title)
        assertEquals("تامر عاشور", item.artist)
        assertEquals("حزين", item.album)
        assertEquals(link.url, item.id)
    }

    @Test
    fun `isPlayableAudioFile rejects zero byte, hidden dot files, images, and videos`() {
        assertTrue(MusicImportLogic.isPlayableAudioFile("/Music/track.mp3", "audio/mpeg", 4096L))
        assertTrue(MusicImportLogic.isPlayableAudioFile("/Music/track.flac", null, 4096L))
        assertTrue(MusicImportLogic.isPlayableAudioFile("/Music/track.ogg", "application/ogg", 4096L))

        assertFalse(MusicImportLogic.isPlayableAudioFile("/Music/empty.mp3", "audio/mpeg", 0L))
        assertFalse(MusicImportLogic.isPlayableAudioFile("/Music/.nomedia", null, 10L))
        assertFalse(MusicImportLogic.isPlayableAudioFile("/Music/.hidden.mp3", "audio/mpeg", 4096L))
        assertFalse(MusicImportLogic.isPlayableAudioFile("/Music/cover.jpg", "image/jpeg", 4096L))
        assertFalse(MusicImportLogic.isPlayableAudioFile("/Music/clip.mp4", "video/mp4", 4096L))
        assertFalse(MusicImportLogic.isPlayableAudioFile("/Music/notes.txt", "text/plain", 4096L))
    }

    @Test
    fun `directPlayableFolderItems and siblingFolderQueueForFile isolate folder scope and sort cleanly`() {
        val f1 = ImportFile(song("1", "content://media/1", "Zeta"), "/Music/Pop/z.mp3")
        val f2 = ImportFile(song("2", "content://media/2", "Alpha"), "/Music/Pop/a.mp3")
        val f2Dup = ImportFile(song("2", "content://media/2", "Alpha"), "/Music/Pop/a.mp3")
        val otherFolder = ImportFile(song("3", "content://media/3", "Beta"), "/Music/Rock/b.mp3")

        val popFolder = ImportFolder("Pop", "/Music/Pop").apply {
            files.addAll(listOf(f1, f2, f2Dup))
        }
        val direct = MusicImportLogic.directPlayableFolderItems(popFolder)
        assertEquals(listOf("Alpha", "Zeta"), direct.map { it.title })

        val (siblingQueue, startIndex) = MusicImportLogic.siblingFolderQueueForFile(
            allFiles = listOf(f1, otherFolder, f2),
            target = f1
        )
        assertEquals(listOf("Alpha", "Zeta"), siblingQueue.map { it.title })
        assertEquals(1, startIndex)
    }
}
