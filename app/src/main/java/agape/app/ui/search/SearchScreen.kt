package agape.app.ui.search

import agape.app.R
import agape.app.core.model.AppLanguage
import agape.app.core.model.L10n
import agape.app.core.model.Passage
import agape.app.core.model.Tradition
import agape.app.data.content.chapterCount
import agape.app.data.content.GOSPELS
import agape.app.data.content.nameStarts
import agape.app.data.content.findBooks
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.Kicker
import agape.app.ui.components.ListRow
import agape.app.ui.components.Pill
import agape.app.ui.components.TabHeader
import agape.app.ui.components.cardShape
import agape.app.ui.passageLabel
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import agape.app.core.model.Testament
import agape.app.data.content.Book
import agape.app.data.content.fold
import agape.app.ui.components.QuietButton
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where a result leads. */
sealed interface SearchTarget {
    data class Verse(val book: String, val chapter: Int, val verse: Int) : SearchTarget
    data class Hour(val id: String) : SearchTarget
    data class Prayer(val id: String) : SearchTarget
    data class Council(val id: String) : SearchTarget
    data class Father(val id: String) : SearchTarget
    data class Article(val id: String) : SearchTarget
    data class Plan(val id: String) : SearchTarget
    data object Calendar : SearchTarget
}

private enum class Kind(val label: Int) {
    ALL(R.string.search_all), BIBLE(R.string.search_bible), PRAYER(R.string.search_prayers),
    CHURCH(R.string.search_church), FATHERS(R.string.search_fathers), SAINTS(R.string.search_saints), PLANS(R.string.search_plans),
}

/** Which part of the Bible a search looks in. */
private enum class Scope(val label: Int) {
    WHOLE(R.string.search_scope_whole), OT(R.string.search_scope_ot), NT(R.string.search_scope_nt),
    GOSPELS(R.string.search_scope_gospels), PSALMS(R.string.search_scope_psalms),
}

private data class Hit(val kind: Kind, val title: String, val subtitle: String, val target: SearchTarget)

/** What the search shows: nothing yet, the hits of a query, or a failure (shown, never a crash). */
private data class Results(val query: String, val hits: List<Hit>, val failed: Boolean = false)

