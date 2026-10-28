package agape.app.data.calendar

import agape.app.core.model.L10n
import agape.app.core.model.Tradition
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

/** How a day is kept, from no fast at all to the strictest fast. */
enum class FastLevel {
    /** An ordinary day: no fast is asked. */
    NONE,

    /** A season of joy in which even Wednesday and Friday are not kept as fast days. */
    FREE,

    /** No meat (the Catholic Friday, the Orthodox Cheesefare week). */
    NO_MEAT,

    /** A fast day. */
    FAST,

    /** A strict fast day. */
    STRICT,
}

/**
 * Fast or feast for one day in one tradition: a general guideline, not a rule of any parish.
 * [season] names why the day is (not) a fast day; [fish] marks a feast inside a fast on which
 * fish is allowed in the Byzantine practice.
 */
data class DayStatus(val level: FastLevel, val season: L10n?, val feast: Feast?, val fish: Boolean = false) {
    val isFast get() = level == FastLevel.FAST || level == FastLevel.STRICT || level == FastLevel.NO_MEAT

    /** One short line such as "Fast day · Great Lent" or "No fast day". */
    val label: L10n get() {
        val (en, nl) = when (level) {
            FastLevel.NONE -> "No fast day" to "Geen vastendag"
            FastLevel.FREE -> "Fast-free" to "Vastenvrij"
            FastLevel.NO_MEAT -> "No meat" to "Geen vlees"
            FastLevel.FAST -> "Fast day" to "Vastendag"
            FastLevel.STRICT -> "Strict fast" to "Strenge vasten"
        }
        val fishEn = if (fish) " · fish allowed" else ""
        val fishNl = if (fish) " · vis toegestaan" else ""
        return L10n(
            listOfNotNull(en, season?.en).joinToString(" · ") + fishEn,
            listOfNotNull(nl, season?.nl).joinToString(" · ") + fishNl,
        )
    }
}

object Fasting {
    private fun t(en: String, nl: String) = L10n(en, nl)

    private val WEDNESDAY = t("Wednesday", "Woensdag")
    private val FRIDAY = t("Friday", "Vrijdag")
    private val GREAT_LENT = t("Great Lent", "Grote Vasten")
    private val HOLY_WEEK = t("Holy Week", "Goede Week")
    private val NATIVITY_FAST = t("Nativity Fast", "Kerstvasten")
    private val APOSTLES_FAST = t("Apostles' Fast", "Apostelvasten")

    fun status(tradition: Tradition, date: LocalDate): DayStatus {
        val feast = LiturgicalCalendar.feasts(tradition, date).firstOrNull { it.rank != FeastRank.SEASON }
        val fromPascha = ChronoUnit.DAYS.between(ChurchCalendar.pascha(tradition, date.year), date).toInt()
        val (level, season) = when (tradition) {
            Tradition.EASTERN_ORTHODOX -> byzantine(date, fromPascha)
            Tradition.ORIENTAL_ORTHODOX -> coptic(date, fromPascha)
            Tradition.CATHOLIC -> latin(date, fromPascha)
        }
        val fish = tradition == Tradition.EASTERN_ORTHODOX && level == FastLevel.FAST && feast != null && feast.rank == FeastRank.GREAT
        return DayStatus(level, season, feast, fish)
    }

    private fun MonthDay.on(date: LocalDate) = month == date.month && dayOfMonth == date.dayOfMonth
    private fun md(month: Int, day: Int) = MonthDay.of(month, day)
    private fun LocalDate.between(from: MonthDay, to: MonthDay): Boolean {
        val d = MonthDay.from(this)
        return !d.isBefore(from) && !d.isAfter(to)
    }

    private fun weekly(date: LocalDate): Pair<FastLevel, L10n?> = when (date.dayOfWeek) {
        DayOfWeek.WEDNESDAY -> FastLevel.FAST to WEDNESDAY
        DayOfWeek.FRIDAY -> FastLevel.FAST to FRIDAY
        else -> FastLevel.NONE to null
    }

