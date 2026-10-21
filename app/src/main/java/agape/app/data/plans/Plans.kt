package agape.app.data.plans

import agape.app.core.model.AppLanguage
import agape.app.core.model.Tradition
import agape.app.data.content.ContentRepository
import agape.app.data.content.PlanDefinition
import agape.app.data.content.PrayerHour
import agape.app.data.content.chapterCount
import java.time.LocalTime

/** The concrete days of [plan] for this tradition's canon, in the translation of [language]. */
fun ContentRepository.planDays(plan: PlanDefinition, tradition: Tradition, language: AppLanguage): List<PlanDay> =
    PlanEngine.days(
        plan,
        canon(tradition, language).map { CanonBook(it.id, it.testament, it.chapterCount(language)) },
        councils(tradition),
    )

/** The quiz guarding a day, if one exists. */
fun ContentRepository.quizKeyFor(plan: PlanDefinition, day: PlanDay): String? =
    PlanEngine.quizKey(plan, day) { quiz(it) != null }

object PrayerClock {
    /** The hour whose time has most recently begun; before the first hour of the day, the last hour of the night. */
    fun current(hours: List<PrayerHour>, now: LocalTime): PrayerHour? {
        if (hours.isEmpty()) return null
        val sorted = hours.sortedBy { LocalTime.parse(it.time) }
        return sorted.lastOrNull { !LocalTime.parse(it.time).isAfter(now) } ?: sorted.last()
    }

    fun next(hours: List<PrayerHour>, now: LocalTime): PrayerHour? {
        val sorted = hours.sortedBy { LocalTime.parse(it.time) }
        return sorted.firstOrNull { LocalTime.parse(it.time).isAfter(now) } ?: sorted.firstOrNull()
    }
}
