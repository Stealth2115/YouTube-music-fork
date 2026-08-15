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
    private const val YT_BASE = "https://www.youtube.com/youtubei/v1/"

    /** Public client key embedded in the music.youtube.com web page (not a Data API key). */
    private const val WEB_REMIX_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"

    private const val WEB_REMIX_NAME = "WEB_REMIX"
    private const val WEB_REMIX_VERSION = "1.20241023.01.00"
    private const val WEB_REMIX_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/129.0.0.0 Safari/537.36"

    private const val ANDROID_VR_NAME = "ANDROID_VR"
    private const val ANDROID_VR_VERSION = "1.60.19"
    private const val ANDROID_VR_UA =
        "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12; en_US; " +
            "Quest 3 Build/SQ3A.220605.009.A1) gzip"

    private const val IOS_NAME = "IOS"
    private const val IOS_VERSION = "20.03.02"
    private const val IOS_UA = "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X; en_US)"

    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MAX_RESPONSE_BYTES = 6 * 1024 * 1024

    val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }

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

    private fun androidVrContext(): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", ANDROID_VR_NAME)
            put("clientVersion", ANDROID_VR_VERSION)
            put("deviceMake", "Oculus")
            put("deviceModel", "Quest 3")
            put("osName", "Android")
            put("osVersion", "12")
            put("androidSdkVersion", 32)
            put("hl", "en")
            put("gl", "US")
            put("userAgent", ANDROID_VR_UA)
        }
    }

    private fun iosContext(): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", IOS_NAME)
            put("clientVersion", IOS_VERSION)
            put("deviceMake", "Apple")
            put("deviceModel", "iPhone16,2")
            put("osName", "iPhone")
            put("osVersion", "18.2.1.22C161")
            put("hl", "en")
            put("gl", "US")
            put("userAgent", IOS_UA)
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
        return post(url, body, WEB_REMIX_NAME, WEB_REMIX_VERSION, WEB_REMIX_UA, music = true)
    }

    /** music.youtube.com browse (albums, artists, playlists). */
    fun browse(browseId: String): JsonObject? {
        val body = buildJsonObject {
            put("context", musicContext())
            put("browseId", browseId)
        }
        val url = MUSIC_BASE + "browse?key=" + WEB_REMIX_KEY + "&prettyPrint=false"
        return post(url, body, WEB_REMIX_NAME, WEB_REMIX_VERSION, WEB_REMIX_UA, music = true)
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
        return post(url, body, WEB_REMIX_NAME, WEB_REMIX_VERSION, WEB_REMIX_UA, music = true, authToken = authToken)
    }

    /**
     * Player endpoint. The ANDROID_VR client returns plain, un-ciphered progressive
     * stream URLs; IOS is used as a fallback when a track is not offered to it.
     */
    fun player(videoId: String, useFallbackClient: Boolean): JsonObject? {
        val ctx = if (useFallbackClient) iosContext() else androidVrContext()
        val name = if (useFallbackClient) IOS_NAME else ANDROID_VR_NAME
        val version = if (useFallbackClient) IOS_VERSION else ANDROID_VR_VERSION
        val ua = if (useFallbackClient) IOS_UA else ANDROID_VR_UA
        val body = buildJsonObject {
            put("context", ctx)
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }
        return post(YT_BASE + "player?prettyPrint=false", body, name, version, ua, music = false)
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
                if (authToken != null) {
                    setRequestProperty("Authorization", "Bearer $authToken")
                    setRequestProperty("X-Goog-AuthUser", "0")
                }
                if (music) {
                    setRequestProperty("Origin", "https://music.youtube.com")
                    setRequestProperty("Referer", "https://music.youtube.com/")
                }
            }
            conn.outputStream.use { it.write(payload) }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.errorStream?.use { it.readBounded() }
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
