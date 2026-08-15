package com.agon.app.player

import android.content.Context
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

/**
 * Application-wide single ExoPlayer instance shared by the UI (ViewModel)
 * and the media session foreground service.
 */
object PlayerHolder {

    @Volatile
    private var instance: ExoPlayer? = null

    fun get(context: Context): ExoPlayer =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

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
        instance?.release()
        instance = null
    }
}