@Composable
fun SearchScreen(open: (SearchTarget) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val tradition = tradition
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(Kind.ALL) }
    var bibleScope by rememberSaveable { mutableStateOf(Scope.WHOLE) }
    var wholeWord by rememberSaveable { mutableStateOf(false) }
    var results by remember { mutableStateOf(Results("", emptyList())) }
    var searching by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    val recent by container.settings.recentSearches.collectAsStateWithLifecycle(emptyList())
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val c = AgapeTheme.colors

    LaunchedEffect(query, lang, tradition, bibleScope, wholeWord, attempt) {
        val q = query.trim()
        if (q.length < 2) { results = Results("", emptyList()); searching = false; return@LaunchedEffect }
        searching = true
        try {
            delay(250) // let the user finish typing
            // All the scanning happens off the main thread, so typing stays smooth however large the texts are.
            val hits = withContext(Dispatchers.Default) { search(container, q, lang, tradition, bibleScope, wholeWord) }
            results = Results(q, hits)
        } catch (e: CancellationException) {
            throw e // a newer query took over
        } catch (e: Exception) {
            Log.w("AgapeSearch", "search failed for \"$q\"", e)
            results = Results(q, emptyList(), failed = true)
        } finally {
            searching = false
        }
    }

    fun go(target: SearchTarget) {
        if (query.isNotBlank()) scope.launch { container.settings.addRecentSearch(query.trim()) }
        keyboard?.hide()
        open(target)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { TabHeader(kicker = "", title = stringResource(R.string.tab_search)) }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = c.inkMuted) },
                trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.search_clear), tint = c.inkMuted) } },
                singleLine = true, shape = cardShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.primary, unfocusedBorderColor = c.line,
                    focusedContainerColor = c.surface, unfocusedContainerColor = c.surface, cursorColor = c.primary,
                    focusedTextColor = c.ink, unfocusedTextColor = c.ink,
                ),
            )
            // What to show: everything, or one kind of result, each with its count.
            val counts = results.hits.groupingBy { it.kind }.eachCount()
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Kind.entries.forEach { k ->
                    val n = if (k == Kind.ALL) results.hits.size else counts[k] ?: 0
                    if (k == Kind.ALL || results.hits.isEmpty() || n > 0 || kind == k) {
                        Pill(stringResource(k.label) + if (results.hits.isNotEmpty()) " · $n" else "", kind == k, { kind = k })
                    }
                }
            }
            // Where in the Bible to look, and whether to match whole words only.
            if (kind == Kind.ALL || kind == Kind.BIBLE) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Scope.entries.forEach { s -> ScopeChip(stringResource(s.label), bibleScope == s) { bibleScope = s } }
                    ScopeChip(stringResource(R.string.search_whole_word), wholeWord) { wholeWord = !wholeWord }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (searching) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp), color = c.accent, trackColor = c.line,
                )
            }
        }
        if (query.trim().length < 2) {
            if (recent.isNotEmpty()) {
                item { Kicker(stringResource(R.string.search_recent), Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)) }
                items(recent, key = { "r$it" }) { r ->
                    ListRow(r, onClick = { query = r }, leading = { Icon(Icons.Outlined.History, null, tint = c.inkFaint) })
                }
            } else item {
                Text(stringResource(R.string.search_empty_hint), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, modifier = Modifier.padding(24.dp))
            }
        } else if (results.failed) {
            item {
                Column(Modifier.padding(24.dp)) {
                    Text(stringResource(R.string.search_failed), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
                    QuietButton(stringResource(R.string.search_retry), { attempt++ })
                }
            }
        } else {
            val shown = results.hits.filter { kind == Kind.ALL || it.kind == kind }
            if (shown.isEmpty() && !searching && results.query == query.trim()) item {
                Text(stringResource(R.string.search_none, query.trim()), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, modifier = Modifier.padding(24.dp))
            }
            shown.groupBy { it.kind }.forEach { (k, list) ->
                val limit = if (kind != Kind.ALL) list.size else if (k == Kind.BIBLE) 12 else 6
                item(key = "h$k") { Kicker(stringResource(k.label) + " · ${list.size}", Modifier.padding(start = 20.dp, top = 16.dp, bottom = 2.dp)) }
                // Keys by position: two results may well look the same (one name on two days, one Father on one verse).
                itemsIndexed(list.take(limit), key = { i, _ -> "$k#$i" }) { _, h ->
                    ResultRow(h, results.query) { go(h.target) }
                }
                if (list.size > limit) item(key = "more$k") {
                    QuietButton(stringResource(R.string.search_show_all, list.size), { kind = k }, Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ScopeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = AgapeTheme.colors
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = if (selected) c.primary else c.inkMuted,
        modifier = Modifier.clip(CircleShape).border(1.dp, if (selected) c.primary else c.line, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** A result, with the words searched for marked in gold. */
@Composable
private fun ResultRow(hit: Hit, query: String, onClick: () -> Unit) {
    val c = AgapeTheme.colors
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(marked(hit.title, query, c.primary), style = MaterialTheme.typography.titleMedium, color = c.ink)
        if (hit.subtitle.isNotBlank()) {
            Text(marked(hit.subtitle, query, c.primary), style = MaterialTheme.typography.bodySmall, color = c.inkMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
    Box(Modifier.padding(start = 20.dp).fillMaxWidth().height(0.5.dp).background(c.line))
}

/** [text] with every occurrence of [query] in bold and [color], ignoring case and accents. */
private fun marked(text: String, query: String, color: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    val q = fold(query)
    if (q.isEmpty()) return@buildAnnotatedString
    // Folding can change length (é → e + accent, removed), so match per character position on the folded text.
    val folded = text.map { fold(it.toString()).ifEmpty { " " }.first() }.joinToString("")
    var from = 0
    while (true) {
        val i = folded.indexOf(q, from)
        if (i < 0 || q.length == 0) break
        addStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold), i, (i + q.length).coerceAtMost(text.length))
        from = i + q.length
    }
}

/** One searchable thing outside the Bible, with all its text folded once (lower case, no accents). */
private class Doc(val hit: Hit, val text: String)

/** The folded texts of a tradition in a language, built once: folding every prayer and article on each keystroke was slow. */
private object SearchIndex {
    private var key: Pair<Tradition, AppLanguage>? = null
    private var docs: List<Doc> = emptyList()

    @Synchronized
    fun docs(container: agape.app.AppContainer, tradition: Tradition, lang: AppLanguage): List<Doc> {
        if (key == tradition to lang) return docs
        docs = build(container, tradition, lang)
        key = tradition to lang
        return docs
    }

    private fun build(container: agape.app.AppContainer, tradition: Tradition, lang: AppLanguage): List<Doc> {
        val content = container.content
        val out = mutableListOf<Doc>()
        fun add(hit: Hit, vararg parts: String?) { out += Doc(hit, fold(parts.filterNotNull().joinToString(" \u0001 "))) }
        fun L10n.both() = if (lang == AppLanguage.ENGLISH) en else this[lang] + " \u0001 " + en

        val book = content.prayerBook(tradition)
        (book.hours + book.extras).forEach { h ->
            add(Hit(Kind.PRAYER, h.name[lang], h.subtitle[lang], SearchTarget.Hour(h.id)), h.name.both(), h.subtitle.both(), h.theme.both(), *h.sections.map { it.title?.both() }.toTypedArray())
        }
        content.listedPrayers(tradition).forEach {
            add(Hit(Kind.PRAYER, it.title[lang], it.subtitle[lang], SearchTarget.Prayer(it.id)), it.title.both(), it.subtitle[lang], it.body[lang].joinToString(" "))
        }
        content.councils.forEach {
            add(Hit(Kind.CHURCH, it.name[lang], "${it.year} · ${it.heresy[lang]}", SearchTarget.Council(it.id)), it.name.both(), it.heresy.both(), it.year, it.keyFigures.joinToString(" "), it.summary.both())
        }
        content.fathers(tradition).forEach {
            add(Hit(Kind.CHURCH, it.name[lang], "${it.era} · ${it.summary[lang]}", SearchTarget.Father(it.id)), it.name.both(), it.summary.both(), it.place.both())
        }
        (content.articles(tradition, agape.app.data.content.ChurchSection.TRADITION) + content.articles(tradition, agape.app.data.content.ChurchSection.HISTORY)).forEach {
            add(Hit(Kind.CHURCH, it.title[lang], it.summary[lang], SearchTarget.Article(it.id)), it.title.both(), it.summary[lang], it.body[lang].joinToString(" "))
        }
        val traditionId = agape.app.data.saints.SaintCalendar.id(tradition)
        content.saintsUnified.commemorations.filter { it.tradition == traditionId }.forEach { s ->
            val date = if (s.date.calendar == "coptic") "${s.date.day} ${agape.app.data.calendar.CopticDate(0, s.date.month, 1).monthName(lang == AppLanguage.DUTCH)}"
                else "%02d-%02d".format(s.date.day, s.date.month)
            add(Hit(Kind.SAINTS, s.name[lang], date, SearchTarget.Calendar), s.name.both())
        }
        content.plans.forEach {
            add(Hit(Kind.PLANS, it.title[lang], it.description[lang], SearchTarget.Plan(it.id)), it.title.both(), it.description.both())
        }
        return out
    }
}

/** Searches everything the app holds, offline. Runs off the main thread. */
private suspend fun search(container: agape.app.AppContainer, q: String, lang: AppLanguage, tradition: Tradition, scope: Scope, wholeWord: Boolean): List<Hit> {
    val content = container.content
    val out = mutableListOf<Hit>()
    val canon = content.canon(tradition, lang)
    val fq = fold(q)
    val word = if (wholeWord) Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(fq) + "(?![\\p{L}\\p{N}])") else null
    fun matches(folded: String) = if (word != null) word.containsMatchIn(folded) else folded.contains(fq)
    val inScope: (Book) -> Boolean = when (scope) {
        Scope.WHOLE -> { _ -> true }
        Scope.OT -> { b -> b.testament == Testament.OT }
        Scope.NT -> { b -> b.testament == Testament.NT }
        Scope.GOSPELS -> { b -> b.id in GOSPELS }
        Scope.PSALMS -> { b -> b.id == "PSA" }
    }

    // "John 3:16", "joh 3", "Matteus 5" → straight to the passage.
    Regex("""^(\d?\s?\p{L}[\p{L} ]*?)\s*(\d+)(?:[:.,](\d+))?$""").matchEntire(q)?.let { m ->
        val book = canon.firstOrNull { b -> b.nameStarts(m.groupValues[1], lang) }
        val ch = m.groupValues[2].toInt()
        if (book != null && ch in 1..book.chapterCount(lang)) {
            val v = m.groupValues[3].toIntOrNull()
            out += Hit(Kind.BIBLE, container.passageLabel(Passage(book.id, ch, v), lang), "", SearchTarget.Verse(book.id, ch, v ?: 0))
        }
    }
    // "Evangelie" or "Gospels" lists the four Gospels; otherwise the books whose name matches, Gospels first.
    canon.findBooks(q, lang).filter(inScope).forEach {
        out += Hit(Kind.BIBLE, it.name[lang], it.intro?.get(lang)?.take(120).orEmpty(), SearchTarget.Verse(it.id, 1, 0))
    }
    // Verses: Gospels first, then the rest of the canon in order. SQLite only ignores the case of plain letters, so
    // the database finds candidates and the folded text decides.
    val books = canon.filter(inScope).map { it.id }
    val order = GOSPELS + canon.map { it.id }.filterNot { it in GOSPELS }
    if (q.length >= 3) {
        val candidates = container.bible.searchVerses(q, lang, order, limit = if (wholeWord) 1000 else 300, books = if (scope == Scope.WHOLE) null else books)
        candidates.filter { matches(fold(it.text)) }.take(200).forEach { v ->
            out += Hit(Kind.BIBLE, container.passageLabel(Passage(v.book, v.chapter, v.verse), lang), v.text, SearchTarget.Verse(v.book, v.chapter, v.verse))
        }
    }

    SearchIndex.docs(container, tradition, lang).filter { matches(it.text) }.forEach { out += it.hit }

    if (q.length >= 3) container.bible.searchQuotes(q).filter { f -> books.isEmpty() || scope == Scope.WHOLE || f.book in books }.forEach { f ->
        out += Hit(Kind.FATHERS, "${f.father} · ${container.passageLabel(Passage(f.book, f.chapter, f.verse), lang)}", f.text, SearchTarget.Verse(f.book, f.chapter, f.verse))
    }
    return out
}
