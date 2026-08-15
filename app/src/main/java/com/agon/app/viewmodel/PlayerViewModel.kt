package com.agon.app.viewmodel

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.agon.app.data.Album
import com.agon.app.data.ArtistInfo
import com.agon.app.data.LyricLine
import com.agon.app.data.MusicRepository
import com.agon.app.data.Playlist
import com.agon.app.data.Song
import com.agon.app.data.UserPrefs
import com.agon.app.data.toMediaItem
import com.agon.app.player.AudioFxManager
import com.agon.app.player.EqPresets
import com.agon.app.player.PlaybackService
import com.agon.app.player.PlayerHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val fx = AudioFxManager()
    private val json = Json { ignoreUnknownKeys = true }

    // ---------------- Library ----------------
    var songs by mutableStateOf<List<Song>>(emptyList()); private set
    var albums by mutableStateOf<List<Album>>(emptyList()); private set
    var artists by mutableStateOf<List<ArtistInfo>>(emptyList()); private set
    var isLoading by mutableStateOf(false); private set
    private var songMap: Map<Long, Song> = emptyMap()
    private var libraryRequested = false

    // ---------------- User data ----------------
    var favorites by mutableStateOf<Set<Long>>(emptySet()); private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList()); private set
    var recentIds by mutableStateOf<List<Long>>(emptyList()); private set

    val recentSongs: List<Song> get() = recentIds.mapNotNull { songMap[it] }
    val favoriteSongs: List<Song> get() = songs.filter { it.id in favorites }
    fun songById(id: Long): Song? = songMap[id]

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
            if (playbackState == Player.STATE_READY) durationMs = player.duration.coerceAtLeast(0L)
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            attachFx(audioSessionId)
        }
    }

    init {
        player.addListener(listener)
        attachFx(player.audioSessionId)
        viewModelScope.launch { loadPrefs() }
        // Position ticker
        viewModelScope.launch {
            while (isActive) {
                if (player.playbackState != Player.STATE_IDLE) {
                    positionMs = player.currentPosition.coerceAtLeast(0L)
                    durationMs = player.duration.coerceAtLeast(0L)
                }
                delay(500)
            }
        }
        // Periodic session save while playing
        viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                if (isPlaying) saveSession()
            }
        }
    }

    override fun onCleared() {
        player.removeListener(listener)
        super.onCleared()
    }

    // ---------------- Prefs ----------------

    private suspend fun loadPrefs() {
        val s = prefs.load()
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
        applyPlaybackParams()
        player.volume = volume
        reconcileBands()
        applyAllFx()
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
            songs = loaded
            songMap = loaded.associateBy { it.id }
            albums = loaded.groupBy { it.albumId }
                .map { (id, ss) -> Album(id, ss.first().album, ss.first().artist, ss.size, ss.first().artUri) }
                .sortedBy { it.name.lowercase() }
            artists = loaded.groupBy { it.artist }
                .map { (name, ss) -> ArtistInfo(name, ss.size, ss.map { it.albumId }.distinct().size) }
                .sortedBy { it.name.lowercase() }
            isLoading = false
            if (restore && resumeSession) restoreSession()
            refreshCurrent()
            syncQueue()
        }
    }

    // ---------------- Session persistence ----------------

    private suspend fun restoreSession() {
        if (player.mediaItemCount > 0) return
        val s = prefs.load()
        val items = s.queueIds.mapNotNull { songMap[it] }
        if (items.isEmpty()) return
        val idx = s.queueIndex.coerceIn(0, items.lastIndex)
        player.setMediaItems(items.map { it.toMediaItem() }, idx, s.queuePos.coerceAtLeast(0L))
        player.shuffleModeEnabled = s.shuffle
        player.repeatMode = s.repeat
        player.playWhenReady = false
        player.prepare()
    }

    private fun saveSession() {
        if (player.mediaItemCount == 0) return
        val ids = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }
        val idx = player.currentMediaItemIndex
        val pos = player.currentPosition.coerceAtLeast(0L)
        val sh = player.shuffleModeEnabled
        val rp = player.repeatMode
        viewModelScope.launch { prefs.saveSession(ids, idx, pos, sh, rp) }
    }

    // ---------------- Playback ----------------

    fun playSongs(list: List<Song>, startIndex: Int = 0, shuffled: Boolean = false) {
        if (list.isEmpty()) return
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
            player.addMediaItem(song.toMediaItem())
            toast("Added to queue")
        }
    }

    fun playNextSong(song: Song) {
        if (player.mediaItemCount == 0) playSongs(listOf(song))
        else {
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
        applyPlaybackParams()
        persistAudio()
    }

    fun nudgePitch(delta: Float) = setPitch(pitchSemitones + delta)

    fun resetPitch() = setPitch(0f)

    fun setPlaybackSpeed(newSpeed: Float) {
        speed = newSpeed.coerceIn(0.5f, 2f)
        applyPlaybackParams()
        persistAudio()
    }

    fun setVolume(v: Float) {
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

    fun playlistSongs(p: Playlist): List<Song> = p.songIds.mapNotNull { songMap[it] }

    // ---------------- History ----------------

    private fun recordRecent() {
        val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        recentIds = (listOf(id) + recentIds.filter { it != id }).take(50)
        viewModelScope.launch { prefs.saveRecents(recentIds) }
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

    fun setEqEnabled(enabled: Boolean) {
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

    fun setResumeSession(enabled: Boolean) {
        resumeSession = enabled
        viewModelScope.launch { prefs.saveResume(enabled) }
    }

    // ---------------- Internal ----------------

    private fun refreshCurrent() {
        currentSong = player.currentMediaItem?.mediaId?.toLongOrNull()?.let { songMap[it] }
        positionMs = player.currentPosition.coerceAtLeast(0L)
        durationMs = player.duration.coerceAtLeast(0L)
        queueIndex = player.currentMediaItemIndex
    }

    private fun syncQueue() {
        val n = player.mediaItemCount
        queue = (0 until n).mapNotNull { i ->
            player.getMediaItemAt(i).mediaId.toLongOrNull()?.let { songMap[it] }
        }
        queueIndex = player.currentMediaItemIndex
        if (currentSong == null) refreshCurrent()
    }

    private fun startService() {
        runCatching {
            val ctx = getApplication<Application>()
            ctx.startService(Intent(ctx, PlaybackService::class.java))
        }
    }
}
