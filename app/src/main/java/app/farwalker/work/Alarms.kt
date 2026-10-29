package app.farwalker.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.farwalker.MainActivity
import app.farwalker.R
import app.farwalker.container
import app.farwalker.domain.News
import app.farwalker.domain.Note
import app.farwalker.localized
import app.farwalker.ru
import app.farwalker.ui.Words
import java.util.concurrent.TimeUnit

/** Every quarter of an hour: read the step counter, move the journey on, refresh the weather. */
class WalkWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        c.sync()
        runCatching { c.refreshWeather() }
        Notes.check(applicationContext)
        return Result.success()
    }
}

object Alarms {
    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<WalkWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("walk", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

/** News of the road, told once each: a new country, a new part of the world, a cape, a ship in port, the goal reached. */
object Notes {
    private const val CHANNEL = "road"
    private val TOLD = setOf(News.COUNTRY, News.CONTINENT, News.CAPE, News.ARRIVED, News.DOCKED, News.STATION)

    fun allowed(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Marks everything so far as told: the app is open and the traveller sees it. */
    suspend fun seen(context: Context) {
        val c = context.container
        val n = c.trip.state.value?.notes?.size ?: return
        if (c.settings.now().told != n) c.settings.update { it.copy(told = n) }
    }

    suspend fun check(context: Context) {
        val c = context.container
        val set = c.settings.now()
        val j = c.trip.state.value ?: return
        val fresh = j.notes.drop(set.told).filter { it.news in TOLD }
        if (fresh.isNotEmpty() && set.notify && allowed(context)) {
            val r = context.localized()
            // A ship docking in a new country is one piece of news, not three: the most telling goes.
            val best = fresh.maxBy { rank(it) }
            val w = Words(r, r.ru, c.world)
            show(context, w.noteTitle(best), w.note(best))
        }
        if (set.told != j.notes.size) c.settings.update { it.copy(told = j.notes.size) }
    }

    private fun rank(n: Note) = when (n.news) {
        News.CONTINENT -> 6
        News.CAPE -> 5
        News.ARRIVED -> 4
        News.COUNTRY -> 3
        News.DOCKED -> 2
        else -> 1
    }

    private fun show(context: Context, title: String, text: String) {
        val m = context.getSystemService(NotificationManager::class.java)
        if (m.getNotificationChannel(CHANNEL) == null) {
            m.createNotificationChannel(NotificationChannel(CHANNEL, context.localized().getString(R.string.channel_road), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(1, n)
    }
}
