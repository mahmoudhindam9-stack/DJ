package com.example.cloud

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage
import com.example.model.AudioItem
import com.example.model.Playlist
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID

data class CloudUser(
    val userId: String,
    val email: String,
    val displayName: String,
    val authProvider: String, // "email" or "google"
    val sessionToken: String
)

data class CloudPlaybackState(
    val userId: String,
    val activeDeviceId: String,
    val activeDevicePlatform: String, // "android" or "web"
    val isPlaying: Boolean,
    val currentTrackId: String = "",
    val currentTrackTitle: String = "",
    val currentTrackArtist: String = "",
    val currentTrackUri: String = "",
    val positionMs: Long = 0L,
    val playbackMode: String = "NORMAL",
    val queueIds: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class UserIsolatedDataSnapshot(
    val userId: String,
    val tracks: List<AudioItem>,
    val playlists: List<Playlist>,
    val favoriteIds: Set<String>,
    val languageMode: String,
    val themeName: String,
    val playbackState: CloudPlaybackState?
)

object CloudSyncManager {
    const val NEON_PROJECT_ID = "dark-fire-99620103"
    const val NEON_BRANCH_ID = "br-spring-term-baakf0qx"
    const val NEON_AUTH_URL = "https://ep-morning-bird-bad5xn0x.neonauth.c-8.eu-central-1.aws.neon.tech/neondb/auth"
    const val NEON_JWKS_URL = "https://ep-morning-bird-bad5xn0x.neonauth.c-8.eu-central-1.aws.neon.tech/neondb/auth/.well-known/jwks.json"
    const val NEON_REALTIME_URL = "https://ep-morning-bird-bad5xn0x.rt.c-8.eu-central-1.aws.neon.tech/"

    const val WEB_APP_SHARED_URL = "https://dj-web-topaz.vercel.app"
    const val WEB_APP_DEV_URL = "https://dj-web-topaz.vercel.app"

    private const val PREFS_AUTH = "dj_cloud_auth_prefs"
    private const val PREFS_ACCOUNTS_REGISTRY = "dj_cloud_accounts_registry"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_ACTIVE_USER_ID = "active_user_id"
    private const val KEY_ACTIVE_EMAIL = "active_email"
    private const val KEY_ACTIVE_NAME = "active_display_name"
    private const val KEY_ACTIVE_PROVIDER = "active_auth_provider"
    private const val KEY_ACTIVE_TOKEN = "active_session_token"
    private const val KEY_ACTIVE_CLOUD_READY = "active_cloud_session_ready"
    private const val KEY_WEB_BASE_URL = "web_base_url"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncPollingJob: Job? = null

    private val _currentUser = MutableStateFlow<CloudUser?>(null)
    val currentUser: StateFlow<CloudUser?> = _currentUser.asStateFlow()

    private val _playbackState = MutableStateFlow<CloudPlaybackState?>(null)
    val playbackState: StateFlow<CloudPlaybackState?> = _playbackState.asStateFlow()

    private val _syncStatus = MutableStateFlow("Ready")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _deviceIdFlow = MutableStateFlow("")
    val deviceIdFlow: StateFlow<String> = _deviceIdFlow.asStateFlow()

    private var onRemoteClaimedAudioOutput: (() -> Unit)? = null
    private var onUserVaultLoaded: ((UserIsolatedDataSnapshot) -> Unit)? = null
    private val lastRemoteVaultSignature = mutableMapOf<String, String>()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
        var devId = prefs.getString(KEY_DEVICE_ID, null)
        if (devId.isNullOrBlank()) {
            devId = "android_" + UUID.randomUUID().toString().replace("-", "").take(12)
            prefs.edit().putString(KEY_DEVICE_ID, devId).apply()
        }
        _deviceIdFlow.value = devId

        // Ensure any legacy run.app URL is upgraded to the production Vercel URL
        val savedUrl = prefs.getString(KEY_WEB_BASE_URL, null)
        if (savedUrl.isNullOrBlank() || savedUrl.contains("run.app", ignoreCase = true)) {
            prefs.edit().putString(KEY_WEB_BASE_URL, WEB_APP_SHARED_URL).apply()
        }

        val userId = prefs.getString(KEY_ACTIVE_USER_ID, null)
        val email = prefs.getString(KEY_ACTIVE_EMAIL, null)
        val token = prefs.getString(KEY_ACTIVE_TOKEN, null)
        if (!userId.isNullOrBlank() && !email.isNullOrBlank() && !token.isNullOrBlank()) {
            val user = CloudUser(
                userId = userId,
                email = email,
                displayName = prefs.getString(KEY_ACTIVE_NAME, email.substringBefore("@")) ?: email,
                authProvider = prefs.getString(KEY_ACTIVE_PROVIDER, "email") ?: "email",
                sessionToken = token
            )
            _currentUser.value = user
            _playbackState.value = loadUserPlaybackState(context, userId)
            if (prefs.getBoolean(KEY_ACTIVE_CLOUD_READY, false)) {
                _syncStatus.value = "Neon Cloud connected"
                startRealtimeSyncLoop(context)
            } else {
                _syncStatus.value = "Local account active — sign in again to reconnect Neon Cloud"
            }
        }
    }

    fun getDeviceId(context: Context): String {
        if (_deviceIdFlow.value.isBlank()) {
            init(context)
        }
        return _deviceIdFlow.value
    }

    fun getWebBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_WEB_BASE_URL, WEB_APP_SHARED_URL) ?: WEB_APP_SHARED_URL
        if (saved.isBlank() || saved.contains("run.app", ignoreCase = true)) {
            prefs.edit().putString(KEY_WEB_BASE_URL, WEB_APP_SHARED_URL).apply()
            return WEB_APP_SHARED_URL
        }
        return saved
    }

    fun setWebBaseUrl(context: Context, url: String) {
        val cleaned = url.trim().trimEnd('/')
        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
            context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_WEB_BASE_URL, cleaned)
                .apply()
        }
    }

    fun registerAudioOutputCallbacks(
        onRemoteOutputClaimed: () -> Unit,
        onVaultRestored: ((UserIsolatedDataSnapshot) -> Unit)? = null
    ) {
        onRemoteClaimedAudioOutput = onRemoteOutputClaimed
        if (onVaultRestored != null) {
            onUserVaultLoaded = onVaultRestored
        }
    }

    /**
     * Email/Password Sign-Up against Neon Auth / Cloud API with deterministic local vault isolation.
     */
    fun signUpWithEmail(
        context: Context,
        email: String,
        password: String,
        displayName: String = ""
    ): Result<CloudUser> {
        val normalizedEmail = email.trim().lowercase()
        if (!normalizedEmail.contains("@") || !normalizedEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("Invalid email address"))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }

        val registry = context.getSharedPreferences(PREFS_ACCOUNTS_REGISTRY, Context.MODE_PRIVATE)
        val existingHash = registry.getString("pwd_$normalizedEmail", null)
        val userId = registry.getString("uid_$normalizedEmail", null)
            ?: ("usr_" + sha256(normalizedEmail).take(20))
        val passwordHash = sha256("$normalizedEmail:$password")

        if (existingHash != null && existingHash != passwordHash) {
            return Result.failure(IllegalStateException("Account already exists with a different password"))
        }

        val resolvedName = displayName.trim().ifBlank { normalizedEmail.substringBefore("@") }
        val sessionToken = issueSessionToken(userId, normalizedEmail, "email")

        registry.edit()
            .putString("uid_$normalizedEmail", userId)
            .putString("pwd_$normalizedEmail", passwordHash)
            .putString("name_$normalizedEmail", resolvedName)
            .putString("provider_$normalizedEmail", "email")
            .apply()

        val user = CloudUser(
            userId = userId,
            email = normalizedEmail,
            displayName = resolvedName,
            authProvider = "email",
            sessionToken = sessionToken
        )
        activateUserSession(context, user)
        syncAuthWithRemoteAsync(context, user, password, isSignUp = true)
        return Result.success(user)
    }

    /**
     * Email/Password Sign-In with verification and strict user_id vault loading.
     */
    fun signInWithEmail(
        context: Context,
        email: String,
        password: String
    ): Result<CloudUser> {
        val normalizedEmail = email.trim().lowercase()
        if (!normalizedEmail.contains("@") || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Email and password are required"))
        }

        val registry = context.getSharedPreferences(PREFS_ACCOUNTS_REGISTRY, Context.MODE_PRIVATE)
        val existingHash = registry.getString("pwd_$normalizedEmail", null)
        val passwordHash = sha256("$normalizedEmail:$password")

        if (existingHash != null && existingHash != passwordHash) {
            return Result.failure(SecurityException("Invalid email or password"))
        }

        val userId = registry.getString("uid_$normalizedEmail", null)
            ?: ("usr_" + sha256(normalizedEmail).take(20))
        val displayName = registry.getString("name_$normalizedEmail", null)
            ?: normalizedEmail.substringBefore("@")

        if (existingHash == null) {
            registry.edit()
                .putString("uid_$normalizedEmail", userId)
                .putString("pwd_$normalizedEmail", passwordHash)
                .putString("name_$normalizedEmail", displayName)
                .putString("provider_$normalizedEmail", "email")
                .apply()
        }

        val sessionToken = issueSessionToken(userId, normalizedEmail, "email")
        val user = CloudUser(
            userId = userId,
            email = normalizedEmail,
            displayName = displayName,
            authProvider = "email",
            sessionToken = sessionToken
        )
        activateUserSession(context, user)
        syncAuthWithRemoteAsync(context, user, password, isSignUp = false)
        return Result.success(user)
    }

    /**
     * Google Sign-In handler supporting Google Account email/idToken and isolating data by user_id.
     */
    fun signInWithGoogle(
        context: Context,
        googleEmail: String,
        displayName: String = "",
        idToken: String? = null
    ): Result<CloudUser> {
        val normalizedEmail = googleEmail.trim().lowercase()
        if (!normalizedEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Valid Google account email is required"))
        }

        val registry = context.getSharedPreferences(PREFS_ACCOUNTS_REGISTRY, Context.MODE_PRIVATE)
        val userId = registry.getString("uid_$normalizedEmail", null)
            ?: ("usr_" + sha256(normalizedEmail).take(20))
        val resolvedName = displayName.trim().ifBlank {
            registry.getString("name_$normalizedEmail", null) ?: normalizedEmail.substringBefore("@")
        }

        registry.edit()
            .putString("uid_$normalizedEmail", userId)
            .putString("name_$normalizedEmail", resolvedName)
            .putString("provider_$normalizedEmail", "google")
            .apply()

        val sessionToken = idToken?.takeIf { it.isNotBlank() }
            ?: issueSessionToken(userId, normalizedEmail, "google")

        val user = CloudUser(
            userId = userId,
            email = normalizedEmail,
            displayName = resolvedName,
            authProvider = "google",
            sessionToken = sessionToken
        )
        activateUserSession(context, user)
        syncGoogleAuthWithRemoteAsync(context, user, idToken)
        return Result.success(user)
    }

    /**
     * Signs out the active user, clears WebView cookies/storage, and clears in-memory session state
     * while preserving the user's isolated vault on disk/cloud for future sign-in.
     */
    fun signOut(context: Context) {
        syncPollingJob?.cancel()
        syncPollingJob = null

        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ACTIVE_USER_ID)
            .remove(KEY_ACTIVE_EMAIL)
            .remove(KEY_ACTIVE_NAME)
            .remove(KEY_ACTIVE_PROVIDER)
            .remove(KEY_ACTIVE_TOKEN)
            .putBoolean(KEY_ACTIVE_CLOUD_READY, false)
            .apply()

        _currentUser.value = null
        _playbackState.value = null
        _syncStatus.value = "Signed out"

        // Clear WebView shared session cookies and DOM storage when signing out
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }
    }

    private fun hasVerifiedCloudSession(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACTIVE_CLOUD_READY, false)
    }

    private fun activateUserSession(context: Context, user: CloudUser) {
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_USER_ID, user.userId)
            .putString(KEY_ACTIVE_EMAIL, user.email)
            .putString(KEY_ACTIVE_NAME, user.displayName)
            .putString(KEY_ACTIVE_PROVIDER, user.authProvider)
            .putString(KEY_ACTIVE_TOKEN, user.sessionToken)
            .putBoolean(KEY_ACTIVE_CLOUD_READY, false)
            .apply()

        syncPollingJob?.cancel()
        syncPollingJob = null
        _currentUser.value = user
        val snapshot = loadUserIsolatedSnapshot(context, user.userId)
        _playbackState.value = snapshot.playbackState
        _syncStatus.value = "Verifying Neon Cloud account…"
        onUserVaultLoaded?.invoke(snapshot)
    }

    private fun saveVerifiedRemoteSession(context: Context, user: CloudUser) {
        if (user.sessionToken.split('.').size != 3) {
            _syncStatus.value = "Cloud sign-in returned an invalid session."
            return
        }
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_USER_ID, user.userId)
            .putString(KEY_ACTIVE_EMAIL, user.email)
            .putString(KEY_ACTIVE_NAME, user.displayName)
            .putString(KEY_ACTIVE_PROVIDER, user.authProvider)
            .putString(KEY_ACTIVE_TOKEN, user.sessionToken)
            .putBoolean(KEY_ACTIVE_CLOUD_READY, true)
            .apply()
        _currentUser.value = user
        _syncStatus.value = "Neon Cloud connected"
        lastRemoteVaultSignature.remove(user.userId)
        startRealtimeSyncLoop(context)
    }

    /**
     * Saves user-scoped library, playlists, favorites, and settings strictly under `user.userId`.
     */
    fun saveUserIsolatedData(
        context: Context,
        tracks: List<AudioItem>? = null,
        playlists: List<Playlist>? = null,
        favoriteIds: Set<String>? = null,
        languageMode: String? = null,
        themeName: String? = null,
        forUserId: String? = _currentUser.value?.userId,
        syncToCloud: Boolean = true
    ) {
        val userId = forUserId ?: return
        val vault = context.getSharedPreferences("dj_user_vault_$userId", Context.MODE_PRIVATE)
        val editor = vault.edit()

        if (tracks != null) {
            val arr = JSONArray()
            tracks.forEach { item ->
                arr.put(JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("artist", item.artist)
                    put("album", item.album)
                    put("durationMs", item.durationMs)
                    put("uri", item.uri.toString())
                    put("sizeBytes", item.sizeBytes)
                })
            }
            editor.putString("tracks_json", arr.toString())
        }

        if (playlists != null) {
            val arr = JSONArray()
            playlists.forEach { pl ->
                arr.put(JSONObject().apply {
                    put("id", pl.id)
                    put("name", pl.name)
                    put("songIds", JSONArray(pl.songIds))
                })
            }
            editor.putString("playlists_json", arr.toString())
        }

        if (favoriteIds != null) {
            editor.putStringSet("favorite_ids", favoriteIds)
        }

        if (languageMode != null) {
            editor.putString("language_mode", languageMode)
        }

        if (themeName != null) {
            editor.putString("theme_name", themeName)
        }

        editor.putLong("updated_at", System.currentTimeMillis())
        editor.apply()

        if (syncToCloud && hasVerifiedCloudSession(context)) {
            pushStateToCloudAsync(context, userId)
        }
    }

    fun loadUserIsolatedSnapshot(
        context: Context,
        userId: String = _currentUser.value?.userId.orEmpty()
    ): UserIsolatedDataSnapshot {
        if (userId.isBlank()) {
            return UserIsolatedDataSnapshot(
                userId = "",
                tracks = emptyList(),
                playlists = emptyList(),
                favoriteIds = emptySet(),
                languageMode = "auto",
                themeName = "DARK",
                playbackState = null
            )
        }

        val vault = context.getSharedPreferences("dj_user_vault_$userId", Context.MODE_PRIVATE)
        val tracks = mutableListOf<AudioItem>()
        val tracksJson = vault.getString("tracks_json", "[]") ?: "[]"
        runCatching {
            val arr = JSONArray(tracksJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                tracks.add(
                    AudioItem(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        artist = obj.optString("artist"),
                        album = obj.optString("album"),
                        durationMs = obj.optLong("durationMs", obj.optLong("duration", 0L)),
                        uri = Uri.parse(obj.optString("uri", "")),
                        sizeBytes = obj.optLong("sizeBytes", 0L)
                    )
                )
            }
        }

        val playlists = mutableListOf<Playlist>()
        val playlistsJson = vault.getString("playlists_json", "[]") ?: "[]"
        runCatching {
            val arr = JSONArray(playlistsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val songArr = obj.optJSONArray("songIds") ?: JSONArray()
                val songIds = mutableListOf<String>()
                for (j in 0 until songArr.length()) {
                    songIds.add(songArr.getString(j))
                }
                playlists.add(
                    Playlist(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        songIds = songIds
                    )
                )
            }
        }

        val favorites = vault.getStringSet("favorite_ids", emptySet())?.toSet() ?: emptySet()
        val languageMode = vault.getString("language_mode", "auto") ?: "auto"
        val themeName = vault.getString("theme_name", "DARK") ?: "DARK"
        val playback = loadUserPlaybackState(context, userId)

        return UserIsolatedDataSnapshot(
            userId = userId,
            tracks = tracks,
            playlists = playlists,
            favoriteIds = favorites,
            languageMode = languageMode,
            themeName = themeName,
            playbackState = playback
        )
    }

    /**
     * Claims active audio output on this device (or updates playback state) and broadcasts to Web/other devices.
     * Guarantees single active audio output across Android and Web.
     */
    fun claimAudioOutput(
        context: Context,
        isPlaying: Boolean,
        currentSong: AudioItem? = null,
        positionMs: Long = 0L,
        playbackMode: String = "NORMAL",
        queueIds: List<String> = emptyList()
    ): CloudPlaybackState? {
        val user = _currentUser.value ?: return null
        val myDeviceId = getDeviceId(context)
        val state = CloudPlaybackState(
            userId = user.userId,
            activeDeviceId = myDeviceId,
            activeDevicePlatform = "android",
            isPlaying = isPlaying,
            currentTrackId = currentSong?.id.orEmpty(),
            currentTrackTitle = currentSong?.title.orEmpty(),
            currentTrackArtist = currentSong?.artist.orEmpty(),
            currentTrackUri = currentSong?.uri?.toString().orEmpty(),
            positionMs = positionMs,
            playbackMode = playbackMode,
            queueIds = queueIds,
            updatedAt = System.currentTimeMillis()
        )
        saveUserPlaybackState(context, state)
        _playbackState.value = state
        pushPlaybackToCloudAsync(context, user, state)
        return state
    }

    /**
     * Handles an incoming playback synchronization event from Web or another device.
     * If another device belonging to the same user starts playing, immediately pauses local Android audio output!
     */
    fun onRemotePlaybackStateReceived(
        context: Context,
        remoteState: CloudPlaybackState
    ): Boolean {
        val activeUser = _currentUser.value ?: return false
        if (remoteState.userId != activeUser.userId) {
            // Strictly ignore events from any other user_id
            return false
        }

        val myDeviceId = getDeviceId(context)
        saveUserPlaybackState(context, remoteState)
        _playbackState.value = remoteState

        if (remoteState.activeDeviceId != myDeviceId && remoteState.isPlaying) {
            _syncStatus.value = "Audio active on ${remoteState.activeDevicePlatform.uppercase()}"
            onRemoteClaimedAudioOutput?.invoke()
            return true
        }
        return false
    }

    private fun saveUserPlaybackState(context: Context, state: CloudPlaybackState) {
        val vault = context.getSharedPreferences("dj_user_vault_${state.userId}", Context.MODE_PRIVATE)
        val obj = JSONObject().apply {
            put("userId", state.userId)
            put("activeDeviceId", state.activeDeviceId)
            put("activeDevicePlatform", state.activeDevicePlatform)
            put("isPlaying", state.isPlaying)
            put("currentTrackId", state.currentTrackId)
            put("currentTrackTitle", state.currentTrackTitle)
            put("currentTrackArtist", state.currentTrackArtist)
            put("currentTrackUri", state.currentTrackUri)
            put("positionMs", state.positionMs)
            put("playbackMode", state.playbackMode)
            put("queueIds", JSONArray(state.queueIds))
            put("updatedAt", state.updatedAt)
        }
        vault.edit().putString("playback_state_json", obj.toString()).apply()
    }

    private fun loadUserPlaybackState(context: Context, userId: String): CloudPlaybackState? {
        val vault = context.getSharedPreferences("dj_user_vault_$userId", Context.MODE_PRIVATE)
        val raw = vault.getString("playback_state_json", null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            val qArr = obj.optJSONArray("queueIds") ?: JSONArray()
            val qList = mutableListOf<String>()
            for (i in 0 until qArr.length()) {
                qList.add(qArr.getString(i))
            }
            CloudPlaybackState(
                userId = obj.optString("userId", userId),
                activeDeviceId = obj.optString("activeDeviceId"),
                activeDevicePlatform = obj.optString("activeDevicePlatform", "android"),
                isPlaying = obj.optBoolean("isPlaying", false),
                currentTrackId = obj.optString("currentTrackId"),
                currentTrackTitle = obj.optString("currentTrackTitle"),
                currentTrackArtist = obj.optString("currentTrackArtist"),
                currentTrackUri = obj.optString("currentTrackUri"),
                positionMs = obj.optLong("positionMs", 0L),
                playbackMode = obj.optString("playbackMode", "NORMAL"),
                queueIds = qList,
                updatedAt = obj.optLong("updatedAt", 0L)
            )
        }.getOrNull()
    }

    /**
     * Generates a secure shared-session handoff URL for opening the Web app in WebView or external browser.
     */
    fun buildAuthenticatedWebUrl(context: Context): String {
        val baseUrl = getWebBaseUrl(context)
        val user = _currentUser.value ?: return baseUrl
        val authPrefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
        if (!authPrefs.getBoolean(KEY_ACTIVE_CLOUD_READY, false)) {
            // Never put a locally generated unsigned token into the browser handoff URL.
            return baseUrl
        }
        val handoffPayload = JSONObject().apply {
            put("userId", user.userId)
            put("email", user.email)
            put("displayName", user.displayName)
            put("authProvider", user.authProvider)
            put("sessionToken", user.sessionToken)
            put("sourceDeviceId", getDeviceId(context))
            put("issuedAt", System.currentTimeMillis())
        }.toString()
        val encoded = android.util.Base64.encodeToString(
            handoffPayload.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )
        val sep = if (baseUrl.contains("?")) "&" else "?"
        return "${baseUrl}${sep}handoff=${URLEncoder.encode(encoded, "UTF-8")}"
    }

    /**
     * Builds the JavaScript snippet injected into the embedded WebView after page load
     * so the Web app receives the authenticated Neon/DJ Cloud session without re-entering credentials.
     */
    fun buildWebViewSessionBootstrapScript(context: Context): String {
        if (!hasVerifiedCloudSession(context)) return ""
        val user = _currentUser.value ?: return ""
        val safeObj = JSONObject().apply {
            put("userId", user.userId)
            put("email", user.email)
            put("displayName", user.displayName)
            put("authProvider", user.authProvider)
            put("sessionToken", user.sessionToken)
            put("androidDeviceId", getDeviceId(context))
        }.toString()
        return """
            (function() {
                try {
                    var session = $safeObj;
                    localStorage.setItem('dj_cloud_session', JSON.stringify(session));
                    window.dispatchEvent(new CustomEvent('dj-cloud-session-handoff', { detail: session }));
                } catch (e) {}
            })();
        """.trimIndent()
    }

    private fun startRealtimeSyncLoop(context: Context) {
        syncPollingJob?.cancel()
        syncPollingJob = scope.launch {
            while (isActive) {
                val user = _currentUser.value ?: break
                pullCloudStateOnce(context, user)
                delay(4000L)
            }
        }
    }

    private fun syncAuthWithRemoteAsync(
        context: Context,
        user: CloudUser,
        password: String,
        isSignUp: Boolean
    ) {
        scope.launch {
            try {
                val endpoint = if (isSignUp) "/api/auth/signup" else "/api/auth/signin"
                val url = URL(getWebBaseUrl(context) + endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 10000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val body = JSONObject().apply {
                    put("email", user.email)
                    put("password", password)
                    put("displayName", user.displayName)
                    put("userId", user.userId)
                    put("deviceId", getDeviceId(context))
                }.toString()
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
                val responseCode = conn.responseCode
                val responseStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseText = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                conn.disconnect()
                if (responseCode !in 200..299) {
                    val message = runCatching { JSONObject(responseText).optString("error") }.getOrNull()
                    _syncStatus.value = message?.takeIf { it.isNotBlank() }
                        ?: "Neon Cloud authentication failed (HTTP $responseCode)."
                    return@launch
                }
                val json = JSONObject(responseText)
                val remoteToken = json.optString("sessionToken", "")
                val remoteUserId = json.optString("userId", "")
                if (remoteUserId.isBlank() || remoteToken.split('.').size != 3) {
                    _syncStatus.value = "Neon Cloud returned an invalid session. Local account remains available."
                    return@launch
                }
                val updated = user.copy(userId = remoteUserId, sessionToken = remoteToken)
                saveVerifiedRemoteSession(context, updated)
            } catch (error: Exception) {
                _syncStatus.value = "Neon Cloud could not be reached. Local account remains available."
            }
        }
    }

    private fun syncGoogleAuthWithRemoteAsync(
        context: Context,
        user: CloudUser,
        idToken: String?
    ) {
        scope.launch {
            try {
                if (idToken.isNullOrBlank()) {
                    _syncStatus.value = "Google account selection is local only. Configure Google OAuth ID-token sign-in to enable Neon Cloud."
                    return@launch
                }
                val url = URL(getWebBaseUrl(context) + "/api/auth/google")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 10000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val body = JSONObject().apply {
                    put("idToken", idToken)
                    put("deviceId", getDeviceId(context))
                }.toString()
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
                val responseCode = conn.responseCode
                val responseStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseText = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                conn.disconnect()
                if (responseCode !in 200..299) {
                    val message = runCatching { JSONObject(responseText).optString("error") }.getOrNull()
                    _syncStatus.value = message?.takeIf { it.isNotBlank() }
                        ?: "Google Cloud authentication failed (HTTP $responseCode)."
                    return@launch
                }
                val json = JSONObject(responseText)
                val remoteToken = json.optString("sessionToken", "")
                val remoteUserId = json.optString("userId", "")
                if (remoteUserId.isBlank() || remoteToken.split('.').size != 3) {
                    _syncStatus.value = "Google Cloud authentication returned an invalid session."
                    return@launch
                }
                val updated = user.copy(
                    userId = remoteUserId,
                    email = json.optString("email", user.email),
                    displayName = json.optString("displayName", user.displayName),
                    sessionToken = remoteToken
                )
                saveVerifiedRemoteSession(context, updated)
            } catch (error: Exception) {
                _syncStatus.value = "Google Cloud authentication could not be completed."
            }
        }
    }

    private fun pushPlaybackToCloudAsync(context: Context, user: CloudUser, state: CloudPlaybackState) {
        if (!hasVerifiedCloudSession(context)) return
        scope.launch {
            runCatching {
                val url = URL(getWebBaseUrl(context) + "/api/sync/playback")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 4000
                    readTimeout = 4000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer ${user.sessionToken}")
                    setRequestProperty("X-User-Id", user.userId)
                }
                val body = JSONObject().apply {
                    put("userId", state.userId)
                    put("activeDeviceId", state.activeDeviceId)
                    put("activeDevicePlatform", state.activeDevicePlatform)
                    put("isPlaying", state.isPlaying)
                    put("currentTrackId", state.currentTrackId)
                    put("currentTrackTitle", state.currentTrackTitle)
                    put("currentTrackArtist", state.currentTrackArtist)
                    put("currentTrackUri", state.currentTrackUri)
                    put("positionMs", state.positionMs)
                    put("playbackMode", state.playbackMode)
                    put("queueIds", JSONArray(state.queueIds))
                    put("updatedAt", state.updatedAt)
                }.toString()
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
                conn.responseCode
                conn.disconnect()
            }
        }
    }

    private fun pushStateToCloudAsync(context: Context, userId: String) {
        if (!hasVerifiedCloudSession(context)) return
        val user = _currentUser.value ?: return
        if (user.userId != userId) return
        scope.launch {
            runCatching {
                val snapshot = loadUserIsolatedSnapshot(context, userId)
                val url = URL(getWebBaseUrl(context) + "/api/sync/state")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    readTimeout = 5000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer ${user.sessionToken}")
                    setRequestProperty("X-User-Id", user.userId)
                }
                val tracksArr = JSONArray()
                snapshot.tracks.forEach { t ->
                    tracksArr.put(JSONObject().apply {
                        put("id", t.id)
                        put("title", t.title)
                        put("artist", t.artist)
                        put("album", t.album)
                        put("durationMs", t.durationMs)
                        put("uri", t.uri.toString())
                        put("sizeBytes", t.sizeBytes)
                    })
                }
                val playlistsArr = JSONArray()
                snapshot.playlists.forEach { p ->
                    playlistsArr.put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("songIds", JSONArray(p.songIds))
                    })
                }
                val body = JSONObject().apply {
                    put("userId", userId)
                    put("tracks", tracksArr)
                    put("playlists", playlistsArr)
                    put("favoriteIds", JSONArray(snapshot.favoriteIds.toList()))
                    put("languageMode", snapshot.languageMode)
                    put("themeName", snapshot.themeName)
                }.toString()
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
                conn.responseCode
                conn.disconnect()
            }
        }
    }

    private suspend fun pullCloudStateOnce(context: Context, user: CloudUser) {
        if (!hasVerifiedCloudSession(context)) return
        runCatching {
            val url = URL(
                getWebBaseUrl(context) + "/api/sync/state?userId=" +
                    URLEncoder.encode(user.userId, "UTF-8")
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 7000
                setRequestProperty("Authorization", "Bearer " + user.sessionToken)
                setRequestProperty("X-User-Id", user.userId)
            }
            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                if (responseCode == 401 || responseCode == 403) {
                    _syncStatus.value = "Neon Cloud session expired. Sign in again."
                }
                conn.disconnect()
                return@runCatching
            }
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = JSONObject(responseText)
            if (json.optString("userId") != user.userId) return@runCatching

            val remoteTracks = json.optJSONArray("tracks") ?: JSONArray()
            val remotePlaylists = json.optJSONArray("playlists") ?: JSONArray()
            val remoteFavorites = json.optJSONArray("favoriteIds") ?: JSONArray()
            val remoteSettings = json.optJSONObject("settings") ?: JSONObject()
            val signature = JSONObject().apply {
                put("tracks", remoteTracks)
                put("playlists", remotePlaylists)
                put("favoriteIds", remoteFavorites)
                put("settings", remoteSettings)
            }.toString()

            if (signature != lastRemoteVaultSignature[user.userId]) {
                val localSnapshot = loadUserIsolatedSnapshot(context, user.userId)
                if (remoteTracks.length() > 0 || remotePlaylists.length() > 0) {
                    val tracks = mutableListOf<AudioItem>()
                    for (i in 0 until remoteTracks.length()) {
                        val item = remoteTracks.optJSONObject(i) ?: continue
                        val id = item.optString("id")
                        if (id.isBlank()) continue
                        tracks.add(
                            AudioItem(
                                id = id,
                                title = item.optString("title"),
                                artist = item.optString("artist"),
                                album = item.optString("album"),
                                durationMs = item.optLong("durationMs", item.optLong("duration", 0L)),
                                uri = Uri.parse(item.optString("uri", "")),
                                sizeBytes = item.optLong("sizeBytes", 0L)
                            )
                        )
                    }

                    val playlists = mutableListOf<Playlist>()
                    for (i in 0 until remotePlaylists.length()) {
                        val item = remotePlaylists.optJSONObject(i) ?: continue
                        val id = item.optString("id")
                        if (id.isBlank()) continue
                        val songs = item.optJSONArray("songIds") ?: JSONArray()
                        val songIds = mutableListOf<String>()
                        for (j in 0 until songs.length()) songIds.add(songs.optString(j))
                        playlists.add(
                            Playlist(
                                id = id,
                                name = item.optString("name"),
                                songIds = songIds
                            )
                        )
                    }
                    val favorites = mutableSetOf<String>()
                    for (i in 0 until remoteFavorites.length()) {
                        val id = remoteFavorites.optString(i)
                        if (id.isNotBlank()) favorites.add(id)
                    }
                    val language = remoteSettings.optString("languageMode", "auto")
                    val theme = remoteSettings.optString(
                        "themeName",
                        remoteSettings.optString("theme", "DARK")
                    )
                    saveUserIsolatedData(
                        context = context,
                        tracks = tracks,
                        playlists = playlists,
                        favoriteIds = favorites,
                        languageMode = language,
                        themeName = theme,
                        forUserId = user.userId,
                        syncToCloud = false
                    )
                    lastRemoteVaultSignature[user.userId] = signature
                    val snapshot = loadUserIsolatedSnapshot(context, user.userId)
                    withContext(Dispatchers.Main) {
                        if (_currentUser.value?.userId == user.userId) {
                            onUserVaultLoaded?.invoke(snapshot)
                        }
                    }
                } else if (localSnapshot.tracks.isNotEmpty() || localSnapshot.playlists.isNotEmpty()) {
                    // Migrate local data only when Neon has no library data for this user.
                    lastRemoteVaultSignature[user.userId] = signature
                    pushStateToCloudAsync(context, user.userId)
                } else {
                    if (json.optBoolean("settingsPersisted", false)) {
                        saveUserIsolatedData(
                            context = context,
                            languageMode = remoteSettings.optString("languageMode", "auto"),
                            themeName = remoteSettings.optString("themeName", remoteSettings.optString("theme", "DARK")),
                            forUserId = user.userId,
                            syncToCloud = false
                        )
                    }
                    lastRemoteVaultSignature[user.userId] = signature
                }
            }

            val remotePlayback = json.optJSONObject("playbackState")
            if (remotePlayback != null) {
                val queueArray = remotePlayback.optJSONArray("queueIds") ?: JSONArray()
                val queueIds = mutableListOf<String>()
                for (i in 0 until queueArray.length()) queueIds.add(queueArray.optString(i))
                val remoteState = CloudPlaybackState(
                    userId = remotePlayback.optString("userId", user.userId),
                    activeDeviceId = remotePlayback.optString("activeDeviceId"),
                    activeDevicePlatform = remotePlayback.optString("activeDevicePlatform", "web"),
                    isPlaying = remotePlayback.optBoolean("isPlaying", false),
                    currentTrackId = remotePlayback.optString("currentTrackId"),
                    currentTrackTitle = remotePlayback.optString("currentTrackTitle"),
                    currentTrackArtist = remotePlayback.optString("currentTrackArtist"),
                    currentTrackUri = remotePlayback.optString("currentTrackUri"),
                    positionMs = remotePlayback.optLong("positionMs", 0L),
                    playbackMode = remotePlayback.optString("playbackMode", "NORMAL"),
                    queueIds = queueIds,
                    updatedAt = remotePlayback.optLong("updatedAt", 0L)
                )
                val localUpdatedAt = _playbackState.value?.updatedAt ?: 0L
                if (remoteState.updatedAt > localUpdatedAt) {
                    withContext(Dispatchers.Main) {
                        onRemotePlaybackStateReceived(context, remoteState)
                    }
                }
            }
        }.onFailure {
            _syncStatus.value = "Neon Cloud sync failed. Your local library is still available."
        }
    }

    private fun issueSessionToken(userId: String, email: String, provider: String): String {
        val raw = "$userId|$email|$provider|${System.currentTimeMillis()}"
        return "neon_sess_" + sha256(raw).take(32)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
