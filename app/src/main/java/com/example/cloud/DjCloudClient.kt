package com.example.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DjCloudUser(val id: String, val email: String, val name: String)

data class DjCloudDevice(
    val id: String,
    val name: String,
    val platform: String,
    val online: Boolean,
    val lastSeen: String
)

data class DjCloudPlayback(
    val activeDeviceId: String?,
    val currentTrack: JSONObject?,
    val queue: JSONArray,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val playbackMode: String
)

data class DjCloudClaim(
    val status: String,
    val player: DjCloudPlayback?,
    val commandId: String?
)

/**
 * Native Android client for the same Neon Auth identity and DJ Cloud API used by the web player.
 * Neon session cookies are encrypted locally with an AES/GCM key held by Android Keystore.
 */
class DjCloudClient(context: Context) {
    private val appContext = context.applicationContext
    private val secureStore = CloudSecureStore(appContext)
    private val cookieJar = PersistentNeonCookieJar(secureStore)
    private val http = OkHttpClient.Builder().cookieJar(cookieJar).build()

    private var cachedJwt: String? = null
    private var jwtExpiresAtMs: Long = 0L

    val deviceId: String by lazy {
        val prefs = appContext.getSharedPreferences("dj_cloud_device", Context.MODE_PRIVATE)
        prefs.getString("device_id", null)
            ?.takeIf { runCatching { UUID.fromString(it); true }.getOrDefault(false) }
            ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).apply() }
    }

    private val authBase = "https://ep-morning-bird-bad5xn0x.neonauth.c-8.eu-central-1.aws.neon.tech/neondb/auth"
    private val apiBase = "https://dj-web-topaz.vercel.app/api/cloud"

    suspend fun signIn(email: String, password: String): DjCloudUser = withContext(Dispatchers.IO) {
        val response = authRequest(
            "sign-in/email",
            "POST",
            JSONObject().put("email", email.trim()).put("password", password).put("rememberMe", true)
        )
        val user = userFrom(response) ?: fetchSessionUser()
            ?: throw IOException("Neon Auth signed in but did not return the account profile.")
        accessToken()
        user
    }

    suspend fun signUp(name: String, email: String, password: String): DjCloudUser = withContext(Dispatchers.IO) {
        val response = authRequest(
            "sign-up/email",
            "POST",
            JSONObject()
                .put("name", name.trim().ifBlank { email.substringBefore("@") })
                .put("email", email.trim())
                .put("password", password)
        )
        val user = userFrom(response) ?: fetchSessionUser()
            ?: throw IOException("Neon Auth created the account but did not return the account profile.")
        accessToken()
        user
    }

    suspend fun currentUser(): DjCloudUser? = withContext(Dispatchers.IO) {
        runCatching { fetchSessionUser() }.getOrNull()
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        runCatching { authRequest("sign-out", "POST", JSONObject()) }
        secureStore.clear()
        cachedJwt = null
        jwtExpiresAtMs = 0L
    }

    suspend fun registerDevice(): JSONObject = withContext(Dispatchers.IO) {
        apiRequest(
            "devices/register",
            "POST",
            JSONObject()
                .put("device_id", deviceId)
                .put("name", android.os.Build.MODEL?.take(80) ?: "Android device")
                .put("platform", "android")
        )
    }

    suspend fun listDevices(): List<DjCloudDevice> = withContext(Dispatchers.IO) {
        val rows = apiRequest("devices", "GET").optJSONArray("data") ?: JSONArray()
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                add(
                    DjCloudDevice(
                        id = row.optString("device_id"),
                        name = row.optString("name", "DJ Device"),
                        platform = row.optString("platform", "web"),
                        online = row.optBoolean("online", false),
                        lastSeen = row.optString("last_seen_at", "")
                    )
                )
            }
        }
    }

    suspend fun playbackState(): DjCloudPlayback? = withContext(Dispatchers.IO) {
        parsePlayback(apiRequest("state", "GET").optJSONObject("data"))
    }

    suspend fun claimPlayback(): DjCloudClaim = withContext(Dispatchers.IO) {
        val data = apiRequest("claim", "POST", JSONObject().put("device_id", deviceId))
            .optJSONObject("data") ?: throw IOException("DJ Cloud did not return a playback claim.")
        DjCloudClaim(
            status = data.optString("status", "unknown"),
            player = parsePlayback(data.optJSONObject("player")),
            commandId = data.optString("command_id").takeIf { it.isNotBlank() }
        )
    }

    suspend fun heartbeat() = withContext(Dispatchers.IO) {
        apiRequest("devices/heartbeat", "POST", JSONObject().put("device_id", deviceId))
    }

    suspend fun pendingCommands(): List<JSONObject> = withContext(Dispatchers.IO) {
        val rows = apiRequest("commands?device_id=$deviceId", "GET").optJSONArray("data") ?: JSONArray()
        buildList {
            for (index in 0 until rows.length()) rows.optJSONObject(index)?.let(::add)
        }
    }

    suspend fun acknowledgePause(commandId: String, positionMs: Long) = withContext(Dispatchers.IO) {
        apiRequest(
            "commands/ack",
            "POST",
            JSONObject()
                .put("device_id", deviceId)
                .put("command_id", commandId)
                .put("position_ms", positionMs.coerceAtLeast(0L))
        )
    }

    suspend fun publishPlayback(snapshot: JSONObject) = withContext(Dispatchers.IO) {
        snapshot.put("device_id", deviceId)
        apiRequest("state", "PUT", snapshot)
    }

    private fun fetchSessionUser(): DjCloudUser? =
        userFrom(authRequest("get-session", "GET"))

    private fun userFrom(response: JSONObject): DjCloudUser? {
        val data = response.optJSONObject("data") ?: response
        val user = data.optJSONObject("user") ?: response.optJSONObject("user") ?: return null
        val id = user.optString("id")
        if (id.isBlank()) return null
        return DjCloudUser(id, user.optString("email"), user.optString("name", user.optString("email")))
    }

    private suspend fun accessToken(): String {
        val now = System.currentTimeMillis()
        cachedJwt?.let { token ->
            if (jwtExpiresAtMs > now + 60_000L) return token
        }
        val result = authRequest("token", "GET")
        val data = result.optJSONObject("data") ?: result
        val token = data.optString("token").takeIf { it.count { char -> char == '.' } == 2 }
            ?: throw IOException("Neon Auth did not return a valid access token.")
        cachedJwt = token
        jwtExpiresAtMs = readJwtExpiry(token) ?: (now + 5 * 60_000L)
        return token
    }

    private fun authRequest(path: String, method: String, body: JSONObject? = null): JSONObject {
        val builder = Request.Builder()
            .url("$authBase/$path")
            .header("Accept", "application/json")
            .header("User-Agent", "DJ-Android")
        if (body != null) {
            builder.method(method, body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")
        } else {
            builder.method(method, null)
        }
        return executeJson(builder.build(), "Neon Auth")
    }

    private suspend fun apiRequest(path: String, method: String, body: JSONObject? = null): JSONObject {
        val token = accessToken()
        val builder = Request.Builder()
            .url("$apiBase/$path")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "DJ-Android")
        if (body != null) {
            builder.method(method, body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")
        } else {
            builder.method(method, null)
        }
        return executeJson(builder.build(), "DJ Cloud")
    }

    private fun executeJson(request: Request, serviceName: String): JSONObject {
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val payload = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (!response.isSuccessful || !payload.optBoolean("ok", true)) {
                val detail = payload.optString("message")
                    .ifBlank { payload.optString("error") }
                    .ifBlank { payload.optJSONObject("error")?.optString("message").orEmpty() }
                throw IOException(detail.ifBlank { "$serviceName request failed (HTTP ${response.code})." })
            }
            return payload
        }
    }

    private fun parsePlayback(row: JSONObject?): DjCloudPlayback? {
        row ?: return null
        return DjCloudPlayback(
            activeDeviceId = row.optString("active_device_id").takeIf { it.isNotBlank() && it != "null" },
            currentTrack = row.optJSONObject("current_track"),
            queue = row.optJSONArray("queue") ?: JSONArray(),
            isPlaying = row.optBoolean("is_playing", false),
            positionMs = row.optString("position_ms").toLongOrNull() ?: row.optLong("position_ms"),
            durationMs = row.optString("duration_ms").toLongOrNull() ?: row.optLong("duration_ms"),
            playbackMode = row.optString("playback_mode", "NORMAL")
        )
    }

    private fun readJwtExpiry(token: String): Long? = runCatching {
        val payload = token.split(".")[1]
        val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        JSONObject(String(decoded, Charsets.UTF_8)).optLong("exp")
            .takeIf { it > 0L }?.times(1000L)
    }.getOrNull()

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private class PersistentNeonCookieJar(private val store: CloudSecureStore) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val all = loadCookies().filter { it.expiresAt > System.currentTimeMillis() }.toMutableList()
        cookies.forEach { fresh ->
            all.removeAll { it.name == fresh.name && it.domain == fresh.domain && it.path == fresh.path }
            all.add(fresh)
        }
        val array = JSONArray()
        all.forEach { cookie ->
            array.put(
                JSONObject()
                    .put("name", cookie.name)
                    .put("value", cookie.value)
                    .put("domain", cookie.domain)
                    .put("path", cookie.path)
                    .put("expiresAt", cookie.expiresAt)
                    .put("secure", cookie.secure)
                    .put("httpOnly", cookie.httpOnly)
                    .put("hostOnly", cookie.hostOnly)
            )
        }
        store.put("neon_auth_cookies", array.toString())
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        loadCookies().filter { it.matches(url) && it.expiresAt > System.currentTimeMillis() }

    private fun loadCookies(): List<Cookie> {
        val raw = store.get("neon_auth_cookies") ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val cookie = runCatching {
                    val builder = Cookie.Builder()
                        .name(item.getString("name"))
                        .value(item.getString("value"))
                        .path(item.optString("path", "/"))
                        .expiresAt(item.optLong("expiresAt", System.currentTimeMillis() + 86_400_000L))
                    val domain = item.getString("domain")
                    if (item.optBoolean("hostOnly", false)) builder.hostOnlyDomain(domain) else builder.domain(domain)
                    if (item.optBoolean("secure", false)) builder.secure()
                    if (item.optBoolean("httpOnly", false)) builder.httpOnly()
                    builder.build()
                }.getOrNull()
                if (cookie != null) add(cookie)
            }
        }
    }
}

/** Encrypted preferences backed by an AES/GCM key stored in Android Keystore. */
private class CloudSecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("dj_cloud_secure", Context.MODE_PRIVATE)

    fun get(name: String): String? {
        val encrypted = prefs.getString(name, null) ?: return null
        return runCatching { decrypt(encrypted) }.getOrElse {
            prefs.edit().remove(name).apply()
            null
        }
    }

    fun put(name: String, value: String) {
        prefs.edit().putString(name, encrypt(value)).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        return "$iv.$data"
    }

    private fun decrypt(value: String): String {
        val parts = value.split(".", limit = 2)
        require(parts.size == 2) { "Encrypted session value is invalid." }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val data = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    companion object {
        private const val KEY_ALIAS = "dj_cloud_session_v1"
    }
}
