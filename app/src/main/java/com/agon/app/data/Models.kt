package com.agon.app.data

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.serialization.Serializable
import java.util.Locale

@Immutable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: String,
    val artUri: String,
    val data: String?,
    val dateAdded: Long,
)

@Immutable
data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    val artUri: String,
)

@Immutable
data class ArtistInfo(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
)

@Immutable
@Serializable
data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long> = emptyList(),
)

@Immutable
data class LyricLine(val timeMs: Long, val text: String)

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setUri(uri)
    .setMediaId(id.toString())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(Uri.parse(artUri))
            .build()
    )
    .build()

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    // Hand-rolled instead of String.format: this runs for every visible list row and
    // on every position tick, and the formatter is comparatively expensive.
    val sb = StringBuilder(8)
    if (h > 0) {
        sb.append(h).append(':')
        if (m < 10) sb.append('0')
    }
    sb.append(m).append(':')
    if (s < 10) sb.append('0')
    sb.append(s)
    return sb.toString()
}

fun formatFreq(hz: Int): String {
    return if (hz >= 1000) {
        val k = hz / 1000f
        if (k >= 10f) "${k.toInt()}k" else String.format(Locale.US, "%.1fk", k).removeSuffix(".0k").let { if (it.endsWith("k")) it else it + "k" }
    } else "$hz"
}
