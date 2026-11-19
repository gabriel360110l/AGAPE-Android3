package agape.app.data.rosary

/**
 * Where the Rosary was left: the set of mysteries, the step reached and whether it was finished, and on which day
 * (as an epoch day). It is resumed on the same day only, because another day has its own mysteries.
 */
data class RosaryProgress(val set: MysterySet, val step: Int, val finished: Boolean, val epochDay: Long) {
    fun resumableOn(today: Long): Boolean = epochDay == today

    fun encode(): String = "${set.name}|$step|$finished|$epochDay"

    companion object {
        /** Progress saved by [encode], or null when it is missing or does not fit the Rosary. */
        fun decode(text: String?): RosaryProgress? {
            val p = text?.split('|')?.takeIf { it.size == 4 } ?: return null
            val set = MysterySet.entries.firstOrNull { it.name == p[0] } ?: return null
            val step = p[1].toIntOrNull()?.takeIf { it in Rosary.steps.indices } ?: return null
            val finished = p[2].toBooleanStrictOrNull() ?: return null
            val day = p[3].toLongOrNull() ?: return null
            return RosaryProgress(set, step, finished, day)
        }
    }
}
