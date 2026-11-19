package agape.app.core.prefs

import agape.app.data.rosary.PrayerRope
import agape.app.data.rosary.RopeCount
import agape.app.data.rosary.RosaryProgress
import android.content.Context

/**
 * Keeps the count of the Rosary and the prayer rope when the screen is left, so coming back (or the app being
 * closed in between) continues where the prayer was. Read and written synchronously: a few short strings.
 */
class PrayerProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("agape_prayer_progress", Context.MODE_PRIVATE)

    fun rosary(): RosaryProgress? = RosaryProgress.decode(prefs.getString(ROSARY, null))
    fun saveRosary(progress: RosaryProgress) = prefs.edit().putString(ROSARY, progress.encode()).apply()

    fun rope(rope: PrayerRope): RopeCount? =
        RopeCount.decode(prefs.getString(ropeKey(rope), null))?.takeIf { it.size in rope.sizes }
    fun saveRope(rope: PrayerRope, count: RopeCount) = prefs.edit().putString(ropeKey(rope), count.encode()).apply()

    private fun ropeKey(rope: PrayerRope) = "rope_${rope.name.lowercase()}"

    private companion object {
        const val ROSARY = "rosary"
    }
}
