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

/**
 * Counting along a rope: the knot reached, and how often the rope was prayed through. A round counts once its last
 * knot is reached; going back from there takes the round off again, so Back always undoes Next exactly.
 */
data class RopeCount(val size: Int, val knot: Int = 0, val rounds: Int = 0) {
    val done: Boolean get() = knot == size
    fun next(): RopeCount = when {
        knot < size -> copy(knot = knot + 1, rounds = if (knot + 1 == size) rounds + 1 else rounds)
        else -> copy(knot = 1)
    }
    fun back(): RopeCount = when {
        knot == size -> copy(knot = size - 1, rounds = (rounds - 1).coerceAtLeast(0))
        knot > 0 -> copy(knot = knot - 1)
        // At the start of a new round: back over the wrap, to the last knot of the round before.
        rounds > 0 -> copy(knot = size)
        else -> this
    }

    fun encode(): String = "$size|$knot|$rounds"

    companion object {
        /** A count saved by [encode], or null when it is missing or not a valid count. */
        fun decode(text: String?): RopeCount? {
            val parts = text?.split('|')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 3 } ?: return null
            val (size, knot, rounds) = parts
            return RopeCount(size, knot, rounds).takeIf { size > 0 && knot in 0..size && rounds >= 0 }
        }
    }
}