    /** Eastern Orthodox, fixed feasts on the new (Revised Julian) calendar as elsewhere in the app. */
    private fun byzantine(date: LocalDate, fromPascha: Int): Pair<FastLevel, L10n?> {
        val pentecost = 49
        return when {
            fromPascha == -2 || fromPascha == -1 -> FastLevel.STRICT to HOLY_WEEK
            fromPascha == -48 -> FastLevel.STRICT to GREAT_LENT
            fromPascha in -6..-3 -> FastLevel.FAST to HOLY_WEEK
            fromPascha in -47..-7 -> FastLevel.FAST to GREAT_LENT
            fromPascha in -55..-49 -> FastLevel.NO_MEAT to t("Cheesefare week", "Kaasweek")
            fromPascha in -69..-64 -> FastLevel.FREE to t("Week after the Publican and the Pharisee", "Week na de Tollenaar en de Farizeeër")
            fromPascha in 0..6 -> FastLevel.FREE to t("Bright Week", "Lichte Week")
            fromPascha in pentecost..pentecost + 6 -> FastLevel.FREE to t("Week after Pentecost", "Week na Pinksteren")
            date.between(md(12, 25), md(12, 31)) || date.between(md(1, 1), md(1, 4)) -> FastLevel.FREE to t("Twelve days of Christmas", "Twaalf dagen van Kerstmis")
            md(1, 5).on(date) -> FastLevel.STRICT to t("Eve of Theophany", "Vooravond van Theofanie")
            md(1, 6).on(date) -> FastLevel.FREE to null
            md(8, 29).on(date) -> FastLevel.STRICT to t("Beheading of John the Forerunner", "Onthoofding van Johannes de Voorloper")
            md(9, 14).on(date) -> FastLevel.STRICT to t("Exaltation of the Cross", "Kruisverheffing")
            date.between(md(11, 15), md(12, 24)) -> FastLevel.FAST to NATIVITY_FAST
            date.between(md(8, 1), md(8, 14)) -> FastLevel.FAST to t("Dormition Fast", "Vasten van de Moeder Gods")
            // From the Monday after All Saints (a week after Pentecost) to 28 June; some years it is short.
            fromPascha >= pentecost + 8 && date.between(md(5, 1), md(6, 28)) -> FastLevel.FAST to APOSTLES_FAST
            else -> weekly(date)
        }
    }

    /** Coptic and the other Oriental Orthodox churches, with the Coptic seasons. */
    private fun coptic(date: LocalDate, fromPascha: Int): Pair<FastLevel, L10n?> {
        val c = LiturgicalCalendar.coptic(date)
        return when {
            fromPascha == -2 -> FastLevel.STRICT to HOLY_WEEK
            fromPascha in -7..-1 -> FastLevel.FAST to HOLY_WEEK
            fromPascha in -55..-8 -> FastLevel.FAST to GREAT_LENT
            fromPascha in -69..-67 -> FastLevel.FAST to t("Fast of Nineveh", "Vasten van Nineve")
            fromPascha in 0..49 -> FastLevel.FREE to t("The Holy Fifty Days", "De Heilige Vijftig Dagen")
            // From the day after Pentecost to 4 Epip; the Feast of the Apostles is 5 Epip.
            fromPascha > 49 && (c.month in 9..10 || (c.month == 11 && c.day <= 4)) -> FastLevel.FAST to APOSTLES_FAST
            c.month == 12 && c.day in 1..15 -> FastLevel.FAST to t("Fast of St Mary", "Vasten van de heilige Maria")
            date.between(md(11, 25), md(12, 31)) || date.between(md(1, 1), md(1, 5)) -> FastLevel.FAST to NATIVITY_FAST
            md(1, 6).on(date) -> FastLevel.STRICT to t("Paramoun of the Nativity", "Paramoun van Kerstmis")
            md(1, 7).on(date) -> FastLevel.FREE to null
            md(1, 18).on(date) -> FastLevel.FAST to t("Paramoun of Theophany", "Paramoun van Theofanie")
            md(1, 19).on(date) -> FastLevel.FREE to null
            else -> weekly(date)
        }
    }

    /** Latin Catholic Church: the universal law (canons 1250–1252); bishops' conferences may adapt it. */
    private fun latin(date: LocalDate, fromPascha: Int): Pair<FastLevel, L10n?> {
        val lent = t("Lent", "Veertigdagentijd")
        return when {
            fromPascha == -46 -> FastLevel.STRICT to t("Ash Wednesday", "Aswoensdag")
            fromPascha == -2 -> FastLevel.STRICT to t("Good Friday", "Goede Vrijdag")
            fromPascha in 0..7 -> FastLevel.FREE to t("Octave of Easter", "Paasoctaaf")
            date.dayOfWeek == DayOfWeek.FRIDAY && solemnity(date, fromPascha) -> FastLevel.NONE to null
            date.dayOfWeek == DayOfWeek.FRIDAY -> FastLevel.NO_MEAT to if (fromPascha in -45..-1) lent else FRIDAY
            fromPascha in -45..-1 -> FastLevel.NONE to lent
            else -> FastLevel.NONE to null
        }
    }

    /** Solemnities that can fall on a Friday and lift its abstinence. */
    private fun solemnity(date: LocalDate, fromPascha: Int): Boolean =
        fromPascha == 68 || // Sacred Heart
            listOf(md(1, 1), md(1, 6), md(3, 19), md(3, 25), md(6, 24), md(6, 29), md(8, 15), md(11, 1), md(12, 8), md(12, 25)).any { it.on(date) }
}
