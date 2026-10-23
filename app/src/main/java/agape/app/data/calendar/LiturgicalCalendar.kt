package agape.app.data.calendar

import agape.app.core.model.L10n
import agape.app.core.model.Tradition
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.JulianFields

/** A date in the Coptic calendar (Anno Martyrum). */
data class CopticDate(val year: Int, val month: Int, val day: Int) {
    fun monthName(dutch: Boolean): String = (if (dutch) MONTHS_NL else MONTHS_EN)[month - 1]

    companion object {
        val MONTHS_EN = listOf("Thout", "Paopi", "Hathor", "Koiak", "Tobi", "Meshir", "Paremhat", "Parmouti", "Pashons", "Paoni", "Epip", "Mesori", "Nasie")
        val MONTHS_NL = listOf("Thoot", "Paopi", "Hathor", "Kiahk", "Tobi", "Amshir", "Baramhat", "Baramoeda", "Bashans", "Paoni", "Epip", "Mesra", "Nasie")
    }
}

enum class FeastRank { GREAT, MAJOR, SEASON }

data class Feast(val name: L10n, val rank: FeastRank)

/** Calendar arithmetic and the principal feasts of each tradition, all offline. */
object LiturgicalCalendar {
    /** Julian Day Number of 1 Thout, year 1 of the Martyrs (29 August 284, Julian). */
    private const val COPTIC_EPOCH = 1825030L

    fun coptic(date: LocalDate): CopticDate {
        val jdn = date.getLong(JulianFields.JULIAN_DAY)
        val year = ((4 * (jdn - COPTIC_EPOCH) + 1463) / 1461).toInt()
        val startOfYear = COPTIC_EPOCH + 365L * (year - 1) + (year / 4)
        val dayOfYear = (jdn - startOfYear).toInt()
        return CopticDate(year, dayOfYear / 30 + 1, dayOfYear % 30 + 1)
    }

    /** The Julian ("old") calendar date shown alongside the civil date. */
    fun julian(date: LocalDate): LocalDate = date.minusDays(ChurchCalendar.JULIAN_OFFSET)

    private fun t(en: String, nl: String) = L10n(en, nl)

