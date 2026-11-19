package agape.app.ui.bible

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import agape.app.ui.theme.Spacing
import agape.app.ui.components.Section
import agape.app.R
import agape.app.core.model.AppLanguage
import agape.app.core.model.Passage
import agape.app.data.bible.FatherNote
import agape.app.data.bible.FatherQuote
import agape.app.data.content.Book
import agape.app.data.content.ChapterCommentary
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.bookName
import agape.app.ui.components.Kicker
import agape.app.ui.components.Ornament
import agape.app.ui.components.SegmentedTabs
import agape.app.ui.components.cardShape
import agape.app.ui.passageLabel
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private data class RefPreview(val passage: Passage, val text: String)

private enum class InfoTab(val label: Int) {
    EXPLANATION(R.string.info_tab_explanation), FATHERS(R.string.info_tab_fathers), REFERENCES(R.string.info_tab_refs),
}

/**
 * Everything about a chapter that is not the text itself, opened from the reader's Info button so the Bible text
 * stays clean: the explanation, the Fathers and where else to read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterInfoSheet(book: String, chapter: Int, onDismiss: () -> Unit, openReference: (String, Int, Int) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    // The book's commentary file is read off the main thread; the sheet fills in when it is there.
    val commentary by produceState<ChapterCommentary?>(null, book, chapter) { value = container.content.commentary(book, chapter) }
    val bookInfo = remember(book) { container.content.book(book) }
    val quotes by produceState(emptyList<FatherQuote>(), book, chapter) { value = container.bible.chapterQuotes(book, chapter) }
    val notes by produceState(emptyList<FatherNote>(), book, chapter) { value = container.bible.fatherNotes(book, chapter) }
    val refs by produceState(emptyList<RefPreview>(), book, chapter, lang) {
        value = container.bible.chapterCrossRefs(book, chapter, limit = 10).map { r ->
            val p = Passage(r.book, r.chapter, r.verse, r.chapter, r.verseEnd)
            RefPreview(p, container.bible.passage(p, lang).joinToString(" ") { it.text })
        }.filter { it.text.isNotBlank() }
    }
    var tab by rememberSaveable { mutableStateOf(InfoTab.EXPLANATION) }
    val go: (String, Int, Int) -> Unit = { b, ch, v -> onDismiss(); openReference(b, ch, v) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl).padding(bottom = Spacing.xl).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Kicker(stringResource(R.string.chapter_explanation))
                Spacer(Modifier.height(Spacing.xs))
                Text("${container.bookName(book, lang)} $chapter", style = MaterialTheme.typography.headlineMedium, color = c.ink)
                Ornament(Modifier.padding(vertical = Spacing.s))
                SegmentedTabs(InfoTab.entries.map { stringResource(it.label) }, tab.ordinal, { tab = InfoTab.entries[it] })
            }
            when (tab) {
                InfoTab.EXPLANATION -> Explanation(commentary, bookInfo, lang)
                InfoTab.FATHERS -> Fathers(commentary, notes, quotes.distinctBy { it.father }.take(6), lang, go)
                InfoTab.REFERENCES -> References(refs, lang, go)
            }
        }
    }
}

@Composable
private fun ColumnScope.Explanation(commentary: ChapterCommentary?, book: Book?, lang: AppLanguage) {
    val c = AgapeTheme.colors
    when {
        commentary != null -> {
            Text(commentary.title[lang], style = MaterialTheme.typography.titleLarge, color = c.primary)
            Body(commentary.summary[lang])
            commentary.meaning[lang].takeIf { it.isNotBlank() }?.let { Section(stringResource(R.string.info_meaning), Modifier.padding(top = Spacing.s)) { Body(it) } }
            Themes(commentary.themes[lang])
            // Only the liturgy of the reader's own tradition; the section stays hidden when it has none.
            commentary.liturgy[tradition][lang].takeIf { it.isNotBlank() }?.let { Section(stringResource(R.string.info_liturgy), Modifier.padding(top = Spacing.s)) { Body(it) } }
        }
        else -> Text(stringResource(R.string.info_none), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
    }
    book?.intro?.get(lang)?.takeIf { it.isNotBlank() }?.let { intro ->
        Section(stringResource(R.string.info_about_book, book.name[lang]), Modifier.padding(top = Spacing.s)) { Body(intro) }
    }
}

@Composable
private fun ColumnScope.Fathers(
    commentary: ChapterCommentary?,
    notes: List<FatherNote>,
    quotes: List<FatherQuote>,
    lang: AppLanguage,
    go: (String, Int, Int) -> Unit,
) {
    val c = AgapeTheme.colors
    val container = LocalContainer.current
    val reading = commentary?.fathers?.get(lang).orEmpty()
    if (reading.isBlank() && notes.isEmpty() && quotes.isEmpty()) {
        Text(stringResource(R.string.info_no_fathers), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
        return
    }
    if (reading.isNotBlank()) Section(stringResource(R.string.info_fathers_reading), Modifier.padding(top = Spacing.s)) { Body(reading) }
    if (lang == AppLanguage.DUTCH && notes.isNotEmpty()) {
        Text(stringResource(R.string.english_only_note), style = MaterialTheme.typography.labelSmall, color = c.inkFaint)
    }
    notes.forEach { n ->
        Column {
            Text("${n.father} · ${n.work}", style = MaterialTheme.typography.titleSmall, color = c.primary)
            Body(n.note, Modifier.padding(top = Spacing.xs))
        }
    }
    if (quotes.isNotEmpty()) {
        Section(stringResource(R.string.chapter_fathers), Modifier.padding(top = Spacing.s)) {
            if (lang == AppLanguage.DUTCH) {
                Text(stringResource(R.string.info_quotes_english), style = MaterialTheme.typography.labelSmall, color = c.inkFaint)
            }
            quotes.forEach { q ->
                Column(Modifier.fillMaxWidth().clip(cardShape).clickable { go(q.book, q.chapter, q.verse) }.padding(vertical = Spacing.s)) {
                    Text(
                        "${q.father} · ${container.passageLabel(Passage(q.book, q.chapter, q.verse), lang)}",
                        style = MaterialTheme.typography.titleSmall, color = c.primary,
                    )
                    Text(q.text, style = MaterialTheme.typography.bodyMedium, color = c.ink, maxLines = 8, modifier = Modifier.padding(top = Spacing.xs))
                    if (q.source.isNotBlank()) Text(q.source, style = MaterialTheme.typography.labelSmall, color = c.inkFaint, modifier = Modifier.padding(top = Spacing.xxs))
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.References(refs: List<RefPreview>, lang: AppLanguage, go: (String, Int, Int) -> Unit) {
    val c = AgapeTheme.colors
    val container = LocalContainer.current
    if (refs.isEmpty()) {
        Text(stringResource(R.string.info_no_refs), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
        return
    }
    refs.forEach { r ->
        Column(
            Modifier.fillMaxWidth().clip(cardShape).background(c.surfaceSunken.copy(alpha = 0.5f))
                .clickable { go(r.passage.book, r.passage.chapter, r.passage.startVerse ?: 0) }.padding(Spacing.m),
        ) {
            Text(container.passageLabel(r.passage, lang), style = MaterialTheme.typography.labelLarge, color = c.primary)
            Text(r.text, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, maxLines = 3, modifier = Modifier.padding(top = Spacing.xxs))
        }
    }
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = AgapeTheme.colors.ink, modifier = modifier)
}

@Composable
private fun Themes(themes: List<String>) {
    if (themes.isEmpty()) return
    Text(themes.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = AgapeTheme.colors.accentInk)
}

/** The quiet close of a chapter: only the quiz, and the book quiz after the last chapter. */
@Composable
fun ChapterQuizRow(book: String, chapter: Int, lastOfBook: Book?, openQuiz: (String, String) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val title = "${container.bookName(book, lang)} $chapter"
    Row(Modifier.fillMaxWidth().padding(top = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterHorizontally)) {
        QuizChip(stringResource(R.string.chapter_quiz)) { openQuiz("chapter:$book.$chapter", title) }
        lastOfBook?.let { b -> QuizChip(stringResource(R.string.book_end_quiz)) { openQuiz("book:${b.id}", b.name[lang]) } }
    }
}

@Composable
private fun QuizChip(text: String, onClick: () -> Unit) {
    val c = AgapeTheme.colors
    Row(
        Modifier.clip(CircleShape).border(1.dp, c.line, CircleShape).clickable(onClick = onClick).padding(horizontal = Spacing.m, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(Icons.Outlined.Quiz, null, tint = c.accent, modifier = Modifier.height(18.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.ink)
    }
}
