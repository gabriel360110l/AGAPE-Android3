package agape.app

import agape.app.core.model.Tradition
import agape.app.data.content.BookCommentary
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommentaryLiturgyTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun chapterWithoutLiturgyStillReads() {
        val book = json.decodeFromString<BookCommentary>(
            """{"book":"X","chapters":[{"chapter":1,"title":{"en":"T","nl":"T"},"summary":{"en":"S","nl":"S"}}]}""",
        )
        Tradition.entries.forEach { assertEquals("", book.chapters[0].liturgy[it].en) }
    }

    @Test fun eachTraditionGetsOnlyItsOwnText() {
        val book = json.decodeFromString<BookCommentary>(
            """{"book":"X","chapters":[{"chapter":1,"title":{"en":"T","nl":"T"},"summary":{"en":"S","nl":"S"},
               "liturgy":{"eo":{"en":"Byzantine","nl":"Byzantijns"},"ca":{"en":"Roman","nl":"Romeins"}}}]}""",
        )
        val liturgy = book.chapters[0].liturgy
        assertEquals("Byzantine", liturgy[Tradition.EASTERN_ORTHODOX].en)
        assertEquals("Romeins", liturgy[Tradition.CATHOLIC].nl)
        assertEquals("", liturgy[Tradition.ORIENTAL_ORTHODOX].en)
    }

    @Test fun builtCommentaryReadsWithLiturgy() {
        val dir = File("src/main/assets/content/commentary")
        if (!dir.isDirectory) return
        var withLiturgy = 0
        dir.listFiles { f -> f.extension == "json" }!!.forEach { f ->
            json.decodeFromString<BookCommentary>(f.readText()).chapters.forEach { c ->
                Tradition.entries.forEach { t ->
                    val l = c.liturgy[t]
                    assertEquals("${f.name} ${c.chapter} $t", l.en.isBlank(), l.nl.isBlank())
                    if (l.en.isNotBlank()) withLiturgy++
                }
            }
        }
        assertTrue(withLiturgy > 0)
    }
}
