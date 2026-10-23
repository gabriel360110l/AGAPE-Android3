package agape.app.data.saints

import agape.app.core.model.L10n
import agape.app.core.model.Tradition
import agape.app.data.calendar.CopticDate
import agape.app.data.calendar.LiturgicalCalendar
import agape.app.data.content.IconEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * The uniform saints dataset of the three traditions (schema agape-saints/1, see tools/schema/README.md), bundled as
 * `assets/content/saints_unified.json` by tools/build_unified_saints.py. Fields the app does not use (sources, refs,
 * provenance) are skipped when parsing.
 */
@Serializable
data class UnifiedSaints(
    val icons: List<UnifiedIcon> = emptyList(),
    val commemorations: List<Commemoration> = emptyList(),
    /** Readings that follow the lectionary rather than a saint: tradition id → civil date (YYYY-MM-DD) → readings. */
    @SerialName("dated_readings") val datedReadings: Map<String, Map<String, List<SaintReading>>> = emptyMap(),
)

@Serializable
data class UnifiedIcon(
    val key: String,
    val asset: String,
    val page: String,
    val title: String,
    val artist: String = "",
    val licence: String,
    val fallback: Boolean = false,
) {
    fun toEntry() = IconEntry(key = key, file = asset, match = emptyList(), title = title, artist = artist, licence = licence, source = page, fallback = fallback)
}

/** A fixed date in the tradition's own calendar: `church` (menologion), `gregorian` or `coptic` (month 13 is Nasie). */
@Serializable
data class DateRule(val calendar: String, val month: Int, val day: Int)

/** 1 great feast / solemnity, 2 feast, 3 memorial / saint, 4 optional memorial, 5 commemoration. */
@Serializable
data class SaintRank(val code: String, val level: Int)

/** The life or a short summary; `scope` is `day` when the text speaks of the whole day (the menologion). */
@Serializable
data class SaintLife(val text: L10n, val scope: String = "commemoration", val url: String? = null)

@Serializable
data class IconLink(val key: String, val match: String)

/** A reading: `display` as the source writes it, `ref` in the app's `BOOK C:V-V` notation when it could be read. */
@Serializable
data class SaintReading(val role: String, val display: String, val ref: String? = null, val service: String? = null)

@Serializable
data class Commemoration(
    val id: String,
    val tradition: String,
    /** `feast`, `event` or `saint`. */
    val kind: String = "saint",
    val date: DateRule,
    val order: Int = 0,
    val rank: SaintRank? = null,
    val name: L10n,
    val life: SaintLife? = null,
    val icon: IconLink? = null,
    val readings: List<SaintReading> = emptyList(),
)

/** One commemoration of a resolved day, with its icon looked up. [ownIcon] is false for a general (fallback) icon. */
data class DayCommemoration(val entry: Commemoration, val icon: IconEntry?, val ownIcon: Boolean)

/** A civil date in one tradition: the date in all three calendars, the commemorations in order, and the readings. */
data class SaintDayView(
    val tradition: Tradition,
    val gregorian: LocalDate,
    /** The same day in the Julian calendar (old style). */
    val julian: LocalDate,
    val coptic: CopticDate,
    val commemorations: List<DayCommemoration>,
    val readings: List<SaintReading>,
) {
    /** The principal commemoration's own icon, else the first own icon of the day, else a general one. */
    val icon: IconEntry? get() =
        commemorations.firstOrNull { it.ownIcon }?.icon ?: commemorations.firstNotNullOfOrNull { it.icon }
}

/** Looks up the commemorations of a civil date, the same way tools/build_unified_saints.py resolves a day. */
class SaintCalendar(private val data: UnifiedSaints) {
    private val icons = data.icons.associateBy { it.key }
    private val byDay = data.commemorations.groupBy { key(it.tradition, it.date.calendar, it.date.month, it.date.day) }

    /**
     * [oldCalendar] reads the Eastern Orthodox menologion on the Julian calendar (13 days later in the civil calendar)
     * instead of the new (Revised Julian) calendar.
     */
    fun day(tradition: Tradition, date: LocalDate, oldCalendar: Boolean = false): SaintDayView {
        val id = id(tradition)
        val julian = LiturgicalCalendar.julian(date)
        val coptic = LiturgicalCalendar.coptic(date)
        val k = when (tradition) {
            Tradition.ORIENTAL_ORTHODOX -> key(id, "coptic", coptic.month, coptic.day)
            Tradition.EASTERN_ORTHODOX -> if (oldCalendar) key(id, "church", julian.monthValue, julian.dayOfMonth)
                else key(id, "church", date.monthValue, date.dayOfMonth)
            Tradition.CATHOLIC -> key(id, "gregorian", date.monthValue, date.dayOfMonth)
        }
        val entries = byDay[k].orEmpty().let { list ->
            if (tradition == Tradition.CATHOLIC) list.sortedWith(compareBy({ it.rank?.level ?: 9 }, { it.order })) else list.sortedBy { it.order }
        }
        val commemorations = entries.map { c ->
            val icon = c.icon?.let { icons[it.key] }
            DayCommemoration(c, icon?.toEntry(), ownIcon = icon != null && c.icon?.match == "name")
        }
        val readings = data.datedReadings[id]?.get(date.toString()).orEmpty() + entries.flatMap { it.readings }
        return SaintDayView(tradition, date, julian, coptic, commemorations, readings)
    }

    private fun key(tradition: String, calendar: String, month: Int, day: Int) = "$tradition/$calendar/$month/$day"

    companion object {
        /** The tradition ids of the schema; the Coptic Church stands for the Oriental Orthodox in the app. */
        fun id(tradition: Tradition): String = when (tradition) {
            Tradition.EASTERN_ORTHODOX -> "eastern_orthodox"
            Tradition.ORIENTAL_ORTHODOX -> "coptic_orthodox"
            Tradition.CATHOLIC -> "catholic"
        }
    }
}
