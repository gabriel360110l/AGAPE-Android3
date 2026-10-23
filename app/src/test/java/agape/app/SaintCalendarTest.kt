package agape.app

import agape.app.core.model.Passage
import agape.app.core.model.Tradition
import agape.app.data.calendar.CopticDate
import agape.app.data.saints.SaintCalendar
import agape.app.data.saints.UnifiedSaints
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** The bundled uniform saints dataset, read the way the calendar screen reads it. */
class SaintCalendarTest {
    private val calendar by lazy {
        // Unit tests run from the module directory.
        val text = File("src/main/assets/content/saints_unified.json").readText()
        SaintCalendar(Json { ignoreUnknownKeys = true }.decodeFromString<UnifiedSaints>(text))
    }

    @Test fun aDayHasItsDateInAllThreeCalendars() {
        val day = calendar.day(Tradition.ORIENTAL_ORTHODOX, LocalDate.of(2026, 10, 23))
        assertEquals(LocalDate.of(2026, 10, 10), day.julian)
        assertEquals(CopticDate(1743, 2, 13), day.coptic)
        assertTrue(day.commemorations.first().entry.name.en.contains("Zacharias"))
    }

    @Test fun eachTraditionReadsItsOwnCalendar() {
        val date = LocalDate.of(2026, 10, 23)
        assertTrue(calendar.day(Tradition.EASTERN_ORTHODOX, date).commemorations.first().entry.name.en.contains("James"))
        assertTrue(calendar.day(Tradition.CATHOLIC, date).commemorations.first().entry.name.en.contains("Capistrano"))
        // Coptic Christmas is 29 Koiak, which falls on 7 January.
        val nativity = calendar.day(Tradition.ORIENTAL_ORTHODOX, LocalDate.of(2027, 1, 7))
        assertNotNull(nativity.icon)
        assertEquals("icons/nativity.jpg", nativity.icon?.file)
    }

    @Test fun catholicReadingsOpenInTheReader() {
        val readings = calendar.day(Tradition.CATHOLIC, LocalDate.of(2026, 10, 25)).readings
        assertEquals(listOf("EXO 22:20-26", "PSA 18:2-3", "1TH 1:5-10", "MAT 22:34-40"), readings.map { it.ref })
        var date = LocalDate.of(2026, 1, 1)
        while (date.year == 2026) {
            calendar.day(Tradition.CATHOLIC, date).readings.mapNotNull { it.ref }.forEach { assertNotNull(it, Passage.parse(it)) }
            date = date.plusDays(1)
        }
    }
}
