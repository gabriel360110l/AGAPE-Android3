package agape.app.notifications

import agape.app.AgapeApp
import agape.app.MainActivity
import agape.app.R
import agape.app.launchSafely
import agape.app.data.content.PrayerHour
import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** A reminder for one prayer hour, stored in settings as `hourId@HH:mm`. */
data class HourReminder(val hourId: String, val time: LocalTime) {
    fun encode(): String = "$hourId@%02d:%02d".format(time.hour, time.minute)

    companion object {
        fun decode(value: String): HourReminder? {
            val (id, t) = value.split('@').takeIf { it.size == 2 } ?: return null
            return runCatching { HourReminder(id, LocalTime.parse(t)) }.getOrNull()
        }

        /** A gentle default: one morning hour, Vespers and the hour before sleep. */
        fun defaults(hours: List<PrayerHour>): Set<String> {
            val morning = hours.firstOrNull { LocalTime.parse(it.time).hour in 6..7 }
            val evening = hours.firstOrNull { it.id == "vespers" }
            val night = hours.firstOrNull { it.id == "compline" }
            return listOfNotNull(morning, evening, night).map { HourReminder(it.id, LocalTime.parse(it.time)).encode() }.toSet()
        }
    }
}

object Notifications {
    const val CHANNEL_PRAYER = "prayer_hours"
    const val CHANNEL_REMINDERS = "gentle_reminders"
    private const val INACTIVITY_WORK = "inactivity"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PRAYER, context.getString(R.string.channel_prayer), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_prayer_desc) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.channel_reminders_desc) },
        )
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun post(context: Context, channel: String, id: Int, title: String, text: String, deepLink: String) {
        if (!canPost(context)) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink), context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, n)
    }

    /** Re-plans every prayer-hour alarm and the inactivity check from current settings. */
    suspend fun reschedule(context: Context) {
        val app = context.applicationContext as AgapeApp
        val settings = app.container.settings.current()
        val alarms = context.getSystemService(AlarmManager::class.java)
        // Cancel everything we may have set before; request codes are derived from the hour id.
        app.container.content.prayerBookHourIds().forEach { alarms.cancel(alarmIntent(context, it, null)) }
        if (settings.prayerNotifications) {
            settings.hourReminders.mapNotNull { HourReminder.decode(it) }.forEach { schedule(context, it) }
        }
        val work = WorkManager.getInstance(context)
        if (settings.inactivityReminders) {
            work.enqueueUniquePeriodicWork(
                INACTIVITY_WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<InactivityWorker>(1, TimeUnit.DAYS).build(),
            )
        } else work.cancelUniqueWork(INACTIVITY_WORK)
    }

    fun schedule(context: Context, reminder: HourReminder) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()
        var next = LocalDate.now().atTime(reminder.time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pending = alarmIntent(context, reminder.hourId, reminder)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        else alarms.setWindow(AlarmManager.RTC_WAKEUP, at, 5 * 60_000L, pending)
    }

    private fun alarmIntent(context: Context, hourId: String, reminder: HourReminder?): PendingIntent {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).setAction("agape.PRAYER_HOUR.$hourId")
        reminder?.let { intent.putExtra(EXTRA_REMINDER, it.encode()) }
        return PendingIntent.getBroadcast(context, hourId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    const val EXTRA_REMINDER = "reminder"
}

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminder = intent.getStringExtra(Notifications.EXTRA_REMINDER)?.let { HourReminder.decode(it) } ?: return
        val pending = goAsync()
        val app = context.applicationContext as AgapeApp
        app.container.appScope.launchSafely {
            try {
                val settings = app.container.settings.current()
                val tradition = settings.tradition
                val hour = tradition?.let { app.container.content.prayerBook(it).hours.firstOrNull { h -> h.id == reminder.hourId } }
                if (settings.prayerNotifications && hour != null) {
                    val lang = app.container.language()
                    val res = app.container.localized(context)
                    Notifications.post(
                        context, Notifications.CHANNEL_PRAYER, reminder.hourId.hashCode(),
                        res.getString(R.string.notif_hour_title, hour.name[lang]),
                        hour.theme[lang].ifBlank { res.getString(R.string.notif_hour_text) },
                        "agape://prayer/${hour.id}",
                    )
                    Notifications.schedule(context, reminder)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) return
        val pending = goAsync()
        val app = context.applicationContext as AgapeApp
        app.container.appScope.launchSafely {
            try { Notifications.reschedule(context) } finally { pending.finish() }
        }
    }
}

/** Once a day: if the app has not been opened for a few days, send one quiet invitation. */
class InactivityWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AgapeApp
        val settings = app.container.settings
        if (!settings.settings.first().inactivityReminders) return Result.success()
        val idleDays = (System.currentTimeMillis() - settings.lastOpenedAt()) / TimeUnit.DAYS.toMillis(1)
        if (idleDays >= INACTIVE_AFTER_DAYS) {
            val res = app.container.localized(applicationContext)
            Notifications.post(
                applicationContext, Notifications.CHANNEL_REMINDERS, 4242,
                res.getString(R.string.notif_inactive_title),
                res.getString(R.string.notif_inactive_text),
                "agape://home",
            )
        }
        return Result.success()
    }

    private companion object {
        const val INACTIVE_AFTER_DAYS = 3
    }
}
