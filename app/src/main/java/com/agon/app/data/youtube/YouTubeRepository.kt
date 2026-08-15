package com.agon.app.data.youtube

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Turns raw InnerTube responses into the app's models.
 *
 * Everything here is anonymous: no API key, no login, no cookies and no credential of
 * any kind is sent or stored. Results are kept only in a small bounded in-memory cache.
 */
class YouTubeRepository {

    private val searchCache = LruCache<String, YtSearchResults>(24)
    private val albumCache = LruCache<String, List<YtTrack>>(16)
    private val streamCache = LruCache<String, StreamResult.Success>(48)

    /** Grace period so a stream URL is never handed out right before it expires. */
    private val expiryGuardMs = 60_000L

    suspend fun search(query: String): YtSearchResults = withContext(Dispatchers.IO) {
        val key = query.trim().lowercase()
        if (key.isEmpty()) return@withContext YtSearchResults.EMPTY
        searchCache.get(key)?.let { return@withContext it }

        val songs = InnerTube.search(key, InnerTube.SearchFilter.SONGS)?.let { parseTracks(it, 25) }.orEmpty()
        val albums = InnerTube.search(key, InnerTube.SearchFilter.ALBUMS)?.let { parseAlbums(it, 12) }.orEmpty()
        val artists = InnerTube.search(key, InnerTube.SearchFilter.ARTISTS)?.let { parseArtists(it, 12) }.orEmpty()

        val results = YtSearchResults(songs, albums, artists)
        if (!results.isEmpty) searchCache.put(key, results)
        results
    }

    /** Tracks of an album / playlist browse id. */
    suspend fun albumTracks(browseId: String, fallbackArtwork: String): List<YtTrack> =
        withContext(Dispatchers.IO) {
            albumCache.get(browseId)?.let { return@withContext it }
            val response = InnerTube.browse(browseId) ?: return@withContext emptyList()
            val tracks = parseTracks(response, 60).map {
                if (it.thumbnailUrl.isEmpty()) it.copy(thumbnailUrl = fallbackArtwork) else it
            }
            if (tracks.isNotEmpty()) albumCache.put(browseId, tracks)
            tracks
        }

    /** Top tracks of an artist channel. */
    suspend fun artistTracks(browseId: String, fallbackArtwork: String): List<YtTrack> =
        albumTracks(browseId, fallbackArtwork)

    /** Liked songs of the signed-in account. */
    suspend fun likedSongs(authToken: String, limit: Int = 200): List<YtTrack> =
        withContext(Dispatchers.IO) {
            val response = InnerTube.browseAuthed("LM", authToken) ?: return@withContext emptyList()
            parseTracks(response, limit)
        }

    /** Playlists saved in the signed-in account's library. */
    suspend fun libraryPlaylists(authToken: String, limit: Int = 100): List<YtPlaylist> =
        withContext(Dispatchers.IO) {
            val response = InnerTube.browseAuthed("FEmusic_liked_playlists", authToken)
                ?: return@withContext emptyList()
            parsePlaylists(response, limit)
        }

    /** Tracks of a saved library playlist. */
    suspend fun libraryPlaylistTracks(
        authToken: String,
        browseId: String,
        fallbackArtwork: String,
        limit: Int = 200,
    ): List<YtTrack> = withContext(Dispatchers.IO) {
        val response = InnerTube.browseAuthed(browseId, authToken) ?: return@withContext emptyList()
        parseTracks(response, limit).map {
            if (it.thumbnailUrl.isEmpty()) it.copy(thumbnailUrl = fallbackArtwork) else it
        }
    }

    /**
     * Resolves a playable, progressive audio stream URL for [videoId].
     * Blocking network call — always invoke from a background thread.
     */
    fun resolveStreamBlocking(videoId: String): StreamResult {
        val now = System.currentTimeMillis()
        streamCache.get(videoId)?.let { cached ->
            if (cached.expiresAtMs - expiryGuardMs > now) return cached
            streamCache.remove(videoId)
        }

        var lastReason = "This track can't be played"
        var gotAnyResponse = false
        for (response in InnerTube.playerResponses(videoId)) {
            gotAnyResponse = true
            val status = response.obj("playabilityStatus")
            val state = status.str("status")
            if (state != null && state != "OK") {
                lastReason = playabilityMessage(state, status)
                continue
            }
            val url = pickAudioUrl(response) ?: continue
            val success = StreamResult.Success(url, expiryOf(url, now))
            streamCache.put(videoId, success)
            return success
        }
        if (!gotAnyResponse) lastReason = "Couldn't reach YouTube"
        return StreamResult.Unavailable(lastReason)
    }

    fun invalidateStream(videoId: String) {
        streamCache.remove(videoId)
    }

    // ---------------- Parsing ----------------

