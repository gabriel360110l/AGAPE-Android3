package agape.app.data.content

import agape.app.core.model.AppLanguage
import agape.app.core.model.L10n
import agape.app.core.model.Tradition
import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/** Bundled, offline content from `assets/content`. Everything is parsed once and kept in memory. */
class ContentRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private inline fun <reified T> load(name: String): T =
        context.assets.open("content/$name").bufferedReader().use { json.decodeFromString<T>(it.readText()) }

    val books: List<Book> by lazy { load<List<Book>>("books.json").sortedBy { it.order } }
    private val booksById by lazy { books.associateBy { it.id } }

    private val prayerBooks: Map<Tradition, PrayerBook> by lazy {
        listOf("prayers_eo.json", "prayers_oo.json", "prayers_ca.json").map { load<PrayerBook>(it) }.associateBy { it.tradition }
    }
    private val prayers: List<Prayer> by lazy { load("prayers_extra.json") }
    val councils: List<Council> by lazy { load<List<Council>>("councils.json").sortedBy { it.number } }
    val plans: List<PlanDefinition> by lazy { load("plans.json") }
    private val quizFile: QuizFile by lazy { load("quizzes.json") }
    private val church: ChurchFile by lazy { load("church.json") }
    private val councilTexts: Map<String, CouncilTexts> by lazy { load("council_texts.json") }
    private val saintsEo: List<SaintEntry> by lazy { load("saints_eo.json") }
    private val verseOfDay: VerseOfDayFile by lazy { load("verse_of_day.json") }
    private val verseNotes: JsonObject by lazy {
        context.assets.open("content/verse_notes.json").bufferedReader().use { json.parseToJsonElement(it.readText()).jsonObject }
    }

    fun book(id: String): Book? = booksById[id]

    /** Books of [tradition]'s canon that exist in the translation used for [language]. */
    fun canon(tradition: Tradition, language: AppLanguage): List<Book> =
        books.filter { tradition in it.traditions && it.chapterCount(language) > 0 }

    fun prayerBook(tradition: Tradition): PrayerBook = prayerBooks.getValue(tradition)
    fun prayerBookHourIds(): Set<String> = prayerBooks.values.flatMap { b -> b.hours.map { it.id } }.toSet()

    /** An hour or an extra service of [tradition]'s prayer book. */
    fun prayerEntry(tradition: Tradition, id: String): PrayerHour? =
        prayerBook(tradition).let { b -> b.hours.firstOrNull { it.id == id } ?: b.extras.firstOrNull { it.id == id } }
    fun prayer(id: String): Prayer? = prayers.firstOrNull { it.id == id }
    fun listedPrayers(tradition: Tradition): List<Prayer> = prayers.filter { it.listed && tradition in it.traditions }

    val quizPassMark: Double get() = quizFile.passMark
    fun quiz(key: String): List<QuizQuestion>? =
        quizFile.quizzes[key] ?: key.removePrefix("council:").takeIf { key.startsWith("council:") }
            ?.let { id -> councils.firstOrNull { it.id == id }?.quiz?.takeIf { q -> q.isNotEmpty() } }

    fun councils(tradition: Tradition): List<Council> = councils.filter { tradition in it.recognizedBy }
    fun council(id: String): Council? = councils.firstOrNull { it.id == id }
    fun councilTexts(id: String): CouncilTexts = councilTexts[id] ?: CouncilTexts()
    fun plan(id: String): PlanDefinition? = plans.firstOrNull { it.id == id }

    fun articles(tradition: Tradition, section: ChurchSection): List<ChurchArticle> =
        church.articles.filter { it.section == section && tradition in it.traditions }
    fun article(id: String): ChurchArticle? = church.articles.firstOrNull { it.id == id }
    fun fathers(tradition: Tradition): List<ChurchFather> = church.fathers.filter { tradition in it.traditions }
    fun father(id: String): ChurchFather? = church.fathers.firstOrNull { it.id == id }

    /** The Byzantine menologion entry for a civil date (new calendar). */
    fun saintEo(date: LocalDate): SaintEntry? = saintsEo.firstOrNull { it.month == date.monthValue && it.day == date.dayOfMonth }

    /** A stable verse for the day, mixing shared and tradition-specific verses. */
    fun verseOfDay(tradition: Tradition, date: LocalDate): String {
        val own = when (tradition) {
            Tradition.EASTERN_ORTHODOX -> verseOfDay.easternOrthodox
            Tradition.ORIENTAL_ORTHODOX -> verseOfDay.orientalOrthodox
            Tradition.CATHOLIC -> verseOfDay.catholic
        }
        val pool = verseOfDay.common + own
        return pool[Math.floorMod(date.toEpochDay(), pool.size.toLong()).toInt()]
    }

    /** Sample per-verse notes: the shared note and, if present, a tradition-specific one. */
    fun verseNotes(key: String, tradition: Tradition): List<L10n> {
        val entry = verseNotes[key] as? JsonObject ?: return emptyList()
        return listOf("all", tradition.name).mapNotNull { k ->
            (entry[k] as? JsonObject)?.let { L10n(it["en"]?.jsonPrimitive?.content.orEmpty(), it["nl"]?.jsonPrimitive?.content.orEmpty()) }
        }
    }
}

fun Book.chapterCount(language: AppLanguage): Int = chapters[language.tag] ?: 0
