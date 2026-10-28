package agape.app.ui.bible

import agape.app.R
import agape.app.core.model.AppLanguage
import agape.app.core.model.Testament
import agape.app.data.content.Book
import agape.app.data.content.ChurchSection
import agape.app.data.content.GOSPELS
import agape.app.data.content.chapterCount
import agape.app.data.content.findBooks
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.Divider
import agape.app.ui.components.Kicker
import agape.app.ui.components.ListRow
import agape.app.ui.components.Pill
import agape.app.ui.components.TabHeader
import agape.app.ui.components.cardShape
import agape.app.ui.onboarding.traditionName
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private enum class BibleTab { BOOKS, CHURCH }

internal enum class BookSection(val label: Int) { OT(R.string.bible_ot), NT(R.string.bible_nt), GOSPELS(R.string.bible_gospels) }

@Composable
fun BibleScreen(
    openChapter: (String, Int) -> Unit,
    openArticle: (String) -> Unit,
    openCouncils: () -> Unit,
    openFathers: () -> Unit,
    openLeaders: () -> Unit,
    openPlans: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(BibleTab.BOOKS) }
    var section by rememberSaveable { mutableStateOf(BookSection.OT) }
    var bookQuery by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val tradition = tradition
    val books = remember(tradition, lang) { container.content.canon(tradition, lang) }

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            TabHeader(kicker = stringResource(traditionName(tradition)), title = stringResource(R.string.tab_bible))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.bible_books), tab == BibleTab.BOOKS, { tab = BibleTab.BOOKS })
                Pill(stringResource(R.string.bible_church), tab == BibleTab.CHURCH, { tab = BibleTab.CHURCH })
            }
        }
        when (tab) {
            BibleTab.BOOKS -> {
                item {
                    AgapeCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), onClick = openPlans, tonal = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.plans_title), style = MaterialTheme.typography.titleMedium, color = AgapeTheme.colors.ink)
                                Text(stringResource(R.string.bible_plans_hint), style = MaterialTheme.typography.bodySmall, color = AgapeTheme.colors.inkMuted)
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = AgapeTheme.colors.inkMuted)
                        }
                    }
                }
                item { BookFilterBar(bookQuery, { bookQuery = it }, section, { section = it }) }
                val shown = filterBooks(books, bookQuery, section, lang)
                items(shown, key = { it.id }) { book ->
                    BookRow(
                        book = book,
                        expanded = expanded == book.id,
                        onToggle = { expanded = if (expanded == book.id) null else book.id },
                        onChapter = { openChapter(book.id, it) },
                    )
                }
                item {
                    Text(
                        stringResource(R.string.bible_translation_note),
                        style = MaterialTheme.typography.bodySmall, color = AgapeTheme.colors.inkFaint,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
            BibleTab.CHURCH -> churchSection(this, tradition, lang, container, openArticle, openCouncils, openFathers, openLeaders)
        }
    }
}

/** A field to find a book by name, and the Old Testament · New Testament · Gospels choice below it. */
@Composable
internal fun BookFilterBar(query: String, onQuery: (String) -> Unit, section: BookSection, onSection: (BookSection) -> Unit) {
    val c = AgapeTheme.colors
    OutlinedTextField(
        value = query, onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        placeholder = { Text(stringResource(R.string.bible_find_book)) },
        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = c.inkMuted) },
        trailingIcon = { if (query.isNotEmpty()) IconButton({ onQuery("") }) { Icon(Icons.Outlined.Close, stringResource(R.string.search_clear), tint = c.inkMuted) } },
        singleLine = true, shape = cardShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.primary, unfocusedBorderColor = c.line,
            focusedContainerColor = c.surface, unfocusedContainerColor = c.surface, cursorColor = c.primary,
        ),
    )
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BookSection.entries.forEach { s -> Pill(stringResource(s.label), query.isEmpty() && section == s, { onQuery(""); onSection(s) }) }
    }
}