    private fun playabilityMessage(state: String, status: JsonObject?): String = when (state) {
        "LOGIN_REQUIRED", "AGE_VERIFICATION_REQUIRED" -> "Age-restricted track — not playable"
        "UNPLAYABLE" -> status.obj("errorScreen")
            .obj("playerErrorMessageRenderer").obj("subreason").runsText()
            ?: status.str("reason")
            ?: "This track isn't available"
        "CONTENT_CHECK_REQUIRED" -> "This track requires confirmation — not playable"
        "ERROR" -> status.str("reason") ?: "This track isn't available"
        else -> status.str("reason") ?: "This track isn't available in your region"
    }

    private fun pickAudioUrl(response: JsonObject): String? {
        val streaming = response.obj("streamingData") ?: return null
        var best: JsonObject? = null
        var bestScore = Long.MIN_VALUE
        for (key in arrayOf("adaptiveFormats", "formats")) {
            val formats = streaming.arr(key) ?: continue
            for (element in formats) {
                val format = element as? JsonObject ?: continue
                if (format.str("url").isNullOrEmpty()) continue // ciphered — skip
                val mime = format.str("mimeType").orEmpty()
                if (!mime.startsWith("audio/")) continue
                val bitrate = format.num("bitrate") ?: format.num("averageBitrate") ?: 0L
                // Prefer AAC/m4a: broadest ExoPlayer + audio-effect compatibility.
                val score = bitrate + if (mime.contains("mp4a")) 1_000_000L else 0L
                if (score > bestScore) {
                    bestScore = score
                    best = format
                }
            }
            if (best != null) break
        }
        return best.str("url")
    }

    private fun expiryOf(url: String, now: Long): Long {
        val marker = "expire="
        val start = url.indexOf(marker)
        if (start < 0) return now + 30 * 60_000L
        val from = start + marker.length
        var end = from
        while (end < url.length && url[end].isDigit()) end++
        val seconds = url.substring(from, end).toLongOrNull() ?: return now + 30 * 60_000L
        return seconds * 1000L
    }

    private fun parseTracks(response: JsonObject, limit: Int): List<YtTrack> {
        val items = response.collectRenderers("musicResponsiveListItemRenderer", limit * 2)
        val out = ArrayList<YtTrack>(minOf(items.size, limit))
        val seen = HashSet<String>(items.size)
        for (item in items) {
            if (out.size >= limit) break
            val videoId = item.obj("playlistItemData").str("videoId")
                ?: item.watchEndpointVideoId()
                ?: continue
            if (!seen.add(videoId)) continue
            val columns = item.arr("flexColumns").orEmpty()
            val title = columns.columnText(0) ?: continue
            val subtitleRuns = columns.columnRuns(1)
            val duration = subtitleRuns.lastDurationMs()
                ?: item.durationFromFixedColumns()
                ?: 0L
            val meta = subtitleRuns.filter { !it.isSeparator() && !it.isDurationText() }
            val artist = meta.firstOrNull { it != "Song" && it != "Video" }?.trim()?.ifBlank { null }
                ?: "Unknown Artist"
            val album = meta.lastOrNull()?.trim().orEmpty()
            out.add(
                YtTrack(
                    videoId = videoId,
                    title = title,
                    artist = artist,
                    album = if (album.isBlank() || album == artist) "YouTube Music" else album,
                    durationMs = duration,
                    thumbnailUrl = item.bestThumbnail(),
                )
            )
        }
        return out
    }

    private fun parseAlbums(response: JsonObject, limit: Int): List<YtAlbum> {
        val items = response.collectRenderers("musicResponsiveListItemRenderer", limit * 2)
        val out = ArrayList<YtAlbum>(minOf(items.size, limit))
        val seen = HashSet<String>(items.size)
        for (item in items) {
            if (out.size >= limit) break
            val browseId = item.browseId()?.takeIf { it.startsWith("MPRE") || it.startsWith("VL") } ?: continue
            if (!seen.add(browseId)) continue
            val columns = item.arr("flexColumns").orEmpty()
            val title = columns.columnText(0) ?: continue
            val meta = columns.columnRuns(1).filter { !it.isSeparator() }
            out.add(
                YtAlbum(
                    browseId = browseId,
                    title = title,
                    artist = meta.firstOrNull { it != "Album" && it != "Single" && it != "EP" }?.trim().orEmpty()
                        .ifBlank { "Unknown Artist" },
                    year = meta.lastOrNull { it.trim().toIntOrNull() != null }?.trim().orEmpty(),
                    thumbnailUrl = item.bestThumbnail(),
                )
            )
        }
        return out
    }

