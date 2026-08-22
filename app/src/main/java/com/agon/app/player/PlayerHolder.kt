package com.agon.app.player

import android.content.Context
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.agon.app.data.youtube.YouTubeRepository
import com.agon.app.data.youtube.youTubeAwareDataSourceFactory

/**
 * Application-wide single ExoPlayer instance shared by the UI (ViewModel)
 * and the media session foreground service.
 */
object PlayerHolder {

    @Volatile
    private var instance: ExoPlayer? = null

    @Volatile
    private var repository: YouTubeRepository? = null

    @Volatile
    private var fx: AudioFxManager? = null

    fun get(context: Context): ExoPlayer =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    /** Shared YouTube repository (search cache + stream resolution) used by UI and player. */
    fun youTubeRepository(): YouTubeRepository =
        repository ?: synchronized(this) {
            repository ?: YouTubeRepository().also { repository = it }
        }

    /** Audio effects bound to the shared player's session, outliving the ViewModel. */
    fun audioFx(): AudioFxManager =
        fx ?: synchronized(this) { fx ?: AudioFxManager().also { fx = it } }

    private fun build(context: Context): ExoPlayer {
        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            // Keep the network (and CPU) alive while streaming YouTube audio.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            // Local files keep using the default pipeline; `ytstream://` items get their
            // real progressive URL resolved on the loading thread just before opening.
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    youTubeAwareDataSourceFactory(context, youTubeRepository())
                )
            )
            .build()
        // Generate an audio session id right away so audio effects (EQ, bass
        // boost, virtualizer, loudness) can attach before the first playback.
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            player.audioSessionId = am.generateAudioSessionId()
        }
        return player
    }

    fun release() {
        fx?.release()
        fx = null
        instance?.release()
        instance = null
    }
}
