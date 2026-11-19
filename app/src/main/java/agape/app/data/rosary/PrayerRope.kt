package agape.app.data.rosary

import agape.app.core.model.L10n

/**
 * The prayer rope besides the Rosary: the Byzantine chotki (komboskini) for the Jesus Prayer, in 33, 50 or 100 knots.
 * Each tenth knot is a bead, where the Mother of God is asked to pray for us.
 */
enum class PrayerRope(val sizes: List<Int>, val prayer: L10n, val bead: L10n?) {
    CHOTKI(
        listOf(33, 50, 100),
        L10n("Lord Jesus Christ, Son of God, have mercy on me, a sinner.", "Heer Jezus Christus, Zoon van God, ontferm U over mij, zondaar."),
        L10n("Most Holy Theotokos, save us.", "Allerheiligste Moeder Gods, red ons."),
    );

    companion object {
        /** A bead after every ten knots. */
        fun isBead(knot: Int) = knot > 0 && knot % 10 == 0
    }
}
