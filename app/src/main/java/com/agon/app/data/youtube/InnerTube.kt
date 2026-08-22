package com.agon.app.data.youtube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.GZIPInputStream

/**
 * Minimal public InnerTube client.
 *
 * Uses only the anonymous, publicly embedded web/app clients that youtube.com and
 * music.youtube.com themselves ship to unauthenticated visitors: no YouTube Data API
 * key, no Google account, no cookies, no tokens and nothing is ever persisted.
 */
internal object InnerTube {

    private const val MUSIC_BASE = "https://music.youtube.com/youtubei/v1/"
    private const val WWW_BASE = "https://www.youtube.com/youtubei/v1/"

    /** Public client key embedded in the music.youtube.com web page (not a Data API key). */
    private const val WEB_REMIX_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"

    private const val WEB_REMIX_NAME = "WEB_REMIX"
    private const val WEB_REMIX_CLIENT_ID = "67"
    private const val WEB_REMIX_VERSION = "1.20260707.12.00"
    private const val WEB_REMIX_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/129.0.0.0 Safari/537.36"

    // Player client config mirrors the current (Aug 2026) official Metrolist/yt-dlp
    // values. visionOS is the ONLY client that returns plain, un-ciphered progressive
    // stream URLs without a proof-of-origin token or a JS signature runtime, which is
    // exactly the fallback path the reference apps use for anonymous, no-JS playback.
    // The other clients (ANDROID_VR, IOS, TV-embed) have been retired by YouTube for
    // anonymous playback, so they are no longer used.
    private const val VISIONOS_NAME = "VISIONOS"
    private const val VISIONOS_VERSION = "1.02"
    private const val VISIONOS_CLIENT_ID = "101"
    private const val VISIONOS_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/26.0 Safari/605.1.15"

    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MAX_RESPONSE_BYTES = 6 * 1024 * 1024

    val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile
    private var cachedVisitorData: String? = null

    @Volatile
    private var lastPlayerFailure: String? = null

    /**
     * Fetches a fresh anonymous visitorData token. Every InnerTube browse response returns
     * one in `responseContext.visitorData`; the player endpoint rejects stale/handmade
     * tokens, so the reference clients fetch this instead of hardcoding it.
     */
    fun visitorData(): String? {
        cachedVisitorData?.let { return it }
        synchronized(this) {
            cachedVisitorData?.let { return it }
            val fetched = runCatching {
                val body = buildJsonObject {
                    put("context", musicContext())
                    put("browseId", "FEmusic_home")
                }
                val url = MUSIC_BASE + "browse?key=" + WEB_REMIX_KEY + "&prettyPrint=false"
                post(url, body, WEB_REMIX_CLIENT_ID, WEB_REMIX_VERSION, WEB_REMIX_UA, music = true)
                    ?.obj("responseContext")?.str("visitorData")
            }.getOrNull()
            cachedVisitorData = fetched
            return fetched
        }
    }

    /** Human-readable reason the last player request failed, or null if it succeeded. */
    fun lastPlayerFailure(): String? = lastPlayerFailure

    /** Search filter params understood by music.youtube.com (stable, public values). */
    object SearchFilter {
        const val SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        const val ALBUMS = "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"
        const val ARTISTS = "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"
    }

