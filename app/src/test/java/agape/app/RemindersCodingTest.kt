package agape.app

import agape.app.core.prefs.Intention
import agape.app.core.prefs.IntentionKind
import agape.app.data.schedule.Place
import agape.app.core.prefs.CustomReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class RemindersCodingTest {
    @Test fun customRemindersRoundTrip() {
        val r = CustomReminder("a1", LocalTime.of(7, 15), "Ochtend | stilte")
        assertEquals(CustomReminder("a1", LocalTime.of(7, 15), "Ochtend   stilte"), CustomReminder.decode(r.encode()))
        assertNull(CustomReminder.decode("x|25:00|nope"))
        assertEquals("", CustomReminder.decode("b|06:00")!!.label)
    }

    @Test fun intentionsRoundTrip() {
        val i = Intention("n1", IntentionKind.DEPARTED, "Maria")
        assertEquals(i, Intention.decode(i.encode()))
        assertEquals(IntentionKind.LIVING, Intention.decode("n2|UNKNOWN|Jan")!!.kind)
        assertNull(Intention.decode("n3|LIVING|  "))
    }

    @Test fun placesRoundTrip() {
        val p = Place(52.3676, 4.9041, "Amsterdam; centrum")
        assertEquals(Place(52.3676, 4.9041, "Amsterdam, centrum"), Place.decode(p.encode()))
        assertNull(Place.decode("100;0;x"))
    }
}
