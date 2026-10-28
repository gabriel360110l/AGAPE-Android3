package agape.app

import agape.app.data.rosary.PrayerRope
import agape.app.data.rosary.RopeCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerRopeTest {
    @Test fun countsRoundsAndStartsAgain() {
        var c = RopeCount(33)
        repeat(33) { c = c.next() }
        assertTrue(c.done)
        assertEquals(1, c.rounds)
        c = c.next()
        assertEquals(1, c.knot)
        assertEquals(1, c.rounds)
        assertEquals(0, RopeCount(41).back().knot)
    }

    @Test fun beadEveryTenKnots() {
        assertTrue(PrayerRope.isBead(10))
        assertTrue(PrayerRope.isBead(40))
        assertFalse(PrayerRope.isBead(0))
        assertFalse(PrayerRope.isBead(41))
    }
}
