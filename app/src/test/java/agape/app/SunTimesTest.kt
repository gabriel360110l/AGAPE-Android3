package agape.app

import agape.app.data.schedule.SunTimes
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SunTimesTest {
    private val amsterdam = ZoneId.of("Europe/Amsterdam")

    private fun near(expected: String, actual: LocalTime?, minutes: Long = 3) {
        val e = LocalTime.parse(expected)
        assertTrue("expected about $e, got $actual", actual != null && Duration.between(e, actual).abs().toMinutes() <= minutes)
    }

    @Test fun amsterdamMidsummerAndMidwinter() {
        val june = SunTimes.of(LocalDate.of(2026, 6, 21), 52.3676, 4.9041, amsterdam)
        near("05:18", june.sunrise)
        near("22:06", june.sunset)
        near("13:41", june.noon)
        val december = SunTimes.of(LocalDate.of(2026, 12, 21), 52.3676, 4.9041, amsterdam)
        near("08:48", december.sunrise)
        near("16:29", december.sunset)
    }

    @Test fun cairoEquinox() {
        val t = SunTimes.of(LocalDate.of(2026, 3, 20), 30.0444, 31.2357, ZoneId.of("Africa/Cairo"))
        near("05:59", t.sunrise)
        near("18:05", t.sunset)
    }

    @Test fun noSunsetInPolarSummer() {
        val t = SunTimes.of(LocalDate.of(2026, 6, 21), 78.22, 15.65, ZoneId.of("Arctic/Longyearbyen"))
        assertNull(t.sunrise)
        assertNull(t.sunset)
    }
}
