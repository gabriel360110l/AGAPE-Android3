package agape.app.core.prefs

import java.time.LocalTime

/** The three lists of the diptychs: the living (and the sick), the departed, and thanksgiving. */
enum class IntentionKind { LIVING, DEPARTED, THANKS }

/** A name or intention remembered at the end of each hour, stored in settings as `id|KIND|text`. */
data class Intention(val id: String, val kind: IntentionKind, val text: String) {
    fun encode(): String = "$id|${kind.name}|${text.replace('|', ' ').trim()}"

    companion object {
        fun decode(value: String): Intention? {
            val parts = value.split('|', limit = 3).takeIf { it.size == 3 } ?: return null
            val kind = runCatching { IntentionKind.valueOf(parts[1]) }.getOrDefault(IntentionKind.LIVING)
            return parts[2].trim().takeIf { it.isNotEmpty() }?.let { Intention(parts[0], kind, it) }
        }
    }
}

val Settings.intentions: List<Intention> get() = prayerIntentions.mapNotNull { Intention.decode(it) }.sortedBy { it.text.lowercase() }

/** An own prayer moment besides the hours, stored in settings as `id|HH:mm|label`. */
data class CustomReminder(val id: String, val time: LocalTime, val label: String) {
    fun encode(): String = "$id|%02d:%02d|%s".format(time.hour, time.minute, label.replace('|', ' ').trim())

    companion object {
        fun decode(value: String): CustomReminder? {
            val parts = value.split('|', limit = 3)
            if (parts.size < 2 || parts[0].isBlank()) return null
            val time = runCatching { LocalTime.parse(parts[1]) }.getOrNull() ?: return null
            return CustomReminder(parts[0], time, parts.getOrNull(2).orEmpty())
        }
    }
}