    fun feasts(tradition: Tradition, date: LocalDate): List<Feast> = buildList {
        val pascha = ChurchCalendar.pascha(tradition, date.year)
        val fromPascha = ChronoUnit.DAYS.between(pascha, date).toInt()
        when (tradition) {
            Tradition.EASTERN_ORTHODOX -> {
                when (fromPascha) {
                    -48 -> add(Feast(t("Clean Monday · Great Lent begins", "Schone Maandag · begin van de Grote Vasten"), FeastRank.SEASON))
                    -7 -> add(Feast(t("Palm Sunday", "Palmzondag"), FeastRank.GREAT))
                    -2 -> add(Feast(t("Great and Holy Friday", "Grote en Heilige Vrijdag"), FeastRank.GREAT))
                    0 -> add(Feast(t("Pascha · The Resurrection of the Lord", "Pascha · De Verrijzenis van de Heer"), FeastRank.GREAT))
                    39 -> add(Feast(t("Ascension", "Hemelvaart"), FeastRank.GREAT))
                    49 -> add(Feast(t("Pentecost", "Pinksteren"), FeastRank.GREAT))
                }
                fixed(date, listOf(
                    1 to 6 to t("Theophany", "Theofanie"), 2 to 2 to t("Meeting of the Lord", "Ontmoeting van de Heer"),
                    3 to 25 to t("Annunciation", "Annunciatie"), 8 to 6 to t("Transfiguration", "Gedaanteverandering"),
                    8 to 15 to t("Dormition of the Theotokos", "Ontslapen van de Moeder Gods"), 9 to 8 to t("Nativity of the Theotokos", "Geboorte van de Moeder Gods"),
                    9 to 14 to t("Exaltation of the Cross", "Kruisverheffing"), 11 to 21 to t("Entry of the Theotokos", "Intrede van de Moeder Gods"),
                    12 to 25 to t("Nativity of Christ", "Geboorte van Christus"),
                )).forEach { add(Feast(it, FeastRank.GREAT)) }
                if (date.monthValue == 11 && date.dayOfMonth == 15) add(Feast(t("Nativity Fast begins", "Begin van de Kerstvasten"), FeastRank.SEASON))
            }
            Tradition.ORIENTAL_ORTHODOX -> {
                when (fromPascha) {
                    -69 -> add(Feast(t("Fast of Jonah (Nineveh) begins", "Vasten van Jona (Nineve) begint"), FeastRank.SEASON))
                    -55 -> add(Feast(t("Great Lent begins", "De Grote Vasten begint"), FeastRank.SEASON))
                    -7 -> add(Feast(t("Palm Sunday", "Palmzondag"), FeastRank.GREAT))
                    -2 -> add(Feast(t("Good Friday", "Goede Vrijdag"), FeastRank.GREAT))
                    0 -> add(Feast(t("Feast of the Resurrection", "Feest van de Verrijzenis"), FeastRank.GREAT))
                    39 -> add(Feast(t("Ascension", "Hemelvaart"), FeastRank.GREAT))
                    49 -> add(Feast(t("Pentecost", "Pinksteren"), FeastRank.GREAT))
                }
                val c = coptic(date)
                listOf(
                    (1 to 1) to Feast(t("Nayrouz · Coptic New Year", "Nayrouz · Koptisch Nieuwjaar"), FeastRank.MAJOR),
                    (1 to 17) to Feast(t("Feast of the Cross", "Feest van het Kruis"), FeastRank.MAJOR),
                    (5 to 11) to Feast(t("Theophany", "Theofanie"), FeastRank.GREAT),
                    (7 to 29) to Feast(t("Annunciation", "Annunciatie"), FeastRank.GREAT),
                    (9 to 24) to Feast(t("Entry of the Lord into Egypt", "Intocht van de Heer in Egypte"), FeastRank.MAJOR),
                    (11 to 5) to Feast(t("Feast of the Apostles", "Feest van de Apostelen"), FeastRank.MAJOR),
                    (12 to 16) to Feast(t("Assumption of St Mary", "Opneming van de heilige Maria"), FeastRank.MAJOR),
                ).forEach { (md, feast) -> if (c.month == md.first && c.day == md.second) add(feast) }
                // 29 Kiahk, but kept on 7 January (28 Kiahk) in the years after a Coptic leap year.
                if (date.monthValue == 1 && date.dayOfMonth == 7) add(Feast(t("Nativity of Christ", "Geboorte van Christus"), FeastRank.GREAT))
            }
            Tradition.CATHOLIC -> {
                when (fromPascha) {
                    -46 -> add(Feast(t("Ash Wednesday", "Aswoensdag"), FeastRank.SEASON))
                    -7 -> add(Feast(t("Palm Sunday", "Palmzondag"), FeastRank.GREAT))
                    -3 -> add(Feast(t("Holy Thursday", "Witte Donderdag"), FeastRank.GREAT))
                    -2 -> add(Feast(t("Good Friday", "Goede Vrijdag"), FeastRank.GREAT))
                    0 -> add(Feast(t("Easter Sunday", "Pasen"), FeastRank.GREAT))
                    39 -> add(Feast(t("Ascension", "Hemelvaart"), FeastRank.GREAT))
                    49 -> add(Feast(t("Pentecost", "Pinksteren"), FeastRank.GREAT))
                    56 -> add(Feast(t("Holy Trinity", "Heilige Drie-eenheid"), FeastRank.MAJOR))
                    60 -> add(Feast(t("Corpus Christi", "Sacramentsdag"), FeastRank.MAJOR))
                    68 -> add(Feast(t("Sacred Heart", "Heilig Hart"), FeastRank.MAJOR))
                }
                val advent = advent(date.year)
                if (date == advent) add(Feast(t("First Sunday of Advent", "Eerste zondag van de Advent"), FeastRank.SEASON))
                if (date == advent.minusDays(7)) add(Feast(t("Christ the King", "Christus Koning"), FeastRank.MAJOR))
                fixed(date, listOf(
                    1 to 1 to t("Mary, Mother of God", "Maria, Moeder van God"), 1 to 6 to t("Epiphany", "Driekoningen"),
                    2 to 2 to t("Presentation of the Lord", "Opdracht van de Heer"), 3 to 19 to t("St Joseph", "Heilige Jozef"),
                    3 to 25 to t("Annunciation", "Aankondiging van de Heer"), 6 to 29 to t("Sts Peter and Paul", "Heiligen Petrus en Paulus"),
                    8 to 6 to t("Transfiguration", "Gedaanteverandering"), 8 to 15 to t("Assumption of Mary", "Maria-Tenhemelopneming"),
                    11 to 1 to t("All Saints", "Allerheiligen"), 11 to 2 to t("All Souls", "Allerzielen"),
                    12 to 8 to t("Immaculate Conception", "Onbevlekte Ontvangenis"), 12 to 25 to t("Christmas", "Kerstmis"),
                )).forEach { add(Feast(it, FeastRank.GREAT)) }
            }
        }
    }

    private fun fixed(date: LocalDate, list: List<Pair<Pair<Int, Int>, L10n>>): List<L10n> =
        list.filter { (md, _) -> md.first == date.monthValue && md.second == date.dayOfMonth }.map { it.second }

    /** First Sunday of Advent: the Sunday between 27 November and 3 December. */
    fun advent(year: Int): LocalDate {
        var d = LocalDate.of(year, 11, 27)
        while (d.dayOfWeek.value != 7) d = d.plusDays(1)
        return d
    }
}
