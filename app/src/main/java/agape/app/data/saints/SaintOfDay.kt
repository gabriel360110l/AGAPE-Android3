package agape.app.data.saints

import agape.app.core.model.AppLanguage
import agape.app.core.model.Tradition
import agape.app.data.calendar.FeastRank
import agape.app.data.calendar.LiturgicalCalendar
import agape.app.data.content.ContentRepository
import agape.app.data.content.IconEntry
import java.time.LocalDate

/** The saint or feast of a day, with an icon when one is bundled. */
data class SaintOfDay(
    val name: String,
    val others: List<String>,
    val summary: String,
    val icon: IconEntry?,
    /** A great feast of the Lord or the Mother of God rather than a saint. */
    val isFeast: Boolean,
)

/**
 * Picks the commemoration of a day from the bundled calendars, so it works offline in all three traditions:
 * the Byzantine menologion, the Coptic Synaxarium (by Coptic date) and the General Roman Calendar. A great feast
 * from the liturgical calendar (Pascha, Pentecost, …) takes the place of the saint on its day.
 */
class SaintsRepository(private val content: ContentRepository) {

    fun forDate(tradition: Tradition, language: AppLanguage, date: LocalDate): SaintOfDay? {
        val greatFeast = LiturgicalCalendar.feasts(tradition, date).firstOrNull { it.rank == FeastRank.GREAT }
        val names: List<Pair<String, String>> // English, shown language
        var summary = ""
        when (tradition) {
            Tradition.EASTERN_ORTHODOX -> {
                val entry = content.saintEo(date) ?: return null
                val en = entry.title.en.split(" · ")
                val shown = entry.title[language].split(" · ")
                names = en.mapIndexed { i, e -> e to (shown.getOrNull(i) ?: e) }
                // The English summaries of the menologion are only a placeholder sentence; show the real ones.
                summary = entry.summary[language].takeUnless { it.startsWith("The Church commemorates") }.orEmpty()
            }
            Tradition.ORIENTAL_ORTHODOX -> {
                val coptic = LiturgicalCalendar.coptic(date)
                names = content.saintsOo(coptic.month, coptic.day).map { it.title.en to it.title[language] }
            }
            Tradition.CATHOLIC -> names = content.saintsCa(date).map { it.title.en to it.title[language] }
        }
        if (greatFeast != null) {
            val icon = content.iconFor(listOf(greatFeast.name.en))
            return SaintOfDay(greatFeast.name[language], names.map { it.second }, summary, icon, isFeast = true)
        }
        if (names.isEmpty()) return null
        return SaintOfDay(
            name = names.first().second,
            others = names.drop(1).map { it.second },
            summary = summary,
            icon = content.iconFor(names.map { it.first }),
            isFeast = false,
        )
    }
}