    private fun musicContext(): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", WEB_REMIX_NAME)
            put("clientVersion", WEB_REMIX_VERSION)
            put("hl", "en")
            put("gl", "US")
            put("userAgent", WEB_REMIX_UA)
        }
        putJsonObject("user") { put("lockedSafetyMode", false) }
    }

    private fun visionOsContext(): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", VISIONOS_NAME)
            put("clientVersion", VISIONOS_VERSION)
            put("deviceMake", "Apple")
            put("deviceModel", "RealityDevice17,1")
            put("osName", "visionOS")
            put("osVersion", "26.5.23O471")
            put("hl", "en")
            put("gl", "US")
        }
    }

    /** music.youtube.com search. Returns the raw InnerTube response, or null on failure. */
    fun search(query: String, params: String?): JsonObject? {
        val body = buildJsonObject {
            put("context", musicContext())
            put("query", query)
            if (params != null) put("params", params)
        }
        val url = MUSIC_BASE + "search?key=" + WEB_REMIX_KEY + "&prettyPrint=false"
        return post(url, body, WEB_REMIX_CLIENT_ID, WEB_REMIX_VERSION, WEB_REMIX_UA, music = true)
    }

    /** music.youtube.com browse (albums, artists, playlists). */
    fun browse(browseId: String): JsonObject? {
        val body = buildJsonObject {
            put("context", musicContext())
            put("browseId", browseId)
        }
        val url = MUSIC_BASE + "browse?key=" + WEB_REMIX_KEY + "&prettyPrint=false"
        return post(url, body, WEB_REMIX_CLIENT_ID, WEB_REMIX_VERSION, WEB_REMIX_UA, music = true)
    }

    /**
     * Authenticated browse for the signed-in account's library (liked songs, saved
     * playlists, …). Uses the account's OAuth access token and omits the public web
     * client key, matching how the official web client sends personalised requests.
     */
    fun browseAuthed(browseId: String, authToken: String): JsonObject? {
        val body = buildJsonObject {
            put("context", musicContext())
            put("browseId", browseId)
        }
        val url = MUSIC_BASE + "browse?prettyPrint=false"
        return post(url, body, WEB_REMIX_CLIENT_ID, WEB_REMIX_VERSION, WEB_REMIX_UA, music = true, authToken = authToken)
    }

    private data class PlayerClient(
        val key: String,
        val clientId: String,
        val version: String,
        val userAgent: String,
        val baseUrl: String,
        val origin: String,
        val referer: String,
        val context: () -> JsonObject,
    )

    /**
     * Player clients tried in priority order. visionOS is the only anonymous client that
     * returns plain, un-ciphered progressive stream URLs without a proof-of-origin token
     * or a JS signature runtime (yt-dlp's `_DEFAULT_JSLESS_CLIENTS = ('visionos',)`).
     * Both API hosts are tried because yt-dlp targets www.youtube.com while the music
     * clients target music.youtube.com; one can be reachable when the other is blocked.
     */
    private val PLAYER_CLIENTS = listOf(
        PlayerClient(
            "visionos-music", VISIONOS_CLIENT_ID, VISIONOS_VERSION, VISIONOS_UA,
            MUSIC_BASE, "https://music.youtube.com", "https://music.youtube.com/",
            ::visionOsContext,
        ),
        PlayerClient(
            "visionos-web", VISIONOS_CLIENT_ID, VISIONOS_VERSION, VISIONOS_UA,
            WWW_BASE, "https://www.youtube.com", "https://www.youtube.com/",
            ::visionOsContext,
        ),
    )

    data class PlayerResponse(val key: String, val json: JsonObject)

    val playerClientCount: Int get() = PLAYER_CLIENTS.size

    /**
     * Player endpoint. Returns the raw InnerTube responses in priority order; the caller
     * walks the list and uses the first response with a playable audio URL.
     *
     * The player is always sent anonymously: visionOS is an anonymous client that rejects
     * OAuth bearer auth with HTTP 400. The account token is only used for library/browse
     * endpoints ([browseAuthed]), not for stream resolution.
     */
    fun playerResponses(videoId: String): List<PlayerResponse> {
        val out = ArrayList<PlayerResponse>(PLAYER_CLIENTS.size)
        for (client in PLAYER_CLIENTS) {
            val body = buildJsonObject {
                put("context", client.context())
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }
            postPlayer(client.baseUrl + "player?prettyPrint=false", body, client)
                ?.let { out.add(PlayerResponse(client.key, it)) }
        }
        return out
    }

    private fun post(
        url: String,
        body: JsonObject,
        clientName: String,
        clientVersion: String,
        userAgent: String,
        music: Boolean,
        authToken: String? = null,
    ): JsonObject? {
        var conn: HttpURLConnection? = null
        return try {
            val payload = body.toString().toByteArray(Charsets.UTF_8)
            conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                instanceFollowRedirects = true
                // Fixed length instead of chunked: InnerTube rejects chunked bodies
                // and it avoids buffering the request twice.
                setFixedLengthStreamingMode(payload.size)
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("X-Goog-Api-Format-Version", "1")
                setRequestProperty("X-YouTube-Client-Name", clientName)
                setRequestProperty("X-YouTube-Client-Version", clientVersion)
                setRequestProperty("X-Goog-Request-Time", System.currentTimeMillis().toString())
                if (authToken != null) {
                    setRequestProperty("Authorization", "Bearer $authToken")
                    setRequestProperty("X-Goog-AuthUser", "0")
                }
                if (music) {
                    setRequestProperty("X-Origin", "https://music.youtube.com")
                    setRequestProperty("Origin", "https://music.youtube.com")
                    setRequestProperty("Referer", "https://music.youtube.com/")
                }
            }
            conn.outputStream.use { it.write(payload) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = conn.errorStream?.use { it.readBounded() }?.take(300)
                android.util.Log.w(
                    "InnerTube",
                    "post ${if (authToken != null) "authed " else ""}${url.substringAfterLast('/').take(40)}: HTTP $code ${errBody.orEmpty()}"
                )
                return null
            }
            val text = conn.decodedStream().use { it.readBounded() }
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** POST used by [playerResponses]: per-client numeric name, user agent and YouTube origin. */
    private fun postPlayer(
        url: String,
        body: JsonObject,
        client: PlayerClient,
    ): JsonObject? {
        var conn: HttpURLConnection? = null
        return try {
            val payload = body.toString().toByteArray(Charsets.UTF_8)
            conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                instanceFollowRedirects = true
                setFixedLengthStreamingMode(payload.size)
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("User-Agent", client.userAgent)
                setRequestProperty("X-Goog-Api-Format-Version", "1")
                setRequestProperty("X-YouTube-Client-Name", client.clientId)
                setRequestProperty("X-YouTube-Client-Version", client.version)
                setRequestProperty("X-Origin", client.origin)
                setRequestProperty("Origin", client.origin)
                setRequestProperty("Referer", client.referer)
                // Fresh anonymous session token (fetched from a browse response); a stale or
                // handmade token makes the player endpoint reject the request outright.
                visitorData()?.let { setRequestProperty("X-Goog-Visitor-Id", it) }
            }
            conn.outputStream.use { it.write(payload) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = conn.errorStream?.use { it.readBounded() }?.take(300)
                lastPlayerFailure = "HTTP $code"
                android.util.Log.w("InnerTube", "player ${client.key}: HTTP $code ${errBody.orEmpty()}")
                return null
            }
            val text = conn.decodedStream().use { it.readBounded() }
            val parsed = json.parseToJsonElement(text) as? JsonObject
            if (parsed == null) {
                lastPlayerFailure = "bad response"
                android.util.Log.w("InnerTube", "player ${client.key}: non-object response (${text.take(200)})")
            } else {
                lastPlayerFailure = null
                android.util.Log.d(
                    "InnerTube",
                    "player ${client.key}: OK, hasStreamingData=${parsed.obj("streamingData") != null}"
                )
            }
            parsed
        } catch (e: IOException) {
            lastPlayerFailure = "network error"
            android.util.Log.w("InnerTube", "player ${client.key}: IOException ${e.javaClass.simpleName}: ${e.message}")
            null
        } catch (e: RuntimeException) {
            lastPlayerFailure = "bad response"
            android.util.Log.w("InnerTube", "player ${client.key}: ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun HttpURLConnection.decodedStream(): InputStream {
        val raw = inputStream
        return if (contentEncoding.equals("gzip", ignoreCase = true)) GZIPInputStream(raw) else raw
    }

    private fun InputStream.readBounded(): String {
        val out = StringBuilder(1 shl 16)
        val buffer = CharArray(8 * 1024)
        reader(Charsets.UTF_8).use { reader ->
            var total = 0
            while (true) {
                val read = reader.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_RESPONSE_BYTES) break
                out.appendRange(buffer, 0, read)
            }
        }
        return out.toString()
    }
}

