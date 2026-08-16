package com.agon.app.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.agon.app.data.Album
import com.agon.app.data.ArtistInfo
import com.agon.app.data.LyricLine
import com.agon.app.data.MusicRepository
import com.agon.app.data.Playlist
import com.agon.app.data.SavedState
import com.agon.app.data.Song
import com.agon.app.data.UserPrefs
import com.agon.app.data.toMediaItem
import com.agon.app.data.youtube.YouTubeAuth
import com.agon.app.data.youtube.YouTubeUnavailableException
import com.agon.app.data.youtube.YtAlbum
import com.agon.app.data.youtube.YtArtist
import com.agon.app.data.youtube.YtPlaylist
import com.agon.app.data.youtube.YtSearchResults
import com.agon.app.data.youtube.YtTrack
import com.agon.app.data.youtube.videoIdOf
import com.agon.app.player.EqPresets
import com.agon.app.player.PlaybackService
import com.agon.app.player.PlayerHolder
import com.agon.app.player.TempoPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.pow

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = UserPrefs(app)
    private val repo = MusicRepository(app)
    val player: ExoPlayer = PlayerHolder.get(app)
    // Shared with the player singleton: a recreated ViewModel re-uses the effects that
    // are already attached to the audio session instead of leaking native ones.
    private val fx = PlayerHolder.audioFx()
    private val json = Json { ignoreUnknownKeys = true }
    private val youtube = PlayerHolder.youTubeRepository()
    private val auth = YouTubeAuth(app)

    // ---------------- Library ----------------
    var songs by mutableStateOf<List<Song>>(emptyList()); private set
    var albums by mutableStateOf<List<Album>>(emptyList()); private set
    var artists by mutableStateOf<List<ArtistInfo>>(emptyList()); private set
    var isLoading by mutableStateOf(false); private set
    private var songMap by mutableStateOf<Map<Long, Song>>(emptyMap())
    private var libraryRequested = false

    // ---------------- User data ----------------
    var favorites by mutableStateOf<Set<Long>>(emptySet()); private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList()); private set
    var recentIds by mutableStateOf<List<Long>>(emptyList()); private set

    /**
     * Bounded LRU of songs that don't come from MediaStore (YouTube results), so the
     * queue, mini player and history can still resolve them by id. Held as snapshot
     * state so derived values recompute when it changes.
     */
    private var remoteSongs by mutableStateOf<Map<Long, Song>>(emptyMap())

    private fun resolveSong(id: Long): Song? = songMap[id] ?: remoteSongs[id]

    private fun rememberRemote(list: List<Song>) {
        val additions = list.filter { it.id !in songMap && it.id !in remoteSongs }
        if (additions.isEmpty()) return
        val updated = LinkedHashMap<Long, Song>(remoteSongs.size + additions.size)
        updated.putAll(remoteSongs)
        for (song in additions) updated[song.id] = song
        // Keep the newest entries only, so long browsing sessions can't grow the heap.
        remoteSongs = if (updated.size <= MAX_REMOTE_SONGS) updated else {
            val trimmed = LinkedHashMap<Long, Song>(MAX_REMOTE_SONGS)
            val drop = updated.size - MAX_REMOTE_SONGS
            var i = 0
            for ((key, value) in updated) {
                if (i++ < drop) continue
                trimmed[key] = value
            }
            trimmed
        }
        persistRemoteSongs()
    }

    /**
     * Seeds [remoteSongs] from the persisted cache so YouTube items survive an app
     * restart (they don't live in MediaStore). Idempotent: entries that already
     * resolve locally are skipped.
     */
    private fun seedRemoteSongs() {
        val s = savedState ?: return
        if (s.ytSongsJson.isBlank()) return
        val cached = runCatching { json.decodeFromString<List<Song>>(s.ytSongsJson) }.getOrDefault(emptyList())
        if (cached.isEmpty()) return
        val updated = LinkedHashMap<Long, Song>(remoteSongs.size + cached.size)
        updated.putAll(remoteSongs)
        for (song in cached) if (song.id !in songMap && song.id !in updated) updated[song.id] = song
        if (updated.size != remoteSongs.size) remoteSongs = updated
    }

    private var remoteSaveJob: Job? = null

    private fun persistRemoteSongs() {
        remoteSaveJob?.cancel()
        remoteSaveJob = viewModelScope.launch {
            delay(500)
            prefs.saveYtSongs(json.encodeToString(remoteSongs.values.toList()))
        }
    }

    // Derived once per dependency change instead of re-filtering the whole library on
    // every recomposition that reads these.
    private val recentSongsState = derivedStateOf { recentIds.mapNotNull { resolveSong(it) } }
    private val favoriteSongsState = derivedStateOf { songs.filter { it.id in favorites } }

    val recentSongs: List<Song> get() = recentSongsState.value
    val favoriteSongs: List<Song> get() = favoriteSongsState.value
    fun songById(id: Long): Song? = resolveSong(id)

    // ---------------- Playback state ----------------
    var currentSong by mutableStateOf<Song?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var positionMs by mutableLongStateOf(0L); private set
    var durationMs by mutableLongStateOf(0L); private set
    var shuffleOn by mutableStateOf(false); private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF); private set
    var queue by mutableStateOf<List<Song>>(emptyList()); private set
    var queueIndex by mutableIntStateOf(0); private set

    // ---------------- Audio params ----------------
    var pitchSemitones by mutableFloatStateOf(0f); private set
    var speed by mutableFloatStateOf(1f); private set
    var volume by mutableFloatStateOf(1f); private set

    // ---------------- Tempo presets ----------------
    var tempoPreset by mutableStateOf("Normal"); private set

    // ---------------- EQ / FX ----------------
    var fxAvailable by mutableStateOf(false); private set
    var eqEnabled by mutableStateOf(false); private set
    var bandLevels by mutableStateOf<List<Int>>(emptyList()); private set
    var bandFreqs by mutableStateOf<List<Int>>(emptyList()); private set
    var eqMin by mutableIntStateOf(-1500); private set
    var eqMax by mutableIntStateOf(1500); private set
    var eqPreset by mutableStateOf("Flat"); private set
    var bassStrength by mutableIntStateOf(0); private set
    var virtStrength by mutableIntStateOf(0); private set
    var loudnessGain by mutableIntStateOf(0); private set

    // ---------------- Sleep timer ----------------
    var sleepRemainingMs by mutableStateOf<Long?>(null); private set
    var stopAfterCurrent by mutableStateOf(false); private set
    private var sleepJob: Job? = null

    // ---------------- UI ----------------
    var showNowPlaying by mutableStateOf(false)
    var message by mutableStateOf<String?>(null); private set
    fun consumeMessage() { message = null }
    private fun toast(text: String) { message = text }

    // ---------------- Settings ----------------
    var accentName by mutableStateOf("Redline"); private set
    var bgName by mutableStateOf("Pure Black"); private set
    var visualizerStyle by mutableStateOf("Bars"); private set
    var resumeSession by mutableStateOf(true); private set

    // ---------------- Lyrics ----------------
    var lyrics by mutableStateOf<List<LyricLine>?>(null); private set
    private var lyricsForSongId: Long = -1L

    /** Consecutive playback errors, used to stop auto-skipping after a run of broken items. */
    private var consecutivePlaybackErrors = 0

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            if (!playing) saveSession()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refreshCurrent()
            if (stopAfterCurrent && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                player.pause()
                stopAfterCurrent = false
                toast("Sleep timer: playback stopped")
            }
            if (player.playWhenReady) recordRecent()
        }
        override fun onEvents(p: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) {
                syncQueue()
                shuffleOn = p.shuffleModeEnabled
                repeatMode = p.repeatMode
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                consecutivePlaybackErrors = 0
                durationMs = player.duration.coerceAtLeast(0L)
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            attachFx(audioSessionId)
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlaybackError(error)
        }
    }

    /**
     * Keeps playback alive when a streamed item can't be played (removed, private,
     * region- or age-restricted, or an expired URL): reports it once and skips ahead
     * instead of letting the player die. Auto-advancing is capped so a run of broken
     * items stops instead of silently skipping through the whole queue forever.
     */
    private fun handlePlaybackError(error: PlaybackException) {
        val failedId = player.currentMediaItem?.mediaId?.toLongOrNull()
        val videoId = failedId?.let { resolveSong(it) }?.let(::videoIdOf)
        if (videoId != null) youtube.invalidateStream(videoId)

        val reason = generateSequence(error.cause) { it.cause }
            .filterIsInstance<YouTubeUnavailableException>()
            .firstOrNull()
            ?.message

        Log.w(TAG, "Playback error: ${error.message} (cause=${error.cause})")

        consecutivePlaybackErrors++
        val canAdvance = player.hasNextMediaItem() && consecutivePlaybackErrors < MAX_AUTO_SKIPS
        if (canAdvance) {
            toast(reason ?: "Can't play this track \u2014 skipping")
            player.seekToNextMediaItem()
            player.prepare()
        } else {
            consecutivePlaybackErrors = 0
            player.pause()
            toast(reason ?: "Playback failed")
        }
    }

    init {
        player.addListener(listener)
        attachFx(player.audioSessionId)
        viewModelScope.launch { loadPrefs() }
        // Restore the YouTube Music sign-in state (if any) and pull the account library.
        viewModelScope.launch {
            ytSignedIn = auth.isSignedIn()
            if (ytSignedIn) loadYtLibrary()
        }
        // Position ticker — only runs while something is actually playing, so an idle
        // or paused app does no periodic work at all (battery + CPU).
        viewModelScope.launch {
            snapshotFlow { isPlaying }.collectLatest { playing ->
                // One immediate sync so the UI is correct the moment playback stops.
                syncProgress()
                if (!playing) return@collectLatest
                while (isActive) {
                    delay(500)
                    syncProgress()
                }
            }
        }
        // Periodic session save while playing
        viewModelScope.launch {
            snapshotFlow { isPlaying }.collectLatest { playing ->
                if (!playing) return@collectLatest
                while (isActive) {
                    delay(10_000)
                    saveSession()
                }
            }
        }
    }

    private fun syncProgress() {
        if (player.playbackState == Player.STATE_IDLE) return
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.coerceAtLeast(0L)
        // Only touch snapshot state when the value really changed: avoids waking up
        // every observer (mini player, seek bar, lyrics) twice a second for nothing.
        if (position != positionMs) positionMs = position
        if (duration != durationMs) durationMs = duration
    }

    override fun onCleared() {
        player.removeListener(listener)
        super.onCleared()
    }

    // ---------------- Prefs ----------------

    private var savedState: SavedState? = null

    private suspend fun loadPrefs() {
        val s = prefs.load().also { savedState = it }
        favorites = s.favorites
        recentIds = s.recents
        playlists = runCatching { json.decodeFromString<List<Playlist>>(s.playlistsJson) }
            .getOrDefault(emptyList())
        pitchSemitones = s.pitch
        speed = s.speed
        volume = s.volume
        eqEnabled = s.eqEnabled
        if (s.eqBands.isNotEmpty()) bandLevels = s.eqBands
        eqPreset = s.eqPreset
        bassStrength = s.bass
        virtStrength = s.virt
        loudnessGain = s.loud
        accentName = s.accent
        bgName = s.bg
        visualizerStyle = s.visualizer
        resumeSession = s.resume
        tempoPreset = TempoPresets.detect(speed, pitchSemitones)
        applyPlaybackParams()
        player.volume = volume
        reconcileBands()
        applyAllFx()
        seedRemoteSongs()
    }

    // ---------------- Library ----------------

    fun onPermissionGranted() {
        if (!libraryRequested) {
            libraryRequested = true
            loadLibrary(restore = true)
        }
    }

    fun rescanLibrary() {
        toast("Rescanning library…")
        loadLibrary(restore = false)
    }

    private fun loadLibrary(restore: Boolean) {
        viewModelScope.launch {
            isLoading = true
            val loaded = repo.loadSongs()
            // Grouping and sorting the whole library is O(n log n) work — keep it off
            // the main thread so a big library never janks the first frame.
            val indexed = withContext(Dispatchers.Default) {
                val map = HashMap<Long, Song>(loaded.size * 2)
                val byAlbum = LinkedHashMap<Long, MutableList<Song>>()
                val byArtist = LinkedHashMap<String, MutableList<Song>>()
                for (song in loaded) {
                    map[song.id] = song
                    byAlbum.getOrPut(song.albumId) { ArrayList(12) }.add(song)
                    byArtist.getOrPut(song.artist) { ArrayList(12) }.add(song)
                }
                val albumList = byAlbum.map { (id, ss) ->
                    val first = ss[0]
                    Album(id, first.album, first.artist, ss.size, first.artUri)
                }.sortedBy { it.name.lowercase() }
                val artistList = byArtist.map { (name, ss) ->
                    val albumIds = HashSet<Long>(ss.size)
                    for (song in ss) albumIds.add(song.albumId)
                    ArtistInfo(name, ss.size, albumIds.size)
                }.sortedBy { it.name.lowercase() }
                LibraryIndex(map, albumList, artistList)
            }
            songs = loaded
            songMap = indexed.byId
            albums = indexed.albums
            artists = indexed.artists
            isLoading = false
            seedRemoteSongs()
            if (restore && resumeSession) restoreSession()
            refreshCurrent()
            syncQueue()
        }
    }

    private class LibraryIndex(
        val byId: Map<Long, Song>,
        val albums: List<Album>,
        val artists: List<ArtistInfo>,
    )

    // ---------------- Session persistence ----------------

    private suspend fun restoreSession() {
        if (player.mediaItemCount > 0) return
        // Reuse the state read at startup instead of paying for a second DataStore read.
        val s = savedState ?: prefs.load().also { savedState = it }
        val items = s.queueIds.mapNotNull { resolveSong(it) }
        if (items.isEmpty()) return
        val idx = s.queueIndex.coerceIn(0, items.lastIndex)
        player.setMediaItems(items.map { it.toMediaItem() }, idx, s.queuePos.coerceAtLeast(0L))
        player.shuffleModeEnabled = s.shuffle
        player.repeatMode = s.repeat
        player.playWhenReady = false
        player.prepare()
    }

    private var lastSavedSession: Int = 0

    private fun saveSession() {
        val count = player.mediaItemCount
        if (count == 0) return
        val ids = ArrayList<Long>(count)
        for (i in 0 until count) player.getMediaItemAt(i).mediaId.toLongOrNull()?.let(ids::add)
        val idx = player.currentMediaItemIndex
        val pos = player.currentPosition.coerceAtLeast(0L)
        val sh = player.shuffleModeEnabled
        val rp = player.repeatMode
        // Skip the write when nothing meaningful changed (position is bucketed to 5s):
        // fewer disk writes means less I/O and better battery life.
        val fingerprint = (((ids.hashCode() * 31 + idx) * 31 + (pos / 5_000L).toInt()) * 31 +
            (if (sh) 1 else 0)) * 31 + rp
        if (fingerprint == lastSavedSession) return
        lastSavedSession = fingerprint
        viewModelScope.launch { prefs.saveSession(ids, idx, pos, sh, rp) }
    }

    // ---------------- Playback ----------------

    fun playSongs(list: List<Song>, startIndex: Int = 0, shuffled: Boolean = false) {
        if (list.isEmpty()) return
        consecutivePlaybackErrors = 0
        rememberRemote(list)
        player.setMediaItems(list.map { it.toMediaItem() }, startIndex.coerceIn(0, list.lastIndex), 0L)
        player.shuffleModeEnabled = shuffled
        player.prepare()
        player.play()
        startService()
    }

    fun shuffleAll() {
        if (songs.isEmpty()) return
        playSongs(songs, songs.indices.random(), shuffled = true)
        toast("Shuffling all songs")
    }

    fun togglePlayPause() {
        if (player.mediaItemCount == 0) return
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.isPlaying) player.pause() else {
            player.play()
            startService()
        }
    }

    fun next() {
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
        else player.seekTo(player.duration.coerceAtLeast(0L))
    }

    fun previous() {
        if (player.currentPosition > 3000 || !player.hasPreviousMediaItem()) player.seekTo(0)
        else player.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        player.seekTo(ms.coerceIn(0L, durationMs.coerceAtLeast(0L)))
        positionMs = ms.coerceAtLeast(0L)
    }

    fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        toast(if (player.shuffleModeEnabled) "Shuffle on" else "Shuffle off")
    }

    fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playFromQueue(index: Int) {
        if (index in 0 until player.mediaItemCount) {
            player.seekTo(index, 0L)
            player.play()
            startService()
        }
    }

    // ---------------- Queue ----------------

    fun addToQueue(song: Song) {
        if (player.mediaItemCount == 0) playSongs(listOf(song))
        else {
            rememberRemote(listOf(song))
            player.addMediaItem(song.toMediaItem())
            toast("Added to queue")
        }
    }

    fun playNextSong(song: Song) {
        if (player.mediaItemCount == 0) playSongs(listOf(song))
        else {
            rememberRemote(listOf(song))
            player.addMediaItem(player.currentMediaItemIndex + 1, song.toMediaItem())
            toast("Playing next")
        }
    }

    fun removeFromQueue(index: Int) {
        if (index in 0 until player.mediaItemCount) player.removeMediaItem(index)
    }

    fun moveQueueItem(from: Int, to: Int) {
        if (from != to && from in 0 until player.mediaItemCount && to in 0 until player.mediaItemCount) {
            player.moveMediaItem(from, to)
        }
    }

    fun clearQueue() {
        val cur = player.currentMediaItemIndex
        if (cur < player.mediaItemCount - 1) player.removeMediaItems(cur + 1, player.mediaItemCount)
        if (cur > 0) player.removeMediaItems(0, cur)
        toast("Queue cleared")
    }

    // ---------------- Pitch / speed / volume ----------------

    private var audioSaveJob: Job? = null
    private fun persistAudio() {
        audioSaveJob?.cancel()
        audioSaveJob = viewModelScope.launch {
            delay(400)
            prefs.saveAudio(pitchSemitones, speed, volume)
        }
    }

    fun setPitch(semitones: Float) {
        pitchSemitones = semitones.coerceIn(-12f, 12f)
        tempoPreset = "Custom"
        applyPlaybackParams()
        persistAudio()
    }

    fun nudgePitch(delta: Float) = setPitch(pitchSemitones + delta)

    fun resetPitch() = setPitch(0f)

    fun setPlaybackSpeed(newSpeed: Float) {
        speed = newSpeed.coerceIn(0.5f, 2f)
        tempoPreset = "Custom"
        applyPlaybackParams()
        persistAudio()
    }

    /** Applies a curated speed + pitch preset (Nightcore, Deep, Vaporwave, …). */
    fun applyTempoPreset(name: String) {
        val preset = TempoPresets.byName(name) ?: return
        tempoPreset = preset.name
        speed = preset.speed
        pitchSemitones = preset.pitchSemitones
        applyPlaybackParams()
        persistAudio()
    }

    fun resetTempo() = applyTempoPreset("Normal")

    fun updateVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        player.volume = volume
        persistAudio()
    }

    private fun applyPlaybackParams() {
        val pitchFactor = 2f.pow(pitchSemitones / 12f)
        player.playbackParameters = PlaybackParameters(speed, pitchFactor)
    }

    // ---------------- Favorites ----------------

    fun toggleFavorite(id: Long) {
        val adding = id !in favorites
        favorites = if (adding) favorites + id else favorites - id
        viewModelScope.launch { prefs.saveFavorites(favorites) }
        toast(if (adding) "Added to favorites" else "Removed from favorites")
    }

    // ---------------- Playlists ----------------

    private fun persistPlaylists() {
        val payload = json.encodeToString(playlists)
        viewModelScope.launch { prefs.savePlaylists(payload) }
    }

    fun createPlaylist(name: String): Playlist {
        val p = Playlist(System.currentTimeMillis(), name.trim().ifBlank { "New Playlist" })
        playlists = playlists + p
        persistPlaylists()
        return p
    }

    fun renamePlaylist(id: Long, name: String) {
        playlists = playlists.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it }
        persistPlaylists()
    }

    fun deletePlaylist(id: Long) {
        playlists = playlists.filter { it.id != id }
        persistPlaylists()
        toast("Playlist deleted")
    }

    fun addToPlaylist(playlistId: Long, songId: Long) {
        var added = false
        playlists = playlists.map {
            if (it.id == playlistId && songId !in it.songIds) {
                added = true
                it.copy(songIds = it.songIds + songId)
            } else it
        }
        persistPlaylists()
        toast(if (added) "Added to playlist" else "Already in playlist")
    }

    fun removeFromPlaylist(playlistId: Long, songId: Long) {
        playlists = playlists.map {
            if (it.id == playlistId) it.copy(songIds = it.songIds - songId) else it
        }
        persistPlaylists()
    }

    fun togglePlaylistSong(playlistId: Long, songId: Long) {
        val p = playlists.firstOrNull { it.id == playlistId } ?: return
        if (songId in p.songIds) removeFromPlaylist(playlistId, songId)
        else {
            playlists = playlists.map {
                if (it.id == playlistId) it.copy(songIds = it.songIds + songId) else it
            }
            persistPlaylists()
        }
    }

    fun playlistSongs(p: Playlist): List<Song> = p.songIds.mapNotNull { resolveSong(it) }

    // ---------------- History ----------------

    private fun recordRecent() {
        val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        if (recentIds.firstOrNull() == id) return // already on top: nothing to write
        val updated = ArrayList<Long>(minOf(recentIds.size + 1, 50))
        updated.add(id)
        for (existing in recentIds) {
            if (updated.size >= 50) break
            if (existing != id) updated.add(existing)
        }
        recentIds = updated
        viewModelScope.launch { prefs.saveRecents(updated) }
    }

    fun clearHistory() {
        recentIds = emptyList()
        viewModelScope.launch { prefs.saveRecents(emptyList()) }
        toast("History cleared")
    }

    // ---------------- EQ / FX ----------------

    private fun attachFx(sessionId: Int) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        fxAvailable = fx.attach(sessionId)
        if (fxAvailable) {
            eqMin = fx.minLevel
            eqMax = fx.maxLevel
            bandFreqs = fx.centerFreqs
            reconcileBands()
            applyAllFx()
        } else if (bandFreqs.isEmpty()) {
            bandFreqs = listOf(60, 230, 910, 3600, 14000)
            reconcileBands()
        }
    }

    private fun reconcileBands() {
        val count = if (fxAvailable && fx.bands > 0) fx.bands else if (bandFreqs.isNotEmpty()) bandFreqs.size else 5
        if (bandFreqs.size != count) bandFreqs = listOf(60, 230, 910, 3600, 14000).take(count) + List((count - 5).coerceAtLeast(0)) { 16000 }
        if (bandLevels.size != count) bandLevels = EqPresets.curve(eqPreset, count, eqMin, eqMax)
    }

    private fun applyAllFx() {
        if (!fxAvailable) return
        fx.setEnabled(eqEnabled)
        bandLevels.forEachIndexed { i, mb -> fx.setBand(i, mb) }
        fx.setBass(bassStrength)
        fx.setVirtualizer(virtStrength)
        fx.setLoudness(loudnessGain)
    }

    private var eqSaveJob: Job? = null
    private fun persistEq() {
        eqSaveJob?.cancel()
        eqSaveJob = viewModelScope.launch {
            delay(400)
            prefs.saveEq(eqEnabled, bandLevels, eqPreset, bassStrength, virtStrength, loudnessGain)
        }
    }

    fun updateEqEnabled(enabled: Boolean) {
        eqEnabled = enabled
        if (fxAvailable) {
            fx.setEnabled(enabled)
            if (enabled) applyAllFx()
        }
        persistEq()
    }

    fun setBand(index: Int, millibels: Int) {
        if (index !in bandLevels.indices) return
        bandLevels = bandLevels.toMutableList().also { it[index] = millibels }
        eqPreset = "Custom"
        if (fxAvailable) fx.setBand(index, millibels)
        persistEq()
    }

    fun applyEqPreset(name: String) {
        eqPreset = name
        if (name != "Custom") {
            val curve = EqPresets.curve(name, bandLevels.size, eqMin, eqMax)
            bandLevels = curve
            if (fxAvailable) curve.forEachIndexed { i, mb -> fx.setBand(i, mb) }
        }
        persistEq()
    }

    fun setBass(strength: Int) {
        bassStrength = strength.coerceIn(0, 1000)
        if (fxAvailable) fx.setBass(bassStrength)
        persistEq()
    }

    fun setVirtualizer(strength: Int) {
        virtStrength = strength.coerceIn(0, 1000)
        if (fxAvailable) fx.setVirtualizer(virtStrength)
        persistEq()
    }

    fun setLoudness(gainMb: Int) {
        loudnessGain = gainMb.coerceIn(0, 2000)
        if (fxAvailable) fx.setLoudness(loudnessGain)
        persistEq()
    }

    fun resetFx() {
        applyEqPreset("Flat")
        bassStrength = 0
        virtStrength = 0
        loudnessGain = 0
        if (fxAvailable) {
            fx.setBass(0); fx.setVirtualizer(0); fx.setLoudness(0)
        }
        persistEq()
        toast("Audio effects reset")
    }

    // ---------------- Sleep timer ----------------

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer(silent = true)
        sleepJob = viewModelScope.launch {
            var remaining = minutes * 60_000L
            sleepRemainingMs = remaining
            while (remaining > 0 && isActive) {
                delay(1000)
                remaining -= 1000
                sleepRemainingMs = remaining
            }
            if (isActive) {
                player.pause()
                sleepRemainingMs = null
                toast("Sleep timer: playback stopped")
            }
        }
        toast("Sleep timer set for $minutes min")
    }

    fun toggleStopAfterCurrent() {
        stopAfterCurrent = !stopAfterCurrent
        if (stopAfterCurrent) toast("Stopping after current song")
    }

    fun cancelSleepTimer(silent: Boolean = false) {
        sleepJob?.cancel()
        sleepJob = null
        sleepRemainingMs = null
        if (!silent) toast("Sleep timer cancelled")
    }

    // ---------------- Lyrics ----------------

    fun loadLyrics(song: Song) {
        if (lyricsForSongId == song.id) return
        lyricsForSongId = song.id
        lyrics = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val data = song.data ?: return@runCatching null
                    val base = data.substringBeforeLast('.')
                    val f = File("$base.lrc")
                    if (f.exists() && f.canRead()) parseLrc(f.readText()) else null
                }.getOrNull()
            }
            if (lyricsForSongId == song.id) lyrics = result ?: emptyList()
        }
    }

    private fun parseLrc(text: String): List<LyricLine> {
        val re = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
        val out = mutableListOf<LyricLine>()
        text.lines().forEach { line ->
            val matches = re.findAll(line).toList()
            val content = line.replace(re, "").trim()
            if (matches.isEmpty()) {
                if (content.isNotEmpty() && !content.startsWith("[")) out.add(LyricLine(-1, content))
            } else {
                matches.forEach { m ->
                    val min = m.groupValues[1].toLongOrNull() ?: 0L
                    val sec = m.groupValues[2].toLongOrNull() ?: 0L
                    val fracStr = m.groupValues[3]
                    val frac = if (fracStr.isEmpty()) 0L else fracStr.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                    if (content.isNotEmpty()) out.add(LyricLine(min * 60_000 + sec * 1000 + frac, content))
                }
            }
        }
        return out.sortedBy { it.timeMs }
    }

    // ---------------- Settings ----------------

    fun setAccent(name: String) {
        accentName = name
        viewModelScope.launch { prefs.saveLook(accentName, bgName, visualizerStyle) }
    }

    fun setBackground(name: String) {
        bgName = name
        viewModelScope.launch { prefs.saveLook(accentName, bgName, visualizerStyle) }
    }

    fun setVisualizer(style: String) {
        visualizerStyle = style
        viewModelScope.launch { prefs.saveLook(accentName, bgName, visualizerStyle) }
    }

    fun updateResumeSession(enabled: Boolean) {
        resumeSession = enabled
        viewModelScope.launch { prefs.saveResume(enabled) }
    }

    // ---------------- YouTube Music ----------------

    var ytQuery by mutableStateOf(""); private set
    var ytResults by mutableStateOf(YtSearchResults.EMPTY); private set
    var ytSearching by mutableStateOf(false); private set
    var ytError by mutableStateOf<String?>(null); private set
    private var ytLoadingCollection = false

    private var ytSearchJob: Job? = null

    // ---------------- YouTube account + library ----------------
    var ytSignedIn by mutableStateOf(false); private set
    var ytAuthBusy by mutableStateOf(false); private set
    var ytLoginUrl by mutableStateOf<String?>(null); private set
    var ytLoginCode by mutableStateOf<String?>(null); private set
    var ytLikedSongs by mutableStateOf<List<YtTrack>>(emptyList()); private set
    var ytPlaylists by mutableStateOf<List<YtPlaylist>>(emptyList()); private set
    var ytLibraryLoading by mutableStateOf(false); private set
    private var ytAuthJob: Job? = null

    fun updateYtQuery(query: String) {
        ytQuery = query
        val trimmed = query.trim()
        ytSearchJob?.cancel()
        if (trimmed.length < 2) {
            ytResults = YtSearchResults.EMPTY
            ytSearching = false
            ytError = null
            return
        }
        ytSearching = true
        ytError = null
        ytSearchJob = viewModelScope.launch {
            delay(350) // debounce: one request per pause in typing, not per keystroke
            val results = runCatching { youtube.search(trimmed) }.getOrNull()
            if (results == null) {
                ytResults = YtSearchResults.EMPTY
                ytError = "Couldn't reach YouTube Music"
            } else {
                ytResults = results
                ytError = if (results.isEmpty) "No results on YouTube Music" else null
            }
            ytSearching = false
        }
    }

    fun clearYtSearch() {
        ytSearchJob?.cancel()
        ytQuery = ""
        ytResults = YtSearchResults.EMPTY
        ytSearching = false
        ytError = null
    }

    // ---------------- YouTube account ----------------

    /** Starts the device-authorization sign-in flow. Progress shows in [ytLoginUrl]/[ytLoginCode]. */
    fun startYtLogin() {
        if (ytAuthBusy) return
        ytAuthBusy = true
        ytAuthJob = viewModelScope.launch {
            val device = auth.requestDeviceCode()
            if (device == null) {
                ytAuthBusy = false
                toast("Couldn't start sign-in \u2014 check your connection")
                return@launch
            }
            ytLoginUrl = device.verificationUrl
            ytLoginCode = device.userCode
            val ok = auth.pollForToken(device.deviceCode)
            ytLoginUrl = null
            ytLoginCode = null
            ytAuthBusy = false
            if (ok) {
                ytSignedIn = true
                toast("Signed in to YouTube Music")
                loadYtLibrary()
            } else {
                toast("Sign-in was cancelled or expired")
            }
        }
    }

    fun cancelYtLogin() {
        ytAuthJob?.cancel()
        ytAuthJob = null
        ytLoginUrl = null
        ytLoginCode = null
        ytAuthBusy = false
    }

    fun signOutYt() {
        viewModelScope.launch { auth.signOut() }
        ytSignedIn = false
        ytLikedSongs = emptyList()
        ytPlaylists = emptyList()
        toast("Signed out of YouTube Music")
    }

    fun refreshYtLibrary() = loadYtLibrary()

    private fun loadYtLibrary() {
        if (!ytSignedIn) return
        ytLibraryLoading = true
        viewModelScope.launch {
            val token = auth.accessToken()
            if (token == null) {
                ytSignedIn = false
                ytLibraryLoading = false
                toast("Signed out \u2014 please sign in again")
                return@launch
            }
            var failed = false
            val liked = runCatching { youtube.likedSongs(token) }
                .onFailure { failed = true }
                .getOrDefault(emptyList())
            val playlists = runCatching { youtube.libraryPlaylists(token) }
                .onFailure { failed = true }
                .getOrDefault(emptyList())
            ytLikedSongs = liked
            ytPlaylists = playlists
            ytLibraryLoading = false
            if (failed) toast("Couldn't load your YouTube Music library")
        }
    }

    /** Plays the account's liked songs through the existing player. */
    fun playYtLikedSongs() = loadYtCollectionAuthed("Liked songs") { token -> youtube.likedSongs(token) }

    /** Plays all tracks of a saved library playlist. */
    fun playYtPlaylist(playlist: YtPlaylist) =
        loadYtCollectionAuthed(playlist.title) { token ->
            youtube.libraryPlaylistTracks(token, playlist.browseId, playlist.thumbnailUrl)
        }

    private fun loadYtCollectionAuthed(label: String, load: suspend (String) -> List<YtTrack>) {
        if (ytLoadingCollection) return
        ytLoadingCollection = true
        viewModelScope.launch {
            val token = auth.accessToken()
            if (token == null) {
                ytLoadingCollection = false
                toast("Sign in to YouTube Music first")
                return@launch
            }
            val tracks = runCatching { load(token) }.getOrDefault(emptyList())
            ytLoadingCollection = false
            if (tracks.isEmpty()) toast("Nothing playable in $label")
            else playSongs(tracks.map(YtTrack::toSong))
        }
    }

    /** Plays a YouTube result through the existing player, keeping the rest of the list as the queue. */
    fun playYtTrack(track: YtTrack, context: List<YtTrack> = listOf(track)) {
        val source = if (context.isEmpty()) listOf(track) else context
        val index = source.indexOfFirst { it.videoId == track.videoId }.coerceAtLeast(0)
        playSongs(source.map(YtTrack::toSong), index)
    }

    fun queueYtTrack(track: YtTrack) = addToQueue(track.toSong())

    fun playYtTrackNext(track: YtTrack) = playNextSong(track.toSong())

    fun playYtAlbum(album: YtAlbum) {
        loadYtCollection(album.title) { youtube.albumTracks(album.browseId, album.thumbnailUrl) }
    }

    fun playYtArtist(artist: YtArtist) {
        loadYtCollection(artist.name) { youtube.artistTracks(artist.browseId, artist.thumbnailUrl) }
    }

    private fun loadYtCollection(label: String, load: suspend () -> List<YtTrack>) {
        if (ytLoadingCollection) return
        ytLoadingCollection = true
        viewModelScope.launch {
            val tracks = runCatching { load() }.getOrDefault(emptyList())
            ytLoadingCollection = false
            if (tracks.isEmpty()) toast("Nothing playable in $label")
            else playSongs(tracks.map(YtTrack::toSong))
        }
    }

    // ---------------- Internal ----------------

    private fun refreshCurrent() {
        val song = player.currentMediaItem?.mediaId?.toLongOrNull()?.let { resolveSong(it) }
        if (song?.id != currentSong?.id) currentSong = song
        positionMs = player.currentPosition.coerceAtLeast(0L)
        durationMs = player.duration.coerceAtLeast(0L)
        val index = player.currentMediaItemIndex
        if (index != queueIndex) queueIndex = index
    }

    private fun syncQueue() {
        val n = player.mediaItemCount
        val next = ArrayList<Song>(n)
        for (i in 0 until n) {
            player.getMediaItemAt(i).mediaId.toLongOrNull()?.let { id -> resolveSong(id)?.let(next::add) }
        }
        // Only publish a new list when the contents actually changed, so the queue
        // sheet and every other observer don't recompose on unrelated timeline events.
        if (!sameSongs(queue, next)) queue = next
        val index = player.currentMediaItemIndex
        if (index != queueIndex) queueIndex = index
        if (currentSong == null) refreshCurrent()
    }

    private fun sameSongs(a: List<Song>, b: List<Song>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) if (a[i].id != b[i].id) return false
        return true
    }

    private fun startService() {
        runCatching {
            val ctx = getApplication<Application>()
            ctx.startService(Intent(ctx, PlaybackService::class.java))
        }
    }

    private companion object {
        const val TAG = "PlayerViewModel"

        /** Upper bound on cached non-MediaStore songs; ~300 entries is a few hundred KB. */
        const val MAX_REMOTE_SONGS = 300

        /** Stop auto-advancing after this many consecutive playback failures. */
        const val MAX_AUTO_SKIPS = 3
    }
}
