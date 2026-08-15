package com.agon.app.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {

    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 5000"
        runCatching {
            context.contentResolver.query(
                uri, projection, selection, null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { c ->
                val idC = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    val id = c.getLong(idC)
                    val albumId = c.getLong(albumIdC)
                    val rawArtist = c.getString(artistC) ?: "<unknown>"
                    out.add(
                        Song(
                            id = id,
                            title = c.getString(titleC) ?: "Unknown",
                            artist = if (rawArtist == "<unknown>") "Unknown Artist" else rawArtist,
                            album = c.getString(albumC) ?: "Unknown Album",
                            albumId = albumId,
                            durationMs = c.getLong(durC),
                            uri = ContentUris.withAppendedId(uri, id).toString(),
                            artUri = "content://media/external/audio/albumart/$albumId",
                            data = c.getString(dataC),
                            dateAdded = c.getLong(dateC),
                        )
                    )
                }
            }
        }
        out
    }
}
