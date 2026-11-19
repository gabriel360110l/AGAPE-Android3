package agape.app.data.counter

/** What a tap does once every prayer of a counter is said. */
enum class AtEnd {
    /** Round again from the first: the prayer rope, prayed through many times. */
    WRAP,
    /** Back to nothing, to pray it once more: the Lord have mercy of the Agpeya. */
    RESTART,
    /** Stay at the end: the Rosary, which ends with its last prayer. */
    STOP,
}

/**
 * One count of prayers, shared by the prayer rope, the Rosary and the counters of the Agpeya: [done] of [total]
 * said, and how many [rounds] were completed. A round counts once its last prayer is said; going back from there
 * takes the round off again, so [back] always undoes [next].
 */
data class PrayerCount(val total: Int, val done: Int = 0, val rounds: Int = 0) {
    init {
        require(total > 0 && done in 0..total && rounds >= 0) { "not a count: $total|$done|$rounds" }
    }

    val complete: Boolean get() = done == total

    fun next(atEnd: AtEnd): PrayerCount = when {
        done < total -> copy(done = done + 1, rounds = if (done + 1 == total) rounds + 1 else rounds)
        atEnd == AtEnd.WRAP -> copy(done = 1)
        atEnd == AtEnd.RESTART -> copy(done = 0)
        else -> this
    }

    fun back(): PrayerCount = when {
        complete -> copy(done = total - 1, rounds = (rounds - 1).coerceAtLeast(0))
        done > 0 -> copy(done = done - 1)
        // At the start of a new round: back over the wrap, to the end of the round before.
        rounds > 0 -> copy(done = total)
        else -> this
    }

    /** Straight to [to] prayers said, as when a bead of the Rosary is tapped. Leaving the end takes its round off. */
    fun jump(to: Int): PrayerCount {
        val target = to.coerceIn(0, total)
        val r = rounds - (if (complete) 1 else 0) + (if (target == total) 1 else 0)
        return copy(done = target, rounds = r.coerceAtLeast(0))
    }

    fun encode(): String = "$total|$done|$rounds"

    companion object {
        /** A count saved by [encode], or null when it is missing or not a valid count. */
        fun decode(text: String?): PrayerCount? {
            val parts = text?.split('|')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 3 } ?: return null
            val (total, done, rounds) = parts
            return if (total > 0 && done in 0..total && rounds >= 0) PrayerCount(total, done, rounds) else null
        }
    }
}

/** The feel of one tap: nothing, a light tick, or the marker pulse at a tenth knot, a decade or the end. */
enum class Buzz { NONE, TICK, MARK }

/**
 * Which buzz a tap gives. Nothing when vibration is off in Settings; a marker pulse where [marker] is true; a tick
 * otherwise. In quiet mode the marker pulse can be switched off ([quietMarkers]); then a marker ticks like any tap.
 */
fun buzzFor(marker: Boolean, vibration: Boolean, quiet: Boolean, quietMarkers: Boolean): Buzz = when {
    !vibration -> Buzz.NONE
    marker && (!quiet || quietMarkers) -> Buzz.MARK
    else -> Buzz.TICK
}

/**
 * The wait on each bead (Settings: wait per bead): a tap counts only [minMillis] after the last step, so no prayer is
 * rushed, also in quiet mode where the whole screen is the tap area. With [minMillis] 0 every tap counts.
 */
class PaceGate(private val minMillis: Long) {
    private var since = Long.MIN_VALUE

    /** Starts the wait anew, from [now]: after every step, and when the counter is shown. */
    fun restart(now: Long) { since = now }

    fun open(now: Long): Boolean = minMillis <= 0 || since == Long.MIN_VALUE || now - since >= minMillis
}
