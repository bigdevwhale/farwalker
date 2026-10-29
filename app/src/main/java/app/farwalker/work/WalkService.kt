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
        val c = container
        if (job == null && c.sensor.allowed) {
            job = c.scope.launch {
                c.sensor.live().conflate().collect { v -> c.trip.reading(v); Notes.check(applicationContext); delay(5000) }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
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

        /** Called as the app goes out of sight, while it still may start a foreground service. */
        fun start(context: Context) {
            if (!context.container.sensor.allowed) return
            runCatching { context.startForegroundService(Intent(context, WalkService::class.java)) }
                .onFailure { Log.w(TAG, "start", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WalkService::class.java))
        }
    }
}
