package agape.app.ui.bible

import agape.app.R
import agape.app.core.model.AppLanguage
import agape.app.core.model.Passage
import agape.app.data.bible.ChapterGuide
import agape.app.data.bible.FatherNote
import agape.app.data.bible.FatherQuote
import agape.app.data.content.Book
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.bookName
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.Kicker
import agape.app.ui.components.Ornament
import agape.app.ui.components.PrimaryButton
import agape.app.ui.components.SecondaryButton
import agape.app.ui.components.cardShape
import agape.app.ui.passageLabel
import agape.app.ui.theme.AgapeTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class RefPreview(val passage: Passage, val text: String)

/** What follows the last verse of a chapter: what it was about, what the Fathers said, where else to read, and a quiz. */
@Composable
fun ChapterEnd(
    book: String,
    chapter: Int,
    guide: ChapterGuide?,
    openReference: (String, Int, Int) -> Unit,
    openQuiz: (String, String) -> Unit,
) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    val title = "${container.bookName(book, lang)} $chapter"
    val quotes by produceState(emptyList<FatherQuote>(), book, chapter) { value = container.bible.chapterQuotes(book, chapter) }
    val notes by produceState(emptyList<FatherNote>(), book, chapter, lang) { value = container.bible.fatherNotes(book, chapter, lang) }
    val refs by produceState(emptyList<RefPreview>(), book, chapter, lang) {
        value = container.bible.chapterCrossRefs(book, chapter, limit = 8).map { r ->
            val p = Passage(r.book, r.chapter, r.verse, r.chapter, r.verseEnd)
            RefPreview(p, container.bible.passage(p, lang).joinToString(" ") { it.text })
        }.filter { it.text.isNotBlank() }
    }
    // One voice per Father, so the section reads as a conversation rather than a list.
    val perFather = remember(quotes) { quotes.distinctBy { it.father }.take(5) }

    Column(Modifier.fillMaxWidth().padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Ornament(Modifier.align(Alignment.CenterHorizontally))
        if (guide != null) {
            EndCard(stringResource(R.string.chapter_explanation)) {
                Text(guide.title, style = MaterialTheme.typography.titleLarge, color = c.ink)
                Spacer(Modifier.height(8.dp))
                Text(guide.summary, style = MaterialTheme.typography.bodyLarge, color = c.ink)
                if (guide.context.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(guide.context, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
                }
                if (guide.themes.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(guide.themes.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = c.accent)
                }
                EnglishNote(lang)
            }
        }
        if (perFather.isNotEmpty() || notes.isNotEmpty()) {
            EndCard(stringResource(R.string.chapter_fathers)) {
                notes.forEach { n ->
                    Text("${n.father} · ${n.work}", style = MaterialTheme.typography.titleSmall, color = c.primary)
                    Text(n.note, style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                }
                perFather.forEach { q ->
                    Text(
                        "${q.father} · ${container.passageLabel(Passage(q.book, q.chapter, q.verse), lang)}",
                        style = MaterialTheme.typography.titleSmall, color = c.primary,
                        modifier = Modifier.clickable { openReference(q.book, q.chapter, q.verse) },
                    )
                    Text(q.text, style = MaterialTheme.typography.bodyMedium, color = c.ink, maxLines = 7, modifier = Modifier.padding(top = 4.dp))
                    if (q.source.isNotBlank()) Text(q.source, style = MaterialTheme.typography.labelSmall, color = c.inkFaint, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                }
                EnglishNote(lang)
            }
        }
        if (refs.isNotEmpty()) {
            EndCard(stringResource(R.string.chapter_cross_refs)) {
                refs.forEach { r ->
                    Column(Modifier.fillMaxWidth().clip(cardShape).clickable { openReference(r.passage.book, r.passage.chapter, r.passage.startVerse ?: 0) }.padding(vertical = 6.dp)) {
                        Text(container.passageLabel(r.passage, lang), style = MaterialTheme.typography.labelLarge, color = c.primary)
                        Text(r.text, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, maxLines = 2)
                    }
                }
            }
        }
        SecondaryButton(stringResource(R.string.chapter_quiz), { openQuiz("chapter:$book.$chapter", title) })
    }
}

/** The close of a whole book: its summary, the Fathers who wrote most about it, and a twenty-question quiz. */
@Composable
fun BookEnd(book: Book, openReference: (String, Int, Int) -> Unit, openQuiz: (String, String) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    val quotes by produceState(emptyList<FatherQuote>(), book.id) { value = container.bible.bookQuotes(book.id, limit = 6) }
    val name = book.name[lang]
    Column(Modifier.fillMaxWidth().padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(cardShape)
                .background(Brush.linearGradient(listOf(c.primary, c.primary.copy(alpha = 0.85f), c.accent.copy(alpha = 0.7f))))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Kicker(stringResource(R.string.book_end_kicker), color = c.onPrimary.copy(alpha = 0.8f))
            Spacer(Modifier.height(8.dp))
            Text(name, style = MaterialTheme.typography.displaySmall, color = c.onPrimary, textAlign = TextAlign.Center)
            book.intro?.get(lang)?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge, color = c.onPrimary.copy(alpha = 0.92f))
            }
        }
        if (quotes.isNotEmpty()) {
            EndCard(stringResource(R.string.book_end_fathers, name)) {
                quotes.forEach { q ->
                    Text(
                        "${q.father} · ${container.passageLabel(Passage(q.book, q.chapter, q.verse), lang)}",
                        style = MaterialTheme.typography.titleSmall, color = c.primary,
                        modifier = Modifier.clickable { openReference(q.book, q.chapter, q.verse) },
                    )
                    Text(q.text, style = MaterialTheme.typography.bodyMedium, color = c.ink, maxLines = 6, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                }
                EnglishNote(lang)
            }
        }
        PrimaryButton(stringResource(R.string.book_end_quiz), { openQuiz("book:${book.id}", name) })
    }
}

@Composable
private fun EndCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    AgapeCard {
        Kicker(title)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun EnglishNote(lang: AppLanguage) {
    if (lang == AppLanguage.DUTCH) {
        Text(stringResource(R.string.english_only_note), style = MaterialTheme.typography.labelSmall, color = AgapeTheme.colors.inkFaint, modifier = Modifier.padding(top = 6.dp))
    }
}
