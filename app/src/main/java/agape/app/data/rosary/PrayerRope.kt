package agape.app.data.rosary

import agape.app.core.model.L10n

/**
 * The prayer ropes besides the Rosary: the Byzantine chotki (komboskini) for the Jesus Prayer, in 33, 50 or 100 knots,
 * and the Coptic rope of 41 knots for Kyrie eleison, prayed 41 times in every hour of the Agpeya. Each tenth knot
 * is a bead: on the chotki the Mother of God is asked to pray for us there.
 */
enum class PrayerRope(val sizes: List<Int>, val prayer: L10n, val bead: L10n?) {
    CHOTKI(
        listOf(33, 50, 100),
        L10n("Lord Jesus Christ, Son of God, have mercy on me, a sinner.", "Heer Jezus Christus, Zoon van God, ontferm U over mij, zondaar."),
        L10n("Most Holy Theotokos, save us.", "Allerheiligste Moeder Gods, red ons."),
    ),
    KYRIE(listOf(41), L10n("Κύριε ἐλέησον", "Κύριε ἐλέησον"), null);

    companion object {
        /** A bead after every ten knots. */
        fun isBead(knot: Int) = knot > 0 && knot % 10 == 0
    }
}

/** Counting along a rope: the knot reached, and how often the rope was prayed through. */
data class RopeCount(val size: Int, val knot: Int = 0, val rounds: Int = 0) {
    val done: Boolean get() = knot == size
    fun next(): RopeCount = when {
        knot < size -> copy(knot = knot + 1, rounds = if (knot + 1 == size) rounds + 1 else rounds)
        else -> copy(knot = 1)
    }
    fun back(): RopeCount = copy(knot = (knot - 1).coerceAtLeast(0))
}
