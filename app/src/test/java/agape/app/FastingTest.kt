package agape.app

import agape.app.core.model.Tradition
import agape.app.data.calendar.FastLevel
import agape.app.data.calendar.Fasting
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FastingTest {
    private fun level(t: Tradition, y: Int, m: Int, d: Int) = Fasting.status(t, LocalDate.of(y, m, d)).level

    @Test fun easternOrthodox() {
        val eo = Tradition.EASTERN_ORTHODOX // Pascha 12 April 2026
        assertEquals(FastLevel.NO_MEAT, level(eo, 2026, 2, 16)) // Cheesefare Monday
        assertEquals(FastLevel.STRICT, level(eo, 2026, 2, 23)) // Clean Monday
        assertEquals(FastLevel.FAST, level(eo, 2026, 3, 15)) // a Sunday of Lent
        assertEquals(FastLevel.STRICT, level(eo, 2026, 4, 10)) // Holy Friday
        assertEquals(FastLevel.FREE, level(eo, 2026, 4, 15)) // Wednesday of Bright Week
        assertEquals(FastLevel.FAST, level(eo, 2026, 6, 8)) // Apostles' Fast begins
        assertEquals(FastLevel.NONE, level(eo, 2026, 6, 29)) // Peter and Paul, a Monday
        assertEquals(FastLevel.FAST, level(eo, 2026, 10, 28)) // a Wednesday
        assertEquals(FastLevel.NONE, level(eo, 2026, 10, 29))
        assertEquals(FastLevel.FREE, level(eo, 2026, 12, 30)) // Wednesday in the twelve days
        assertEquals(FastLevel.STRICT, level(eo, 2026, 1, 5))
        assertEquals(FastLevel.STRICT, level(eo, 2026, 9, 14))
        val entry = Fasting.status(eo, LocalDate.of(2026, 11, 21))
        assertEquals("Vastendag · Kerstvasten · vis toegestaan", entry.label.nl)
    }

    @Test fun orientalOrthodox() {
        val oo = Tradition.ORIENTAL_ORTHODOX
        assertEquals(FastLevel.FAST, level(oo, 2026, 2, 2)) // Nineveh
        assertEquals(FastLevel.NONE, level(oo, 2026, 2, 5)) // Thursday after Nineveh
        assertEquals(FastLevel.FAST, level(oo, 2026, 2, 16)) // Great Lent, 55 days
        assertEquals(FastLevel.STRICT, level(oo, 2026, 4, 10)) // Good Friday
        assertEquals(FastLevel.FREE, level(oo, 2026, 4, 22)) // Wednesday in the Fifty Days
        assertEquals(FastLevel.FAST, level(oo, 2026, 6, 1)) // Apostles' Fast
        assertEquals(FastLevel.FAST, level(oo, 2026, 7, 11)) // 4 Epip, last day
        assertEquals(FastLevel.FAST, level(oo, 2026, 8, 7)) // Fast of St Mary
        assertEquals(FastLevel.FAST, level(oo, 2026, 12, 1)) // Nativity Fast
        assertEquals(FastLevel.STRICT, level(oo, 2027, 1, 6)) // Paramoun
        assertEquals(FastLevel.FREE, level(oo, 2027, 1, 7)) // Nativity
        assertEquals(FastLevel.FAST, level(oo, 2026, 10, 30)) // a Friday
        assertEquals(FastLevel.NONE, level(oo, 2026, 10, 31))
    }

    @Test fun catholic() {
        val rc = Tradition.CATHOLIC // Easter 5 April 2026
        assertEquals(FastLevel.STRICT, level(rc, 2026, 2, 18)) // Ash Wednesday
        assertEquals(FastLevel.NONE, level(rc, 2026, 3, 4)) // Wednesday in Lent
        assertEquals(FastLevel.NO_MEAT, level(rc, 2026, 3, 6)) // Friday in Lent
        assertEquals(FastLevel.STRICT, level(rc, 2026, 4, 3)) // Good Friday
        assertEquals(FastLevel.FREE, level(rc, 2026, 4, 10)) // Friday in the Octave
        assertEquals(FastLevel.NO_MEAT, level(rc, 2026, 10, 30))
        assertEquals(FastLevel.NONE, level(rc, 2026, 12, 25)) // Christmas on a Friday
        assertEquals("No fast day", Fasting.status(rc, LocalDate.of(2026, 10, 28)).label.en)
    }
}
