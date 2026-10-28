package agape.app.data.schedule

import java.time.Duration
import java.time.LocalTime

/**
 * The prayer hours on the old reckoning, where the day runs from sunrise to sunset in twelve "hours" whose length
 * changes with the season: the third hour is a quarter of the daylight after sunrise, the sixth is solar noon, the
 * ninth three quarters. Morning prayer is at sunrise, Matins and the Office of Readings at first light, Vespers at
 * sunset (in the Agpeya the eleventh hour, Compline the twelfth) and the night prayers an hour after dusk.
 * Hours this does not know, and every hour on days without sunrise or sunset, keep their fixed time.
 */
object SunSchedule {
    fun timeFor(hourId: String, sun: SunTimes, fixed: LocalTime, agpeya: Boolean): LocalTime {
        val rise = sun.sunrise ?: return fixed
        val set = sun.sunset ?: return fixed
        val day = Duration.between(rise, set).takeIf { !it.isNegative && !it.isZero } ?: return fixed
        fun hour(n: Int): LocalTime = rise.plus(day.multipliedBy(n.toLong()).dividedBy(12))
        val dusk = sun.dusk ?: set.plusMinutes(40)
        return when (hourId) {
            "matins", "readings" -> sun.dawn ?: rise.minusMinutes(40)
            "morning", "first", "prime", "lauds" -> rise
            "third", "terce" -> hour(3)
            "sixth", "sext" -> sun.noon
            "ninth", "none" -> hour(9)
            "vespers" -> if (agpeya) hour(11) else set
            "compline" -> if (agpeya) set else dusk.plusHours(1)
            "veil" -> dusk.plusHours(1)
            "evening" -> dusk.plusHours(2)
            else -> fixed
        }.withSecond(0).withNano(0)
    }
}
