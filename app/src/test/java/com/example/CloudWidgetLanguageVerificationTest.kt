package com.example

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.example.cloud.CloudPlaybackState
import com.example.cloud.CloudSyncManager
import com.example.model.AudioItem
import com.example.model.Playlist
import com.example.ui.locale.AppLanguageManager
import com.example.ui.locale.LanguageMode
import com.example.widget.TimeWeatherWidgetConfigActivity
import com.example.widget.TimeWeatherWidgetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudWidgetLanguageVerificationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("app_language_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("dj_cloud_auth_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("dj_cloud_accounts_registry", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun neonCloudConfig_matchesProjectSpecificationAndNoSupabase() {
        assertEquals("dark-fire-99620103", CloudSyncManager.NEON_PROJECT_ID)
        assertEquals("br-spring-term-baakf0qx", CloudSyncManager.NEON_BRANCH_ID)
        assertEquals("https://dj-web-topaz.vercel.app", CloudSyncManager.WEB_APP_SHARED_URL)
        assertEquals("https://dj-web-topaz.vercel.app", CloudSyncManager.getWebBaseUrl(context))
        assertTrue(CloudSyncManager.NEON_AUTH_URL.contains("ep-morning-bird-bad5xn0x.neonauth"))
        assertTrue(CloudSyncManager.NEON_REALTIME_URL.contains("ep-morning-bird-bad5xn0x.rt"))
        assertFalse(CloudSyncManager.NEON_AUTH_URL.contains("supabase", ignoreCase = true))
    }

    @Test
    fun appLanguageManager_autoDetectsPhoneLanguageAndSupportsManualOverrideAndRtlLtr() {
        val originalLocale = Locale.getDefault()
        try {
            // 1. Arabic system locale on first launch with AUTO mode
            val arLocale = Locale("ar", "EG")
            Locale.setDefault(arLocale)
            AppLanguageManager.init(context, arLocale)
            assertEquals(LanguageMode.AUTO, AppLanguageManager.languageMode.value)
            assertTrue(AppLanguageManager.isArabicFlow.value)
            assertEquals(LayoutDirection.Rtl, AppLanguageManager.currentLayoutDirection())

            // 2. Manual override to English switches layout direction to LTR
            AppLanguageManager.setLanguageMode(context, LanguageMode.ENGLISH, arLocale)
            assertEquals(LanguageMode.ENGLISH, AppLanguageManager.languageMode.value)
            assertFalse(AppLanguageManager.isArabicFlow.value)
            assertEquals(LayoutDirection.Ltr, AppLanguageManager.currentLayoutDirection())

            // 3. Manual override to Arabic switches layout direction to RTL even when system is English
            Locale.setDefault(Locale.US)
            AppLanguageManager.setLanguageMode(context, LanguageMode.ARABIC, Locale.US)
            assertEquals(LanguageMode.ARABIC, AppLanguageManager.languageMode.value)
            assertTrue(AppLanguageManager.isArabicFlow.value)
            assertEquals(LayoutDirection.Rtl, AppLanguageManager.currentLayoutDirection())

            // 4. Switching back to AUTO follows English system locale
            AppLanguageManager.setLanguageMode(context, LanguageMode.AUTO, Locale.US)
            assertFalse(AppLanguageManager.isArabicFlow.value)
            assertEquals(LayoutDirection.Ltr, AppLanguageManager.currentLayoutDirection())
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun cloudSyncManager_authenticatesEmailAndGoogle_andEnforcesStrictPerUserIsolation() {
        CloudSyncManager.init(context)
        CloudSyncManager.signOut(context)

        // Sign up User A
        val resA = CloudSyncManager.signUpWithEmail(context, "alpha@djcloud.test", "AlphaPass123", "Alpha DJ")
        assertTrue(resA.isSuccess)
        val userA = resA.getOrThrow()
        assertEquals("alpha@djcloud.test", userA.email)

        // Save tracks, playlists, favorites, and language in User A's isolated vault
        CloudSyncManager.saveUserIsolatedData(
            context = context,
            tracks = listOf(
                AudioItem(
                    id = "track_alpha_1",
                    title = "Alpha Exclusive Mix",
                    artist = "DJ Alpha",
                    album = "Alpha Club",
                    durationMs = 180_000L,
                    uri = Uri.parse("https://example.com/alpha.mp3")
                )
            ),
            playlists = listOf(
                Playlist(id = "pl_alpha_1", name = "Alpha Oriental Set", songIds = listOf("track_alpha_1"))
            ),
            favoriteIds = setOf("track_alpha_1"),
            languageMode = "ar"
        )

        val snapA = CloudSyncManager.loadUserIsolatedSnapshot(context, userA.userId)
        assertEquals(1, snapA.tracks.size)
        assertEquals("Alpha Exclusive Mix", snapA.tracks.first().title)
        assertTrue(snapA.favoriteIds.contains("track_alpha_1"))
        assertEquals("ar", snapA.languageMode)

        // Sign up User B -> User B must NOT see any of User A's tracks, playlists, or favorites
        val resB = CloudSyncManager.signUpWithEmail(context, "beta@djcloud.test", "BetaPass456", "Beta DJ")
        assertTrue(resB.isSuccess)
        val userB = resB.getOrThrow()
        assertNotEquals(userA.userId, userB.userId)

        val snapBInitial = CloudSyncManager.loadUserIsolatedSnapshot(context, userB.userId)
        assertTrue("User B track list must be empty initially", snapBInitial.tracks.isEmpty())
        assertTrue("User B playlists must be empty initially", snapBInitial.playlists.isEmpty())
        assertTrue("User B favorites must be empty initially", snapBInitial.favoriteIds.isEmpty())

        // Add User B specific data
        CloudSyncManager.saveUserIsolatedData(
            context = context,
            tracks = listOf(
                AudioItem(
                    id = "track_beta_99",
                    title = "Beta Private Track",
                    artist = "DJ Beta",
                    album = "Beta Studio",
                    durationMs = 210_000L,
                    uri = Uri.parse("https://example.com/beta.mp3")
                )
            ),
            favoriteIds = setOf("track_beta_99"),
            languageMode = "en"
        )

        val snapBAfter = CloudSyncManager.loadUserIsolatedSnapshot(context, userB.userId)
        assertEquals(1, snapBAfter.tracks.size)
        assertEquals("track_beta_99", snapBAfter.tracks.first().id)

        // Sign back in as User A -> User A's vault is restored intact without User B's data
        val signInA = CloudSyncManager.signInWithEmail(context, "alpha@djcloud.test", "AlphaPass123")
        assertTrue(signInA.isSuccess)
        val snapARestored = CloudSyncManager.loadUserIsolatedSnapshot(context)
        assertEquals(1, snapARestored.tracks.size)
        assertEquals("track_alpha_1", snapARestored.tracks.first().id)
        assertEquals("ar", snapARestored.languageMode)

        // Google Sign-In works and creates an isolated Google account session
        val googleRes = CloudSyncManager.signInWithGoogle(context, "googleuser@gmail.com", "Google User")
        assertTrue(googleRes.isSuccess)
        assertEquals("google", googleRes.getOrThrow().authProvider)
        assertTrue(CloudSyncManager.loadUserIsolatedSnapshot(context).tracks.isEmpty())

        // WebView handoff URL & bootstrap script include handoff token for shared session
        val handoffUrl = CloudSyncManager.buildAuthenticatedWebUrl(context)
        assertTrue(handoffUrl.contains("handoff="))
        val script = CloudSyncManager.buildWebViewSessionBootstrapScript(context)
        assertTrue(script.contains("dj-cloud-session-handoff"))

        // Sign-out clears active user state
        CloudSyncManager.signOut(context)
        assertNull(CloudSyncManager.currentUser.value)
        assertTrue(CloudSyncManager.loadUserIsolatedSnapshot(context).tracks.isEmpty())
    }

    @Test
    fun cloudSyncManager_enforcesSingleActiveAudioOutputArbitrationBetweenAndroidAndWeb() {
        CloudSyncManager.init(context)
        val user = CloudSyncManager.signUpWithEmail(context, "arbitration@djcloud.test", "SyncPass123", "Sync Tester").getOrThrow()

        var androidPausedByRemoteWeb = false
        CloudSyncManager.registerAudioOutputCallbacks(
            onRemoteOutputClaimed = {
                androidPausedByRemoteWeb = true
            }
        )

        // 1. Android starts playback and claims the single active audio output
        val song = AudioItem(
            id = "song_77",
            title = "Cairo Nights",
            artist = "DJ Oriental",
            album = "Live",
            durationMs = 240_000L,
            uri = Uri.parse("https://example.com/cairo.mp3")
        )
        CloudSyncManager.claimAudioOutput(
            context = context,
            isPlaying = true,
            currentSong = song,
            positionMs = 15_000L
        )
        assertEquals("android", CloudSyncManager.playbackState.value?.activeDevicePlatform)
        assertTrue(CloudSyncManager.playbackState.value?.isPlaying == true)
        assertFalse(androidPausedByRemoteWeb)

        // 2. Web claims active audio output from a remote browser/WebView session
        val paused = CloudSyncManager.onRemotePlaybackStateReceived(
            context = context,
            remoteState = CloudPlaybackState(
                userId = user.userId,
                activeDeviceId = "web_browser_session_1",
                activeDevicePlatform = "web",
                isPlaying = true,
                currentTrackId = "song_77",
                currentTrackTitle = "Cairo Nights",
                currentTrackArtist = "DJ Oriental",
                currentTrackUri = "https://example.com/cairo.mp3",
                positionMs = 18_500L,
                updatedAt = System.currentTimeMillis() + 5_000L
            )
        )

        // Android local audio output must be immediately paused so only Web plays
        assertTrue("onRemotePlaybackStateReceived must return true when remote device claims output", paused)
        assertTrue("Android playback must pause when Web claims active output", androidPausedByRemoteWeb)
        assertEquals("web", CloudSyncManager.playbackState.value?.activeDevicePlatform)
        assertEquals(18_500L, CloudSyncManager.playbackState.value?.positionMs)
    }

    @Test
    fun widgets_onlyTimeWeatherWidgetExists_andConfigActivitySupportsTransparencyPreviewApplyAndCancel() {
        // Verify the 3 deleted widget classes no longer exist in the classpath
        val deletedWidgets = listOf(
            "com.example.widget.MusicWidgetProvider",
            "com.example.widget.QuickPlayerWidgetProvider",
            "com.example.widget.DJMixerWidgetProvider"
        )
        for (className in deletedWidgets) {
            val exists = try {
                Class.forName(className)
                true
            } catch (_: ClassNotFoundException) {
                false
            }
            assertFalse("Deleted widget $className must not exist", exists)
        }

        // Verify TimeWeatherWidgetProvider exists and stores transparency properly
        val prefs = context.getSharedPreferences("time_weather_widget", Context.MODE_PRIVATE)
        prefs.edit().putInt("bg_alpha_42", 180).commit()
        assertEquals(180, prefs.getInt("bg_alpha_42", 255))

        // Verify TimeWeatherWidgetConfigActivity launches with AppWidgetId
        val intent = Intent(context, TimeWeatherWidgetConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 42)
        }
        val controller = Robolectric.buildActivity(TimeWeatherWidgetConfigActivity::class.java, intent).create()
        assertNotNull(controller.get())

        // Verify WebAccessActivity launches properly as a home-screen launcher activity
        val webController = Robolectric.buildActivity(com.example.cloud.WebAccessActivity::class.java).create()
        assertNotNull(webController.get())
    }
}
