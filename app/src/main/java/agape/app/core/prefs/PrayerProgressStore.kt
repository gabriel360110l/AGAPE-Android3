package agape.app.core.prefs

import agape.app.data.counter.PrayerCount
import android.content.Context

/**
 * Keeps where a prayer counter was when its screen is left, so coming back (or the app being closed in between)
 * continues where the prayer was: the prayer rope, the Rosary and the counters of the Agpeya alike. A count saved
 * with a day ([today], an epoch day) comes back on that day only; one saved without a day always does. Read and
 * written synchronously: a few short strings.
 */
class PrayerProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("agape_prayer_progress", Context.MODE_PRIVATE)

    fun count(key: String, today: Long? = null): PrayerCount? =
        text(key, today)?.let { PrayerCount.decode(it) }

    fun saveCount(key: String, count: PrayerCount, today: Long? = null) = saveText(key, count.encode(), today)

    /** A short text kept with a counter, such as the set of mysteries of the Rosary. */
    fun text(key: String, today: Long? = null): String? {
        if (today != null && prefs.getLong(dayKey(key), Long.MIN_VALUE) != today) return null
        return prefs.getString(key, null)
    }

    fun saveText(key: String, value: String, today: Long? = null) {
        val edit = prefs.edit().putString(key, value)
        if (today != null) edit.putLong(dayKey(key), today) else edit.remove(dayKey(key))
        edit.apply()
    }

    private fun dayKey(key: String) = "${key}_day"
}
