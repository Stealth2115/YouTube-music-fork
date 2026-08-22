package com.agon.app.player

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.agon.app.MainActivity
import com.agon.app.R

/**
 * Foreground media session service: keeps music playing in the background,
 * provides the notification / lock-screen media controls and handles audio
 * focus + becoming-noisy through the shared ExoPlayer.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var mediaController: MediaController? = null

    override fun onCreate() {
        super.onCreate()

        // Monochrome white music-note icon for the status bar / notification.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this)
                .apply { setSmallIcon(R.drawable.ic_launcher_foreground) }
        )

        val player = PlayerHolder.get(this)
        val sessionIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, sessionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pending)
            .build()

        // media3 only keeps the service foreground (and posts the media notification /
        // lock-screen controls) while a controller is connected. Hold one for the app's
        // lifetime so the notification reliably shows during playback.
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener(
            { runCatching { mediaController = future.get() } },
            ContextCompat.getMainExecutor(this)
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaController?.release()
        mediaController = null
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