/** The books a [BookFilterBar] asks for: those matching the query, or else the chosen section. */
internal fun filterBooks(books: List<Book>, query: String, section: BookSection, lang: AppLanguage): List<Book> =
    if (query.isNotBlank()) books.findBooks(query, lang) else when (section) {
        BookSection.GOSPELS -> books.filter { it.id in GOSPELS }
        BookSection.OT -> books.filter { it.testament == Testament.OT }
        BookSection.NT -> books.filter { it.testament == Testament.NT }
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookRow(book: Book, expanded: Boolean, onToggle: () -> Unit, onChapter: (Int) -> Unit, current: Int? = null) {
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    val chapters = book.chapterCount(lang)
    Column {
        ListRow(
            title = book.name[lang],
            subtitle = if (book.deuterocanonical) stringResource(R.string.bible_deutero) else null,
            onClick = onToggle,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(chapters.toString(), style = MaterialTheme.typography.labelMedium, color = c.inkFaint)
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = c.inkFaint)
                }
            },
        )
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                book.intro?.get(lang)?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.inkMuted, maxLines = 4, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val chapterLabel = stringResource(R.string.bible_chapter)
                    (1..chapters).forEach { ch ->
                        val here = ch == current
                        Box(
                            Modifier.size(48.dp).clip(cardShape).background(if (here) c.primary else c.surface).border(1.dp, if (here) c.primary else c.line, cardShape)
                                .clickable { onChapter(ch) }.semantics { contentDescription = "$chapterLabel $ch" },
                            contentAlignment = Alignment.Center,
                        ) { Text(ch.toString(), style = MaterialTheme.typography.labelLarge, color = if (here) c.onPrimary else c.ink) }
                    }
                }
            }
        }
        Divider()
    }
}

private fun churchSection(
    scope: androidx.compose.foundation.lazy.LazyListScope,
    tradition: agape.app.core.model.Tradition,
    lang: agape.app.core.model.AppLanguage,
    container: agape.app.AppContainer,
    openArticle: (String) -> Unit,
    openCouncils: () -> Unit,
    openFathers: () -> Unit,
    openLeaders: () -> Unit,
) = with(scope) {
    val content = container.content
    item {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChurchTile(
                title = stringResource(R.string.church_councils),
                subtitle = stringResource(R.string.church_councils_count, content.councils(tradition).size),
                symbol = "☩", modifier = Modifier.weight(1f), onClick = openCouncils,
            )
            ChurchTile(
                title = stringResource(R.string.church_fathers),
                subtitle = stringResource(R.string.church_fathers_count, content.fathers(tradition).size),
                symbol = "✠", modifier = Modifier.weight(1f), onClick = openFathers,
            )
        }
    }
    item {
        AgapeCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), onClick = openLeaders) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.leaders_title), style = MaterialTheme.typography.titleLarge, color = AgapeTheme.colors.ink)
                    Text(content.leaders(tradition).joinToString(" · ") { it.current }.take(80), style = MaterialTheme.typography.bodySmall, color = AgapeTheme.colors.inkMuted)
                }
                Chevron()
            }
        }
    }
    item { ChurchHeading(R.string.church_your_tradition) }
    items(content.articles(tradition, ChurchSection.TRADITION), key = { it.id }) { a ->
        AgapeCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), onClick = { openArticle(a.id) }) {
            Text(a.title[lang], style = MaterialTheme.typography.titleLarge, color = AgapeTheme.colors.ink)
            Spacer(Modifier.height(4.dp))
            Text(a.summary[lang], style = MaterialTheme.typography.bodyMedium, color = AgapeTheme.colors.inkMuted)
        }
    }
    item { ChurchHeading(R.string.church_history) }
    items(content.articles(tradition, ChurchSection.HISTORY), key = { it.id }) { a ->
        ListRow(a.title[lang], subtitle = a.summary[lang], onClick = { openArticle(a.id) }, trailing = { Chevron() })
        Divider()
    }
}

@Composable
private fun ChurchTile(title: String, subtitle: String, symbol: String, modifier: Modifier, onClick: () -> Unit) {
    val c = AgapeTheme.colors
    Column(
        modifier.clip(cardShape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(c.primary, c.primary.copy(alpha = 0.78f))))
            .clickable(onClick = onClick).padding(18.dp),
    ) {
        Text(symbol, style = MaterialTheme.typography.headlineMedium, color = c.accent)
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.onPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.onPrimary.copy(alpha = 0.75f))
    }
}

@Composable
private fun ChurchHeading(title: Int) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 4.dp)) {
        Kicker(stringResource(title))
    }
}

@Composable
private fun Chevron() = Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = AgapeTheme.colors.inkFaint)
