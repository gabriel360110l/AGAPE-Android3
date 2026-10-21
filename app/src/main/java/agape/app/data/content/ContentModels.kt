package agape.app.data.content

import agape.app.core.model.L10n
import agape.app.core.model.L10nLines
import agape.app.core.model.Testament
import agape.app.core.model.Tradition
import kotlinx.serialization.Serializable

@Serializable
data class Book(
    val id: String,
    val order: Int,
    val testament: Testament,
    val deuterocanonical: Boolean,
    val traditions: List<Tradition>,
    val name: L10n,
    val intro: L10n? = null,
    /** Chapter count per language code ("en" = WEB, "nl" = Statenvertaling); 0 when the translation lacks the book. */
    val chapters: Map<String, Int>,
)

@Serializable
data class PrayerBook(
    val tradition: Tradition,
    val book: L10n,
    val intro: L10n,
    val hours: List<PrayerHour>,
)

@Serializable
data class PrayerHour(
    val id: String,
    val name: L10n,
    val subtitle: L10n = L10n(),
    /** Local time "HH:mm" at which this hour is traditionally prayed. */
    val time: String,
    val theme: L10n = L10n(),
    val sections: List<PrayerSection>,
)

@Serializable
data class PrayerSection(
    val kind: Kind,
    val title: L10n? = null,
    val body: L10nLines? = null,
    /** For [Kind.scripture]: a passage such as "PSA 51" or "LUK 2:29-32". */
    val ref: String? = null,
    /** For [Kind.prayer]: id of a prayer in prayers_extra.json. */
    val prayerId: String? = null,
) {
    @Suppress("EnumEntryName")
    enum class Kind { text, scripture, prayer }
}

@Serializable
data class Prayer(
    val id: String,
    val traditions: List<Tradition>,
    val title: L10n,
    val subtitle: L10n = L10n(),
    val body: L10nLines,
    val listed: Boolean = true,
)

@Serializable
data class QuizQuestion(val q: L10n, val options: List<L10n>, val answer: Int)

@Serializable
data class QuizFile(val passMark: Double, val quizzes: Map<String, List<QuizQuestion>>)

@Serializable
data class Council(
    val id: String,
    val number: Int,
    val year: String,
    val recognizedBy: List<Tradition>,
    val name: L10n,
    val convokedBy: L10n,
    val reason: L10n,
    val canons: List<L10n>,
    val outcome: L10n,
    val passages: List<String>,
    val quiz: List<QuizQuestion> = emptyList(),
)

enum class PlanCategory { BEGINNER, OT_NT, FULL, COUNCILS }

@Serializable
data class PlanDefinition(
    val id: String,
    val category: PlanCategory,
    /** A locked plan only opens the next day after the previous day's quiz is passed. */
    val locked: Boolean,
    val title: L10n,
    val description: L10n,
    val generator: PlanGenerator,
)

@Serializable
data class PlanGenerator(
    val type: Type,
    val books: List<String> = emptyList(),
    val testament: Testament? = null,
    val days: Int = 0,
    val fixedDays: List<FixedDay> = emptyList(),
) {
    enum class Type { BOOKS, TESTAMENT, CANON, ORDERED, FIXED, COUNCILS }
}

@Serializable
data class FixedDay(val title: L10n, val readings: List<String>)

enum class ChurchSection { HISTORY, TRADITION }

@Serializable
data class ChurchArticle(
    val id: String,
    val section: ChurchSection,
    val traditions: List<Tradition>,
    val title: L10n,
    val summary: L10n,
    val body: L10nLines,
)

@Serializable
data class ChurchFather(
    val id: String,
    val name: L10n,
    val era: String,
    val traditions: List<Tradition>,
    val summary: L10n,
)

@Serializable
data class ChurchFile(val articles: List<ChurchArticle>, val fathers: List<ChurchFather>)

@Serializable
data class SaintEntry(val month: Int, val day: Int, val rank: String, val title: L10n, val summary: L10n)

@Serializable
data class VerseOfDayFile(
    val common: List<String>,
    @kotlinx.serialization.SerialName("EASTERN_ORTHODOX") val easternOrthodox: List<String>,
    @kotlinx.serialization.SerialName("ORIENTAL_ORTHODOX") val orientalOrthodox: List<String>,
    @kotlinx.serialization.SerialName("CATHOLIC") val catholic: List<String>,
)