    private fun parseArtists(response: JsonObject, limit: Int): List<YtArtist> {
        val items = response.collectRenderers("musicResponsiveListItemRenderer", limit * 2)
        val out = ArrayList<YtArtist>(minOf(items.size, limit))
        val seen = HashSet<String>(items.size)
        for (item in items) {
            if (out.size >= limit) break
            val browseId = item.browseId()?.takeIf { it.startsWith("UC") } ?: continue
            if (!seen.add(browseId)) continue
            val columns = item.arr("flexColumns").orEmpty()
            val name = columns.columnText(0) ?: continue
            val meta = columns.columnRuns(1).filter { !it.isSeparator() }
            out.add(
                YtArtist(
                    browseId = browseId,
                    name = name,
                    subtitle = meta.lastOrNull { it.contains("subscriber", true) }?.trim().orEmpty()
                        .ifBlank { "Artist" },
                    thumbnailUrl = item.bestThumbnail(),
                )
            )
        }
        return out
    }

    private fun parsePlaylists(response: JsonObject, limit: Int): List<YtPlaylist> {
        val items = response.collectRenderers("musicResponsiveListItemRenderer", limit * 2)
        val out = ArrayList<YtPlaylist>(minOf(items.size, limit))
        val seen = HashSet<String>(items.size)
        for (item in items) {
            if (out.size >= limit) break
            val browseId = item.browseId()?.takeIf { it.startsWith("VL") || it.startsWith("RD") } ?: continue
            if (!seen.add(browseId)) continue
            val columns = item.arr("flexColumns").orEmpty()
            val title = columns.columnText(0) ?: continue
            val meta = columns.columnRuns(1).filter { !it.isSeparator() }
            val trackCount = meta.firstOrNull { it.contains("song", ignoreCase = true) }?.trim().orEmpty()
            out.add(YtPlaylist(browseId, title, trackCount, item.bestThumbnail()))
        }
        return out
    }
}

// ---------------- Renderer helpers ----------------

private fun List<kotlinx.serialization.json.JsonElement>.columnText(index: Int): String? =
    (getOrNull(index) as? JsonObject)
        .obj("musicResponsiveListItemFlexColumnRenderer")
        .obj("text")
        .runsText()
        ?.trim()
        ?.ifBlank { null }

private fun List<kotlinx.serialization.json.JsonElement>.columnRuns(index: Int): List<String> {
    val runs = (getOrNull(index) as? JsonObject)
        .obj("musicResponsiveListItemFlexColumnRenderer")
        .obj("text")
        .arr("runs") ?: return emptyList()
    return runs.mapNotNull { (it as? JsonObject).str("text") }
}

/** True for the punctuation-only runs InnerTube puts between metadata fields. */
private fun String.isSeparator(): Boolean {
    val trimmed = trim()
    if (trimmed.isEmpty()) return true
    return trimmed.all { it == '\u2022' || it == '|' || it == '-' || it == '\u2013' || it == ',' }
}

private fun String.isDurationText(): Boolean {
    if (length !in 4..8 || !contains(':')) return false
    return all { it.isDigit() || it == ':' }
}

private fun List<String>.lastDurationMs(): Long? =
    lastOrNull { it.isDurationText() }?.durationTextToMs()

private fun String.durationTextToMs(): Long? {
    val parts = split(':')
    if (parts.size !in 2..3) return null
    var total = 0L
    for (part in parts) {
        val value = part.toLongOrNull() ?: return null
        total = total * 60 + value
    }
    return total * 1000L
}

private fun JsonObject.durationFromFixedColumns(): Long? {
    val fixed = arr("fixedColumns") ?: return null
    for (element in fixed) {
        val text = (element as? JsonObject)
            .obj("musicResponsiveListItemFixedColumnRenderer")
            .obj("text")
            .runsText()
            ?.trim()
        if (text != null && text.isDurationText()) return text.durationTextToMs()
    }
    return null
}

private fun JsonObject.watchEndpointVideoId(): String? =
    collectRenderers("watchEndpoint", 1).firstOrNull().str("videoId")

private fun JsonObject.browseId(): String? =
    collectRenderers("browseEndpoint", 1).firstOrNull().str("browseId")

private fun JsonObject.bestThumbnail(): String {
    val thumbs = collectRenderers("thumbnail", 6)
        .firstNotNullOfOrNull { it.arr("thumbnails") }
        ?: return ""
    var best = ""
    var bestWidth = -1L
    for (element in thumbs) {
        val thumb = element as? JsonObject ?: continue
        val url = (thumb["url"] as? JsonPrimitive)?.content ?: continue
        val width = thumb.num("width") ?: 0L
        // Cap at ~544px: enough for full-screen artwork, keeps memory/bandwidth low.
        if (width in (bestWidth + 1)..544L || bestWidth < 0) {
            best = url
            bestWidth = width
        }
    }
    return best
}
