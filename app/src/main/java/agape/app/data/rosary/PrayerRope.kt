package agape.app.data.rosary

import agape.app.core.model.L10n
import agape.app.core.model.Tradition

/**
 * A prayer rope for the Jesus Prayer, in 33, 50 or 100 knots. Its home is the Byzantine chotki (komboskini); the
 * screen is open to every tradition, with the cross and the explanation of the user's own (see the rope screen).
 */
enum class PrayerRope(val sizes: List<Int>, val prayer: L10n) {
    CHOTKI(
        listOf(33, 50, 100),
        L10n("Lord Jesus Christ, Son of God, have mercy on me, a sinner.", "Heer Jezus Christus, Zoon van God, ontferm U over mij, zondaar."),
    );

    /**
     * What is said on a marker knot instead of the Jesus Prayer: on the Byzantine chotki the Mother of God is asked to
     * pray for us. Other traditions keep the Jesus Prayer on every knot.
     */
    fun markerPrayer(tradition: Tradition): L10n? =
        if (tradition == Tradition.EASTERN_ORTHODOX) L10n("Most Holy Theotokos, save us.", "Allerheiligste Moeder Gods, red ons.") else null

    companion object {
        /** The marker choices offered: none, or a marker every 10, 25 or 33 knots. */
        val MARKER_CHOICES = listOf(0, 10, 25, 33)

        /** Whether knot [knot] (1-based) carries a marker when there is one every [every] knots (0: none). */
        fun isMarker(knot: Int, every: Int) = every > 0 && knot > 0 && knot % every == 0
    }
}
