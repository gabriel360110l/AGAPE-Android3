package agape.app.notifications

import agape.app.AgapeApp
import agape.app.MainActivity
import agape.app.R
import agape.app.launchSafely
import agape.app.core.model.Tradition
import agape.app.core.prefs.CustomReminder
import agape.app.core.prefs.Settings
import agape.app.data.content.PrayerHour
import agape.app.data.schedule.SunSchedule
import agape.app.data.schedule.SunTimes
import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
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
    /** Prayer reminders ring the church bell; a channel's sound is fixed once made, hence the new id. */
    const val CHANNEL_PRAYER = "prayer_bell"
    private const val OLD_CHANNEL_PRAYER = "prayer_hours"
    const val CHANNEL_REMINDERS = "gentle_reminders"
    private const val INACTIVITY_WORK = "inactivity"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(OLD_CHANNEL_PRAYER)
        val bell = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PRAYER, context.getString(R.string.channel_prayer), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_prayer_desc)
                setSound(bellUri(context), bell)
                enableVibration(true)
                vibrationPattern = BELL_VIBRATION
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.channel_reminders_desc) },
        )
    }

    private val BELL_VIBRATION = longArrayOf(0L, 400L, 250L, 400L)

    private fun bellUri(context: Context): Uri = Uri.parse("android.resource://${context.packageName}/${R.raw.church_bell}")

    /** A reminder right now, so the bell and its volume can be tried out. */
    fun test(context: Context) {
        createChannels(context)
        val res = (context.applicationContext as AgapeApp).container.localized(context)
        post(context, CHANNEL_PRAYER, 7777, res.getString(R.string.notif_test_title), res.getString(R.string.notif_test_text), "agape://prayer")
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
            // Before Android 8 there are no channels: the bell and vibration are set on each prayer reminder.
            .apply { if (channel == CHANNEL_PRAYER) setSound(bellUri(context)).setVibrate(BELL_VIBRATION).setPriority(NotificationCompat.PRIORITY_HIGH) }
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, n)
    }

    /** Re-plans every prayer-hour alarm, the own prayer moments and the inactivity check from current settings. */
    suspend fun reschedule(context: Context) {
        val app = context.applicationContext as AgapeApp
        val settings = app.container.settings.current()
        val alarms = context.getSystemService(AlarmManager::class.java)
        // Cancel everything we may have set before; request codes are derived from the hour or moment id.
        app.container.content.prayerBookHourIds().forEach { alarms.cancel(alarmIntent(context, it, null)) }
        val custom = settings.customReminders.mapNotNull { CustomReminder.decode(it) }
        (scheduledCustom(context) + custom.map { it.id }).forEach { alarms.cancel(customIntent(context, it, null)) }
        rememberScheduledCustom(context, if (settings.prayerNotifications) custom.map { it.id }.toSet() else emptySet())
        if (settings.prayerNotifications) {
            settings.hourReminders.mapNotNull { HourReminder.decode(it) }.forEach { schedule(context, it, settings) }
            custom.forEach { scheduleCustom(context, it) }
        }
        val work = WorkManager.getInstance(context)
        if (settings.inactivityReminders) {
            work.enqueueUniquePeriodicWork(
                INACTIVITY_WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<InactivityWorker>(1, TimeUnit.DAYS).build(),
            )
        } else work.cancelUniqueWork(INACTIVITY_WORK)
    }

    /** The next time [reminder] rings: its own time, or the hour's place in the day of the sun when that is chosen. */
    fun schedule(context: Context, reminder: HourReminder, settings: Settings) {
        val zone = ZoneId.systemDefault()
        val place = settings.place?.takeIf { settings.sunTimes }
        fun on(date: LocalDate) = date.atTime(
            if (place == null) reminder.time
            else SunSchedule.timeFor(reminder.hourId, SunTimes.of(date, place.latitude, place.longitude, zone), reminder.time, settings.tradition == Tradition.ORIENTAL_ORTHODOX),
        )
        var next = on(LocalDate.now())
        if (!next.isAfter(LocalDateTime.now())) next = on(LocalDate.now().plusDays(1))
        setAlarm(context, next, alarmIntent(context, reminder.hourId, reminder))
    }

    fun scheduleCustom(context: Context, reminder: CustomReminder) {
        var next = LocalDate.now().atTime(reminder.time)
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        setAlarm(context, next, customIntent(context, reminder.id, reminder))
    }

    private fun setAlarm(context: Context, at: LocalDateTime, pending: PendingIntent) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        else alarms.setWindow(AlarmManager.RTC_WAKEUP, millis, 5 * 60_000L, pending)
    }

    private fun customIntent(context: Context, id: String, reminder: CustomReminder?): PendingIntent {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).setAction("agape.PRAYER_MOMENT.$id")
        reminder?.let { intent.putExtra(EXTRA_CUSTOM, it.encode()) }
        return PendingIntent.getBroadcast(context, "moment:$id".hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // Deleted moments are no longer in settings, so the ids that have an alarm are kept to cancel them later.
    private fun scheduledCustom(context: Context): Set<String> =
        context.getSharedPreferences("agape_alarms", Context.MODE_PRIVATE).getStringSet("moments", emptySet()).orEmpty()

    private fun rememberScheduledCustom(context: Context, ids: Set<String>) =
        context.getSharedPreferences("agape_alarms", Context.MODE_PRIVATE).edit().putStringSet("moments", ids).apply()

    private fun alarmIntent(context: Context, hourId: String, reminder: HourReminder?): PendingIntent {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).setAction("agape.PRAYER_HOUR.$hourId")
        reminder?.let { intent.putExtra(EXTRA_REMINDER, it.encode()) }
        return PendingIntent.getBroadcast(context, hourId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    const val EXTRA_REMINDER = "reminder"
    const val EXTRA_CUSTOM = "moment"
}

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminder = intent.getStringExtra(Notifications.EXTRA_REMINDER)?.let { HourReminder.decode(it) }
        val moment = intent.getStringExtra(Notifications.EXTRA_CUSTOM)?.let { CustomReminder.decode(it) }
        if (reminder == null && moment == null) return
        val pending = goAsync()
        val app = context.applicationContext as AgapeApp
        app.container.appScope.launchSafely {
            try {
                val settings = app.container.settings.current()
                val lang = app.container.language()
                val res = app.container.localized(context)
                val hour = reminder?.let { r -> settings.tradition?.let { app.container.content.prayerBook(it).hours.firstOrNull { h -> h.id == r.hourId } } }
                if (settings.prayerNotifications && reminder != null && hour != null) {
                    Notifications.post(
                        context, Notifications.CHANNEL_PRAYER, reminder.hourId.hashCode(),
                        res.getString(R.string.notif_hour_title, hour.name[lang]),
                        hour.theme[lang].ifBlank { res.getString(R.string.notif_hour_text) },
                        "agape://prayer/${hour.id}",
                    )
                    Notifications.schedule(context, reminder, settings)
                }
                if (settings.prayerNotifications && moment != null) {
                    Notifications.post(
                        context, Notifications.CHANNEL_PRAYER, "moment:${moment.id}".hashCode(),
                        moment.label.ifBlank { res.getString(R.string.moment_default) },
                        res.getString(R.string.notif_hour_text),
                        "agape://prayer",
                    )
                    Notifications.scheduleCustom(context, moment)
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
