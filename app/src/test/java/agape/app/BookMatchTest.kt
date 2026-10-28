package agape.app

import agape.app.core.model.AppLanguage
import agape.app.core.model.L10n
import agape.app.core.model.Testament
import agape.app.core.model.Tradition
import agape.app.data.content.Book
import agape.app.data.content.findBooks
import agape.app.data.content.isGospelQuery
import agape.app.data.content.nameStarts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookMatchTest {
    private fun book(id: String, order: Int, en: String, nl: String, t: Testament = Testament.NT) =
        Book(id, order, t, false, Tradition.entries, L10n(en, nl), chapters = mapOf("en" to 1, "nl" to 1))

    private val canon = listOf(
        book("GEN", 1, "Genesis", "Genesis", Testament.OT),
        book("1CO", 60, "1 Corinthians", "1 Korintiërs"),
        book("MAT", 55, "Matthew", "Matteüs"),
        book("MRK", 56, "Mark", "Marcus"),
        book("LUK", 57, "Luke", "Lucas"),
        book("JHN", 58, "John", "Johannes"),
        book("1JN", 80, "1 John", "1 Johannes"),
    )

    @Test fun ignoresAccentsAndKnowsCommonSpellings() {
        val mat = canon.first { it.id == "MAT" }
        assertTrue(mat.nameStarts("Matteus", AppLanguage.DUTCH))
        assertTrue(mat.nameStarts("Mattheüs", AppLanguage.DUTCH))
        assertTrue(canon.first { it.id == "MRK" }.nameStarts("Markus", AppLanguage.DUTCH))
        assertTrue(canon.first { it.id == "1CO" }.nameStarts("1 korintiers", AppLanguage.DUTCH))
        assertFalse(mat.nameStarts("Marcus", AppLanguage.DUTCH))
    }

    @Test fun gospelWordsListTheFourGospelsInOrder() {
        assertTrue(isGospelQuery("Evangeliën"))
        assertTrue(isGospelQuery("gospels"))
        assertFalse(isGospelQuery("eva"))
        assertEquals(listOf("MAT", "MRK", "LUK", "JHN"), canon.findBooks("evangelie", AppLanguage.DUTCH).map { it.id })
    }

    @Test fun gospelsComeFirstAmongMatches() {
        assertEquals(listOf("JHN", "1JN"), canon.findBooks("johannes", AppLanguage.DUTCH).map { it.id })
    }
}
