package agape.app

import agape.app.core.model.Passage
import agape.app.data.rosary.BeadKind
import agape.app.data.rosary.MysterySet
import agape.app.data.rosary.Rosary
import agape.app.data.rosary.RosaryPrayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class RosaryTest {
    @Test fun beadsMakeARosary() {
        val kinds = (0 until Rosary.BEAD_COUNT).map { Rosary.kind(it) }
        assertEquals(1, kinds.count { it == BeadKind.CRUCIFIX })
        assertEquals(1, kinds.count { it == BeadKind.MEDAL })
        // Two on the pendant and four between the decades.
        assertEquals(6, kinds.count { it == BeadKind.LARGE })
        // Three on the pendant and fifty in the loop.
        assertEquals(53, kinds.count { it == BeadKind.SMALL })
    }

    @Test fun prayerCounts() {
        val prayers = Rosary.steps.flatMap { it.prayers }
        assertEquals(53, prayers.count { it == RosaryPrayer.HAIL_MARY })
        assertEquals(6, prayers.count { it == RosaryPrayer.OUR_FATHER })
        assertEquals(6, prayers.count { it == RosaryPrayer.GLORY_BE })
        assertEquals(5, prayers.count { it == RosaryPrayer.FATIMA })
        assertEquals(5, Rosary.steps.count { it.announce })
        assertEquals(RosaryPrayer.SIGN_OF_THE_CROSS, Rosary.steps.first().prayers.single())
        assertEquals(RosaryPrayer.SIGN_OF_THE_CROSS, Rosary.steps.last().prayers.single())
    }

    @Test fun everyBeadIsPrayedAndDecadesUseTheirOwnBeads() {
        assertEquals((0 until Rosary.BEAD_COUNT).toSet(), Rosary.steps.map { it.bead }.toSet())
        for (d in 0 until 5) {
            val beads = Rosary.steps.filter { it.decade == d && it.prayers == listOf(RosaryPrayer.HAIL_MARY) }.map { it.bead }
            assertEquals((0 until 10).map { Rosary.decadeBead(d, it) }, beads)
            assertTrue(beads.all { Rosary.kind(it) == BeadKind.SMALL })
        }
    }

    @Test fun mysteriesOfTheDay() {
        assertEquals(MysterySet.JOYFUL, MysterySet.forDay(DayOfWeek.MONDAY))
        assertEquals(MysterySet.SORROWFUL, MysterySet.forDay(DayOfWeek.FRIDAY))
        assertEquals(MysterySet.LUMINOUS, MysterySet.forDay(DayOfWeek.THURSDAY))
        assertEquals(MysterySet.GLORIOUS, MysterySet.forDay(DayOfWeek.SUNDAY))
        MysterySet.entries.forEach { assertEquals(5, Rosary.mysteries.getValue(it).size) }
    }

    /** Every mystery has its title, scripture and fruit in English and in Dutch, and its scripture can be opened. */
    @Test fun everyMysteryHasItsMeditationInBothLanguages() {
        assertEquals(MysterySet.entries.toSet(), Rosary.mysteries.keys)
        val all = Rosary.mysteries.values.flatten()
        assertEquals(20, all.size)
        Rosary.mysteries.values.forEach { assertEquals(5, it.size) }
        all.forEach { m ->
            listOf(m.title, m.fruit).forEach { t ->
                assertTrue("English missing: $t", t.en.isNotBlank())
                assertTrue("Dutch missing: $t", t.nl.isNotBlank())
                assertTrue("Not translated: $t", t.en != t.nl || t.en.length < 6)
            }
            assertNotNull("Scripture of ${m.title.en} cannot be read: ${m.passage}", Passage.parse(m.passage))
        }
        // Twenty different meditations.
        assertEquals(20, all.map { it.fruit.en }.toSet().size)
        assertEquals(20, all.map { it.fruit.nl }.toSet().size)
        assertEquals(20, all.map { it.passage }.toSet().size)
    }
}
