package com.agon.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "redline_prefs")

data class SavedState(
    val favorites: Set<Long>,
    val playlistsJson: String,
    val recents: List<Long>,
    val queueIds: List<Long>,
    val queueIndex: Int,
    val queuePos: Long,
    val shuffle: Boolean,
    val repeat: Int,
    val pitch: Float,
    val speed: Float,
    val volume: Float,
    val eqEnabled: Boolean,
    val eqBands: List<Int>,
    val eqPreset: String,
    val bass: Int,
    val virt: Int,
    val loud: Int,
    val accent: String,
    val bg: String,
    val visualizer: String,
    val resume: Boolean,
)

class UserPrefs(private val context: Context) {

    private object K {
        val FAVORITES = stringSetPreferencesKey("favorites")
        val PLAYLISTS = stringPreferencesKey("playlists")
        val RECENTS = stringPreferencesKey("recents")
        val QUEUE = stringPreferencesKey("queue_ids")
        val QUEUE_INDEX = intPreferencesKey("queue_index")
        val QUEUE_POS = longPreferencesKey("queue_pos")
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val REPEAT = intPreferencesKey("repeat")
        val PITCH = floatPreferencesKey("pitch")
        val SPEED = floatPreferencesKey("speed")
        val VOLUME = floatPreferencesKey("volume")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_BANDS = stringPreferencesKey("eq_bands")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val BASS = intPreferencesKey("bass")
        val VIRT = intPreferencesKey("virt")
        val LOUD = intPreferencesKey("loud")
        val ACCENT = stringPreferencesKey("accent")
        val BG = stringPreferencesKey("bg")
        val VIS = stringPreferencesKey("visualizer")
        val RESUME = booleanPreferencesKey("resume")
    }

    suspend fun load(): SavedState {
        val p = context.dataStore.data.first()
        fun csvLongs(s: String?): List<Long> =
            s?.split(',')?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()
        return SavedState(
            favorites = p[K.FAVORITES]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet(),
            playlistsJson = p[K.PLAYLISTS] ?: "[]",
            recents = csvLongs(p[K.RECENTS]),
            queueIds = csvLongs(p[K.QUEUE]),
            queueIndex = p[K.QUEUE_INDEX] ?: 0,
            queuePos = p[K.QUEUE_POS] ?: 0L,
            shuffle = p[K.SHUFFLE] ?: false,
            repeat = p[K.REPEAT] ?: 0,
            pitch = p[K.PITCH] ?: 0f,
            speed = p[K.SPEED] ?: 1f,
            volume = p[K.VOLUME] ?: 1f,
            eqEnabled = p[K.EQ_ENABLED] ?: false,
            eqBands = p[K.EQ_BANDS]?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList(),
            eqPreset = p[K.EQ_PRESET] ?: "Flat",
            bass = p[K.BASS] ?: 0,
            virt = p[K.VIRT] ?: 0,
            loud = p[K.LOUD] ?: 0,
            accent = p[K.ACCENT] ?: "Redline",
            bg = p[K.BG] ?: "Pure Black",
            visualizer = p[K.VIS] ?: "Bars",
            resume = p[K.RESUME] ?: true,
        )
    }

    suspend fun saveFavorites(ids: Set<Long>) =
        context.dataStore.edit { it[K.FAVORITES] = ids.map(Long::toString).toSet() }

    suspend fun savePlaylists(jsonString: String) =
        context.dataStore.edit { it[K.PLAYLISTS] = jsonString }

    suspend fun saveRecents(ids: List<Long>) =
        context.dataStore.edit { it[K.RECENTS] = ids.joinToString(",") }

    suspend fun saveSession(ids: List<Long>, index: Int, pos: Long, shuffle: Boolean, repeat: Int) =
        context.dataStore.edit {
            it[K.QUEUE] = ids.joinToString(",")
            it[K.QUEUE_INDEX] = index
            it[K.QUEUE_POS] = pos
            it[K.SHUFFLE] = shuffle
            it[K.REPEAT] = repeat
        }

    suspend fun saveAudio(pitch: Float, speed: Float, volume: Float) =
        context.dataStore.edit {
            it[K.PITCH] = pitch
            it[K.SPEED] = speed
            it[K.VOLUME] = volume
        }

    suspend fun saveEq(enabled: Boolean, bands: List<Int>, preset: String, bass: Int, virt: Int, loud: Int) =
        context.dataStore.edit {
            it[K.EQ_ENABLED] = enabled
            it[K.EQ_BANDS] = bands.joinToString(",")
            it[K.EQ_PRESET] = preset
            it[K.BASS] = bass
            it[K.VIRT] = virt
            it[K.LOUD] = loud
        }

    suspend fun saveLook(accent: String, bg: String, visualizer: String) =
        context.dataStore.edit {
            it[K.ACCENT] = accent
            it[K.BG] = bg
            it[K.VIS] = visualizer
        }

    suspend fun saveResume(resume: Boolean) =
        context.dataStore.edit { it[K.RESUME] = resume }
}
