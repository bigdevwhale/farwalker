package app.farwalker.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.farwalker.MainActivity
import app.farwalker.R
import app.farwalker.container
import app.farwalker.localized
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * Keeps counting while the app is out of sight: locked screen, home button or another app. Android
 * hands step counts only to an app the user can see or one running a foreground service, so the app
 * becomes one for that time, with a quiet notification, and stops as soon as it is open again.
 * Swiping the app away from recents stops it too; the steps are then caught up on the next open.
 */
class WalkService : Service() {
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ok = runCatching {
            ServiceCompat.startForeground(this, ID, note(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        }.onFailure { Log.w(TAG, "startForeground", it) }.isSuccess
        if (!ok) { stopSelf(); return START_NOT_STICKY }
        // Asked to stop: startForeground above has already answered the start that may be waiting on it.
        if (intent?.action == STOP) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val c = container
        if (job == null && c.sensor.allowed) {
            job = c.scope.launch {
                // The widget follows the walk: at once with news of the road, and with the steps now and then.
                var drawnAt = 0L
                c.sensor.live().conflate().collect { v ->
                    val news = c.trip.reading(v)
                    Notes.check(applicationContext)
                    val now = System.currentTimeMillis()
                    if (news.isNotEmpty() || now - drawnAt >= WIDGET_EVERY_MS) {
                        runCatching { RoadWidget.updateAll(applicationContext) }.onFailure { Log.w(TAG, "widget", it) }
                        drawnAt = now
                    }
                    delay(5000)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        container.write { RoadWidget.updateAll(app) }
        super.onDestroy()
    }

    private fun note(): android.app.Notification {
        val r = localized()
        val m = getSystemService(NotificationManager::class.java)
        if (m.getNotificationChannel(CHANNEL) == null) {
            m.createNotificationChannel(NotificationChannel(CHANNEL, r.getString(R.string.channel_walk), NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(r.getString(R.string.walk_title))
            .setContentText(r.getString(R.string.walk_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val TAG = "WalkService"
        private const val CHANNEL = "walk"
        private const val ID = 10
        private const val STOP = "stop"
        /** The widget is redrawn with new steps at most this often while walking. */
        private const val WIDGET_EVERY_MS = 15_000L

        /** Started and not yet asked to stop. */
        @Volatile private var started = false

        /** Called as the app goes out of sight, while it still may start a foreground service. */
        fun start(context: Context) {
            if (!context.container.sensor.allowed) return
            runCatching { context.startForegroundService(Intent(context, WalkService::class.java)) }
                .onSuccess { started = true }
                .onFailure { Log.w(TAG, "start", it) }
        }

        /**
         * Called as the app comes back. A service started as a foreground one must reach startForeground
         * even if the app is back a moment later (a permission dialog, the notification shade), or Android
         * kills the app (ForegroundServiceDidNotStartInTimeException) and nothing counts until it is opened
         * again; stopping it outright could come first. So it is asked to stop, and does so in turn.
         */
        fun stop(context: Context) {
            if (!started) return
            started = false
            runCatching { context.startService(Intent(context, WalkService::class.java).setAction(STOP)) }
                .onFailure { Log.w(TAG, "stop", it); context.stopService(Intent(context, WalkService::class.java)) }
        }
    }
}
