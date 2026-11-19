package agape.app

import agape.app.data.rosary.MysterySet
import agape.app.data.rosary.PrayerRope
import agape.app.data.rosary.RopeCount
import agape.app.data.rosary.Rosary
import agape.app.data.rosary.RosaryProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun countsManyRounds() {
        var c = RopeCount(50)
        repeat(50 * 3 + 7) { c = c.next() }
        assertEquals(7, c.knot)
        assertEquals(3, c.rounds)
    }

    @Test fun backAfterAFullRoundTakesTheRoundOff() {
        var c = RopeCount(33)
        repeat(33) { c = c.next() }
        assertEquals(RopeCount(33, 33, 1), c)
        c = c.back()
        assertEquals(RopeCount(33, 32, 0), c)
        assertFalse(c.done)
        // Next again completes the round once more, counted once.
        c = c.next()
        assertEquals(RopeCount(33, 33, 1), c)
    }

    @Test fun backAlwaysUndoesNext() {
        var c = RopeCount(33)
        val seen = mutableListOf(c)
        repeat(33 * 2 + 5) { c = c.next(); seen += c }
        // Going all the way back passes every count in reverse; the wrap to knot 1 goes back by way of the full rope.
        for (expected in seen.reversed().drop(1)) {
            c = c.back()
            if (c.knot == 0 && c.rounds > 0) c = c.back()
            assertEquals(expected.knot, c.knot)
            assertEquals(expected.rounds, c.rounds)
        }
        assertEquals(RopeCount(33), c)
        assertEquals(RopeCount(33), c.back())
    }

    @Test fun savesAndRestoresTheCount() {
        val c = RopeCount(100, 42, 3)
        assertEquals(c, RopeCount.decode(c.encode()))
        assertNull(RopeCount.decode(null))
        assertNull(RopeCount.decode("33|40|0"))
        assertNull(RopeCount.decode("rubbish"))
    }

    @Test fun beadEveryTenKnots() {
        assertTrue(PrayerRope.isBead(10))
        assertTrue(PrayerRope.isBead(40))
        assertFalse(PrayerRope.isBead(0))
        assertFalse(PrayerRope.isBead(41))
    }

    @Test fun rosaryProgressIsKeptForTheDay() {
        val p = RosaryProgress(MysterySet.SORROWFUL, 23, false, 20_000)
        assertEquals(p, RosaryProgress.decode(p.encode()))
        assertTrue(p.resumableOn(20_000))
        assertFalse(p.resumableOn(20_001))
        assertNull(RosaryProgress.decode("SORROWFUL|${Rosary.steps.size}|false|1"))
        assertNull(RosaryProgress.decode("NONE|1|false|1"))
        assertNull(RosaryProgress.decode(null))
    }

    @Test fun everyBeadCanBeTappedToItsPrayer() {
        for (bead in 0 until Rosary.BEAD_COUNT) {
            val step = Rosary.firstStepOn(bead)
            assertTrue("bead $bead has no step", step >= 0)
            assertEquals(bead, Rosary.steps[step].bead)
        }
    }
}
