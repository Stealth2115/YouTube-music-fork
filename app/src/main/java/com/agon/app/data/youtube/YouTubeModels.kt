package com.agon.app.data.youtube

import androidx.compose.runtime.Immutable
import com.agon.app.data.Song

/** Scheme used for queue items whose real stream URL is resolved lazily at load time. */
const val YT_SCHEME = "ytstream"

fun ytStreamUri(videoId: String): String = "$YT_SCHEME://$videoId"

fun videoIdOf(song: Song): String? =
    if (song.uri.startsWith("$YT_SCHEME://")) song.uri.removePrefix("$YT_SCHEME://").ifBlank { null } else null

/**
 * Stable, collision-resistant negative id for a YouTube track so it can live in the
 * same [Song] model as MediaStore items (whose ids are always positive).
 */
fun youtubeSongId(videoId: String): Long {
    var hash = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis
    for (element in videoId) {
        hash = hash xor element.code.toLong()
        hash *= 0x100000001b3L
    }
    return if (hash == Long.MIN_VALUE) -1L else -(if (hash < 0) -hash else hash)
}

@Immutable
data class YtTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val thumbnailUrl: String,
) {
    fun toSong(): Song = Song(
        id = youtubeSongId(videoId),
        title = title,
        artist = artist,
        album = album,
        albumId = -1L,
        durationMs = durationMs,
        uri = ytStreamUri(videoId),
        artUri = thumbnailUrl,
        data = null,
        dateAdded = 0L,
    )
}

@Immutable
data class YtAlbum(
    val browseId: String,
    val title: String,
    val artist: String,
    val year: String,
    val thumbnailUrl: String,
)

@Immutable
data class YtArtist(
    val browseId: String,
    val name: String,
    val subtitle: String,
    val thumbnailUrl: String,
)

@Immutable
data class YtSearchResults(
    val tracks: List<YtTrack> = emptyList(),
    val albums: List<YtAlbum> = emptyList(),
    val artists: List<YtArtist> = emptyList(),
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty()

    companion object {
        val EMPTY = YtSearchResults()
    }
}

/** Result of resolving a playable audio stream. */
sealed interface StreamResult {
    data class Success(val url: String, val expiresAtMs: Long) : StreamResult
    data class Unavailable(val reason: String) : StreamResult
}
