package agape.app.data.content

import agape.app.core.model.AppLanguage
import java.text.Normalizer

/** Lower case without accents, so "Matteus" finds "Matteüs" and "Korintiers" finds "Korintiërs". */
fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(combiningMarks, "").lowercase().trim()

private val combiningMarks = Regex("\\p{Mn}+")

/** Whether [q] occurs in this text, ignoring case and accents. */
fun String.has(q: String) = fold(this).contains(fold(q))

val GOSPELS = listOf("MAT", "MRK", "LUK", "JHN")

/** Common spellings that the book names in books.json do not start with. */
private val bookAliases = mapOf(
    "MAT" to listOf("mattheus", "matth"),
    "MRK" to listOf("markus"),
    "LUK" to listOf("lukas"),
    "JHN" to listOf("joh"),
    "JAS" to listOf("jacobus"),
    "REV" to listOf("apocalyps", "apokalyps"),
    "SNG" to listOf("song of songs"),
)

private val gospelWords = listOf("evangelie", "evangelien", "gospel", "gospels")

/** "Evangelie", "Evangeliën", "Gospels" (or their first four letters or more) ask for the four Gospels. */
fun isGospelQuery(q: String): Boolean = fold(q).let { f -> f.length >= 4 && gospelWords.any { it.startsWith(f) || f == "4 $it" || f == "vier $it" || f == "four $it" } }

/** Whether a name of this book, or one of its common spellings, starts with [name]. */
fun Book.nameStarts(name: String, lang: AppLanguage): Boolean {
    val n = fold(name)
    return n.isNotEmpty() && (fold(this.name[lang]).startsWith(n) || fold(this.name.en).startsWith(n) || id.lowercase() == n ||
        bookAliases[id].orEmpty().any { it.startsWith(n) })
}

/** Whether this book answers a search for [q]: its name contains it or starts like it. */
fun Book.matches(q: String, lang: AppLanguage): Boolean = name[lang].has(q) || name.en.has(q) || nameStarts(q, lang)

/** The books asked for by [q], Gospels first. */
fun List<Book>.findBooks(q: String, lang: AppLanguage): List<Book> =
    (if (isGospelQuery(q)) filter { it.id in GOSPELS } else filter { it.matches(q, lang) })
        .sortedBy { GOSPELS.indexOf(it.id).let { i -> if (i < 0) GOSPELS.size else i } }