// ---------------- Small JSON navigation helpers ----------------

internal fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject

internal fun JsonObject?.arr(key: String): JsonArray? = this?.get(key) as? JsonArray

internal fun JsonObject?.str(key: String): String? = (this?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject?.num(key: String): Long? = (this?.get(key) as? JsonPrimitive)?.content?.toLongOrNull()

/** Concatenated text of a `runs` based text node. */
internal fun JsonObject?.runsText(): String? {
    val runs = this.arr("runs") ?: return this.str("simpleText")
    if (runs.isEmpty()) return null
    val sb = StringBuilder()
    for (run in runs) sb.append((run as? JsonObject).str("text") ?: "")
    return sb.toString().ifBlank { null }
}

/**
 * Depth-limited recursive walk that collects every object stored under [key].
 * InnerTube layouts change often; collecting renderers by name keeps parsing resilient.
 */
internal fun JsonObject.collectRenderers(key: String, limit: Int): List<JsonObject> {
    val out = ArrayList<JsonObject>(minOf(limit, 32))
    walkForKey(this, key, limit, 0, out)
    return out
}

private fun walkForKey(node: JsonObject, key: String, limit: Int, depth: Int, out: MutableList<JsonObject>) {
    if (out.size >= limit || depth > 24) return
    for ((k, v) in node) {
        if (out.size >= limit) return
        when (v) {
            is JsonObject -> {
                if (k == key) out.add(v)
                // Always descend: renderers are frequently nested inside a node of the
                // same name (e.g. thumbnail -> musicThumbnailRenderer -> thumbnail).
                walkForKey(v, key, limit, depth + 1, out)
            }
            is JsonArray -> for (item in v) {
                if (item is JsonObject) walkForKey(item, key, limit, depth + 1, out)
                if (out.size >= limit) return
            }
            else -> Unit
        }
    }
}
