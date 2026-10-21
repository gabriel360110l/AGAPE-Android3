package agape.app.data.bible

import agape.app.core.model.AppLanguage
import agape.app.core.model.Passage
import agape.app.core.model.VerseRef
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

data class Verse(val book: String, val chapter: Int, val verse: Int, val text: String) {
    val ref: VerseRef get() = VerseRef(book, chapter, verse)
}

data class CrossRef(val book: String, val chapter: Int, val verse: Int, val verseEnd: Int)

data class ChapterGuide(val title: String, val summary: String, val context: String, val themes: List<String>, val related: List<Passage>)

data class FatherNote(val father: String, val work: String, val date: String, val verseFrom: Int, val verseTo: Int, val note: String, val source: String)

/** The bundled, read-only Bible database. Translation follows the app language: WEB for English, Statenvertaling for Dutch. */
class BibleRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var db: SQLiteDatabase? = null

    private fun database(): SQLiteDatabase = db ?: synchronized(this) {
        db ?: open().also { db = it }
    }

    /** SQLite cannot open files inside the APK, so the database is copied once per content version. */
    private fun open(): SQLiteDatabase {
        val target = File(context.noBackupFilesDir, "bible-v$DB_VERSION.db")
        if (!target.exists()) {
            context.noBackupFilesDir.listFiles { f -> f.name.startsWith("bible-v") }?.forEach { it.delete() }
            val tmp = File(target.path + ".tmp")
            context.assets.open("bible/bible.db").use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(target)
        }
        return SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    private fun translation(language: AppLanguage) = if (language == AppLanguage.DUTCH) "sv" else "web"

    suspend fun chapter(book: String, chapter: Int, language: AppLanguage): List<Verse> = withContext(Dispatchers.IO) {
        database().rawQuery(
            "SELECT verse, text FROM verses WHERE translation = ? AND book = ? AND chapter = ? ORDER BY verse",
            arrayOf(translation(language), book, chapter.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(Verse(book, chapter, c.getInt(0), c.getString(1))) } }
    }

    suspend fun passage(passage: Passage, language: AppLanguage): List<Verse> = withContext(Dispatchers.IO) {
        val start = passage.startVerse ?: 1
        val end = passage.endVerse ?: Int.MAX_VALUE
        database().rawQuery(
            """SELECT chapter, verse, text FROM verses WHERE translation = ? AND book = ?
               AND (chapter * 1000 + verse) BETWEEN CAST(? AS INTEGER) AND CAST(? AS INTEGER) ORDER BY chapter, verse""",
            arrayOf(
                translation(language), passage.book,
                (passage.chapter * 1000L + start).toString(),
                (passage.endChapter * 1000L + minOf(end, 999)).toString(),
            ),
        ).use { c -> buildList { while (c.moveToNext()) add(Verse(passage.book, c.getInt(0), c.getInt(1), c.getString(2))) } }
    }

    suspend fun verse(ref: VerseRef, language: AppLanguage): Verse? =
        passage(Passage(ref.book, ref.chapter, ref.verse), language).firstOrNull()

    suspend fun crossRefs(ref: VerseRef, limit: Int = 12): List<CrossRef> = withContext(Dispatchers.IO) {
        database().rawQuery(
            "SELECT to_book, to_chapter, to_verse, to_verse_end FROM cross_refs WHERE book = ? AND chapter = ? AND verse = ? ORDER BY votes DESC LIMIT ?",
            arrayOf(ref.book, ref.chapter.toString(), ref.verse.toString(), limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(CrossRef(c.getString(0), c.getInt(1), c.getInt(2), c.getInt(3))) } }
    }

    suspend fun chapterGuide(book: String, chapter: Int, language: AppLanguage): ChapterGuide? = withContext(Dispatchers.IO) {
        database().rawQuery(
            "SELECT title, summary, context, themes, related FROM chapter_guides WHERE book = ? AND chapter = ? AND lang IN (?, 'en') ORDER BY lang = ? DESC LIMIT 1",
            arrayOf(book, chapter.toString(), language.tag, language.tag),
        ).use { c ->
            if (!c.moveToFirst()) null else ChapterGuide(
                title = c.getString(0),
                summary = c.getString(1),
                context = c.getString(2),
                themes = runCatching { json.decodeFromString<List<String>>(c.getString(3)) }.getOrDefault(emptyList()),
                related = runCatching { json.decodeFromString<List<RelatedRow>>(c.getString(4)) }.getOrDefault(emptyList())
                    .mapNotNull { r -> r.chapter?.let { Passage(r.book, it, r.verse) } },
            )
        }
    }

    suspend fun fatherNotes(book: String, chapter: Int, language: AppLanguage): List<FatherNote> = withContext(Dispatchers.IO) {
        database().rawQuery(
            "SELECT father, work, date, verse_from, verse_to, note_en, note_nl, source FROM father_notes WHERE book = ? AND chapter = ? ORDER BY sort",
            arrayOf(book, chapter.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    // The legacy Dutch notes are short placeholders; prefer the English note when the Dutch one is empty.
                    val note = if (language == AppLanguage.DUTCH) c.getString(6).ifBlank { c.getString(5) } else c.getString(5)
                    add(FatherNote(c.getString(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4), note, c.getString(7)))
                }
            }
        }
    }

    @Serializable
    private data class RelatedRow(val book: String, val chapter: Int? = null, val verse: Int? = null)

    companion object {
        const val DB_VERSION = 1
    }
}
