package agape.app

import agape.app.data.schedule.SunSchedule
import agape.app.data.schedule.SunTimes
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class SunScheduleTest {
    private val sun = SunTimes(
        dawn = LocalTime.of(5, 30), sunrise = LocalTime.of(6, 0), noon = LocalTime.of(12, 10),
        sunset = LocalTime.of(18, 0), dusk = LocalTime.of(18, 30),
    )

    @Test fun hoursFollowTheSun() {
        assertEquals(LocalTime.of(5, 30), SunSchedule.timeFor("matins", sun, LocalTime.of(5, 30), agpeya = false))
        assertEquals(LocalTime.of(6, 0), SunSchedule.timeFor("first", sun, LocalTime.of(7, 0), agpeya = false))
        assertEquals(LocalTime.of(9, 0), SunSchedule.timeFor("third", sun, LocalTime.of(9, 0), agpeya = false))
        assertEquals(LocalTime.of(12, 10), SunSchedule.timeFor("sixth", sun, LocalTime.of(12, 0), agpeya = false))
        assertEquals(LocalTime.of(18, 0), SunSchedule.timeFor("vespers", sun, LocalTime.of(18, 0), agpeya = false))
        assertEquals(LocalTime.of(19, 30), SunSchedule.timeFor("compline", sun, LocalTime.of(21, 0), agpeya = false))
    }

    @Test fun agpeyaCountsElevenAndTwelve() {
        assertEquals(LocalTime.of(17, 0), SunSchedule.timeFor("vespers", sun, LocalTime.of(17, 0), agpeya = true))
        assertEquals(LocalTime.of(18, 0), SunSchedule.timeFor("compline", sun, LocalTime.of(18, 0), agpeya = true))
    }

    @Test fun unknownHoursAndPolarDaysKeepTheirTime() {
        assertEquals(LocalTime.of(0, 0), SunSchedule.timeFor("midnight", sun, LocalTime.of(0, 0), agpeya = false))
        val polar = sun.copy(sunrise = null, sunset = null)
        assertEquals(LocalTime.of(9, 0), SunSchedule.timeFor("third", polar, LocalTime.of(9, 0), agpeya = false))
    }
}
