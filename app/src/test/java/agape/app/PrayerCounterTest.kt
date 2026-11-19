package agape.app

import agape.app.data.counter.AtEnd
import agape.app.data.counter.Buzz
import agape.app.data.counter.PrayerCount
import agape.app.data.counter.buzzFor
import agape.app.data.rosary.BeadKind
import agape.app.data.rosary.PrayerRope
import agape.app.data.rosary.Rosary
import agape.app.ui.components.CounterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one counter of the prayer rope, the Rosary and the Agpeya: tap, back, jump, wait per bead, feel and saving. */
class PrayerCounterTest {
    private var now = 0L
    private val saved = mutableListOf<PrayerCount>()
    private val felt = mutableListOf<Buzz>()

    private fun counter(total: Int, atEnd: AtEnd, marker: (PrayerCount) -> Boolean = { it.complete }) =
        CounterState(PrayerCount(total), atEnd, save = { saved += it }, clock = { now }).also {
            it.marker = marker
            it.feel = { b -> felt += b }
        }

    @Test fun eachEndBehavesAsItsCounter() {
        assertEquals(PrayerCount(3, 1, 1), PrayerCount(3, 3, 1).next(AtEnd.WRAP))
        assertEquals(PrayerCount(3, 0, 1), PrayerCount(3, 3, 1).next(AtEnd.RESTART))
        assertEquals(PrayerCount(3, 3, 1), PrayerCount(3, 3, 1).next(AtEnd.STOP))
    }

    @Test fun backUndoesTapInEveryMode() {
        for (mode in AtEnd.entries) {
            val c = counter(12, mode)
            val seen = mutableListOf(c.count)
            repeat(12) { c.tap(); seen += c.count }
            for (expected in seen.reversed().drop(1)) {
                c.back()
                assertEquals("$mode", expected, c.count)
            }
        }
    }

    @Test fun everyChangeIsSavedAndTheEndIsReportedOnce() {
        var completed = 0
        val c = counter(41, AtEnd.RESTART)
        c.onComplete = { completed++ }
        repeat(41) { c.tap() }
        assertEquals(1, completed)
        assertEquals(41, saved.size)
        assertEquals(PrayerCount(41, 41, 1), saved.last())
        c.tap()
        assertEquals(PrayerCount(41, 0, 1), c.count)
        assertEquals(1, completed)
    }

    @Test fun theRosaryStopsAtItsEnd() {
        val c = counter(Rosary.steps.size, AtEnd.STOP)
        repeat(Rosary.steps.size) { assertTrue(c.tap()) }
        assertTrue(c.count.complete)
        assertFalse(c.tap())
        c.back()
        assertEquals(Rosary.steps.lastIndex, c.count.done)
        assertEquals(0, c.count.rounds)
    }

    @Test fun tappingABeadJumpsAndLeavingTheEndTakesTheRoundOff() {
        val c = counter(Rosary.steps.size, AtEnd.STOP)
        val step = Rosary.firstStepOn(30)
        c.jump(step)
        assertEquals(step, c.count.done)
        repeat(Rosary.steps.size) { c.tap() }
        assertTrue(c.count.complete)
        assertEquals(1, c.count.rounds)
        c.jump(0)
        assertEquals(PrayerCount(Rosary.steps.size, 0, 0), c.count)
    }

    @Test fun theWaitPerBeadHoldsTapsBack() {
        val c = counter(33, AtEnd.WRAP)
        c.paceMillis = 2_000
        now = 2_000
        assertTrue(c.tap())
        now += 500
        assertFalse(c.tap())
        assertEquals(1, c.count.done)
        now += 1_500
        assertTrue(c.tap())
        c.paceMillis = 0
        assertTrue(c.tap())
        assertEquals(3, c.count.done)
    }

    @Test fun ropeMarkersOnEveryTenthKnotAndTheEnd() {
        val c = counter(33, AtEnd.WRAP) { PrayerRope.isBead(it.done) || it.complete }
        repeat(33) { c.tap() }
        val marks = felt.withIndex().filter { it.value == Buzz.MARK }.map { it.index + 1 }
        assertEquals(listOf(10, 20, 30, 33), marks)
        assertEquals(33 - 4, felt.count { it == Buzz.TICK })
    }

    @Test fun rosaryMarkersOnTheLargeBeads() {
        val c = counter(Rosary.steps.size, AtEnd.STOP) { it.complete || Rosary.kind(Rosary.steps[it.done].bead) == BeadKind.LARGE }
        repeat(Rosary.steps.size) { c.tap() }
        val large = (1 until Rosary.steps.size).count { Rosary.kind(Rosary.steps[it].bead) == BeadKind.LARGE }
        assertEquals(large + 1, felt.count { it == Buzz.MARK })
    }

    @Test fun vibrationAndQuietMarkersFollowSettings() {
        assertEquals(Buzz.NONE, buzzFor(marker = true, vibration = false, quiet = true, quietMarkers = true))
        assertEquals(Buzz.MARK, buzzFor(marker = true, vibration = true, quiet = false, quietMarkers = false))
        assertEquals(Buzz.MARK, buzzFor(marker = true, vibration = true, quiet = true, quietMarkers = true))
        assertEquals(Buzz.TICK, buzzFor(marker = true, vibration = true, quiet = true, quietMarkers = false))
        assertEquals(Buzz.TICK, buzzFor(marker = false, vibration = true, quiet = true, quietMarkers = true))
        val c = counter(10, AtEnd.WRAP)
        c.vibration = false
        repeat(10) { c.tap() }
        assertTrue(felt.all { it == Buzz.NONE })
    }

    @Test fun savedCountsComeBackAndBadOnesDoNot() {
        val c = PrayerCount(100, 42, 3)
        assertEquals(c, PrayerCount.decode(c.encode()))
        // The rope's count from before the shared counter reads the same.
        assertEquals(PrayerCount(33, 12, 2), PrayerCount.decode("33|12|2"))
        for (bad in listOf(null, "", "33|40|0", "0|0|0", "33|-1|0", "33|1|-1", "rubbish", "1|2")) {
            assertEquals(bad, null, PrayerCount.decode(bad))
        }
    }
}
