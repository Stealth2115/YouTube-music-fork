package com.agon.app.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.serialization.Serializable
import java.util.Locale

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

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    val artUri: String,
)

data class ArtistInfo(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
)

@Serializable
data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long> = emptyList(),
)

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
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatFreq(hz: Int): String {
    return if (hz >= 1000) {
        val k = hz / 1000f
        if (k >= 10f) "${k.toInt()}k" else String.format(Locale.US, "%.1fk", k).removeSuffix(".0k").let { if (it.endsWith("k")) it else it + "k" }
    } else "$hz"
}
