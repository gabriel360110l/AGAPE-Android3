package agape.app

import agape.app.core.model.Tradition
import agape.app.data.counter.AtEnd
import agape.app.data.counter.PrayerCount
import agape.app.data.rosary.PrayerRope
import agape.app.data.rosary.Rosary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerRopeTest {
    @Test fun countsRoundsAndStartsAgain() {
        var c = PrayerCount(33)
        repeat(33) { c = c.next(AtEnd.WRAP) }
        assertTrue(c.complete)
        assertEquals(1, c.rounds)
        c = c.next(AtEnd.WRAP)
        assertEquals(1, c.done)
        assertEquals(1, c.rounds)
        assertEquals(0, PrayerCount(41).back().done)
    }

    @Test fun countsManyRounds() {
        var c = PrayerCount(50)
        repeat(50 * 3 + 7) { c = c.next(AtEnd.WRAP) }
        assertEquals(7, c.done)
        assertEquals(3, c.rounds)
    }

    @Test fun backAfterAFullRoundTakesTheRoundOff() {
        var c = PrayerCount(33)
        repeat(33) { c = c.next(AtEnd.WRAP) }
        assertEquals(PrayerCount(33, 33, 1), c)
        c = c.back()
        assertEquals(PrayerCount(33, 32, 0), c)
        assertFalse(c.complete)
        // Next again completes the round once more, counted once.
        c = c.next(AtEnd.WRAP)
        assertEquals(PrayerCount(33, 33, 1), c)
    }

    @Test fun backAlwaysUndoesNext() {
        var c = PrayerCount(33)
        val seen = mutableListOf(c)
        repeat(33 * 2 + 5) { c = c.next(AtEnd.WRAP); seen += c }
        // Going all the way back passes every count in reverse; the wrap to knot 1 goes back by way of the full rope.
        for (expected in seen.reversed().drop(1)) {
            c = c.back()
            if (c.done == 0 && c.rounds > 0) c = c.back()
            assertEquals(expected.done, c.done)
            assertEquals(expected.rounds, c.rounds)
        }
        assertEquals(PrayerCount(33), c)
        assertEquals(PrayerCount(33), c.back())
    }

    @Test fun savesAndRestoresTheCount() {
        val c = PrayerCount(100, 42, 3)
        assertEquals(c, PrayerCount.decode(c.encode()))
        assertNull(PrayerCount.decode(null))
        assertNull(PrayerCount.decode("33|40|0"))
        assertNull(PrayerCount.decode("rubbish"))
    }

    @Test fun markersFollowTheChoice() {
        assertEquals(listOf(0, 10, 25, 33), PrayerRope.MARKER_CHOICES)
        fun marks(size: Int, every: Int) = (1..size).filter { PrayerRope.isMarker(it, every) }
        assertEquals(listOf(10, 20, 30), marks(33, 10))
        assertEquals(listOf(25, 50, 75, 100), marks(100, 25))
        assertEquals(listOf(33), marks(33, 33))
        assertEquals(listOf(33, 66, 99), marks(100, 33))
        assertEquals(emptyList<Int>(), marks(100, 0))
        assertFalse(PrayerRope.isMarker(0, 10))
    }

    @Test fun onlyTheByzantineRopeHasAMarkerPrayer() {
        assertEquals("Allerheiligste Moeder Gods, red ons.", PrayerRope.CHOTKI.markerPrayer(Tradition.EASTERN_ORTHODOX)?.nl)
        assertNull(PrayerRope.CHOTKI.markerPrayer(Tradition.ORIENTAL_ORTHODOX))
        assertNull(PrayerRope.CHOTKI.markerPrayer(Tradition.CATHOLIC))
    }

    @Test fun everyBeadCanBeTappedToItsPrayer() {
        for (bead in 0 until Rosary.BEAD_COUNT) {
            val step = Rosary.firstStepOn(bead)
            assertTrue("bead $bead has no step", step >= 0)
            assertEquals(bead, Rosary.steps[step].bead)
        }
    }
}
