package agape.app

import agape.app.data.content.QuizFile
import agape.app.data.content.QuizQuestion
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Chapter quizzes are split per book (assets/content/quizzes/BOOK.json), so the app reads only the book it needs. */
class QuizFilesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val dir = File("src/main/assets/content/quizzes")
    private val bookIds = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").findAll(File("src/main/assets/content/books.json").readText())
        .map { it.groupValues[1] }.toSet()

    @Test fun eachBookFileHoldsOnlyItsOwnChapters() {
        val files = dir.listFiles { f -> f.extension == "json" && f.name != "index.json" }.orEmpty()
        assertTrue("no per-book quiz files in $dir", files.isNotEmpty())
        files.forEach { f ->
            val book = f.nameWithoutExtension
            assertTrue("$book is not a book in books.json", book in bookIds)
            val quizzes = json.decodeFromString<Map<String, List<QuizQuestion>>>(f.readText())
            quizzes.forEach { (key, questions) ->
                assertTrue("$key in ${f.name}", key.startsWith("chapter:$book.") && key.substringAfter('.').toIntOrNull() != null)
                assertTrue("$key has too few questions", questions.size >= 3)
            }
        }
    }

    @Test fun theIndexKeepsThePassMarkAndNoChapterQuizzes() {
        val index = json.decodeFromString<QuizFile>(File(dir, "index.json").readText())
        assertTrue(index.passMark in 0.5..1.0)
        assertEquals(emptyList<String>(), index.quizzes.keys.filter { it.startsWith("chapter:") })
    }
}
