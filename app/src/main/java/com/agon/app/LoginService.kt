package com.agon.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.agon.app.data.youtube.YouTubeAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service that polls Google's token endpoint for a device-code sign-in while the
 * user is approving it in their browser.
 *
 * Polling in a service (instead of a UI coroutine) keeps the app process alive and the poll
 * running while the browser is in the foreground, so the sign-in completes as soon as the user
 * approves it - without having to keep the app open in split-screen.
 */
class LoginService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceCode = intent?.getStringExtra(EXTRA_DEVICE_CODE)
        if (deviceCode == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        scope.launch {
            val auth = YouTubeAuth(applicationContext)
            // Bound the wait: the code itself expires, but cap it so the service can't run forever.
            val ok = withTimeoutOrNull(MAX_POLL_MS) { auth.pollForToken(deviceCode) } == true
            // Always clear the pending marker; the UI detects success via the stored tokens.
            auth.clearPendingLogin()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Sign-in", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Signing in to YouTube Music")
            .setContentText("Waiting for you to approve the sign-in")
            .setOngoing(true)
            .build()
    }

    companion object {
        const val EXTRA_DEVICE_CODE = "device_code"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "sign_in"
        const val MAX_POLL_MS = 15 * 60_000L
    }
}
