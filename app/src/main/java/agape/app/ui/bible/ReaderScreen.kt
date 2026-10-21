package agape.app.ui.bible

import agape.app.R
import agape.app.core.prefs.ReadingPosition
import agape.app.data.bible.ChapterGuide
import agape.app.data.bible.Verse
import agape.app.data.content.chapterCount
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.LocalSettings
import agape.app.ui.bookName
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Kicker
import agape.app.ui.components.Ornament
import agape.app.ui.components.PrimaryButton
import agape.app.ui.components.SecondaryButton
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    book: String,
    chapter: Int,
    focusVerse: Int,
    onBack: () -> Unit,
    openChapter: (String, Int, Int) -> Unit,
    openReference: (String, Int, Int) -> Unit,
    openQuiz: (String, String) -> Unit,
) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val settings = LocalSettings.current
    val tradition = tradition
    val scope = rememberCoroutineScope()
    val c = AgapeTheme.colors
    val bookInfo = remember(book) { container.content.book(book) }
    val title = "${container.bookName(book, lang)} $chapter"
    val chapterCount = bookInfo?.chapterCount(lang) ?: chapter

    val verses by produceState<List<Verse>?>(null, book, chapter, lang) { value = container.bible.chapter(book, chapter, lang) }
    val guide by produceState<ChapterGuide?>(null, book, chapter, lang) { value = container.bible.chapterGuide(book, chapter, lang) }
    val highlights by remember(book, chapter) { container.user.highlightsIn(book, chapter) }.collectAsStateWithLifecycle(emptyList())
    val bookmarks by container.user.bookmarks.collectAsStateWithLifecycle(emptyList())
    val notes by container.user.notes.collectAsStateWithLifecycle(emptyList())
    val read by container.user.chaptersRead.collectAsStateWithLifecycle(emptyList())
    val isRead = read.any { it.book == book && it.chapter == chapter }
    val quizKey = "chapter:$book.$chapter".takeIf { container.content.quiz(it) != null }

    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var flash by remember { mutableStateOf(focusVerse) }
    var fontMenu by remember { mutableStateOf(false) }
    val list = rememberLazyListState()

    LaunchedEffect(book, chapter) {
        container.settings.setLastPosition(ReadingPosition(book, chapter, focusVerse.coerceAtLeast(1)))
    }
    LaunchedEffect(verses, focusVerse) {
        val vs = verses ?: return@LaunchedEffect
        val index = vs.indexOfFirst { it.verse == focusVerse }
        if (index >= 0) {
            list.scrollToItem(index + 1) // +1 for the header item
            delay(1600)
            flash = 0
        }
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title, onBack) {
            IconButton(onClick = { showGuide = !showGuide }) {
                Icon(Icons.Outlined.Info, stringResource(R.string.reader_guide), tint = if (showGuide) c.primary else c.inkMuted)
            }
            Column {
                IconButton(onClick = { fontMenu = true }) { Icon(Icons.Outlined.FormatSize, stringResource(R.string.settings_font_size), tint = c.inkMuted) }
                DropdownMenu(fontMenu, onDismissRequest = { fontMenu = false }) {
                    listOf(0.9f to R.string.font_small, 1f to R.string.font_normal, 1.15f to R.string.font_large, 1.3f to R.string.font_xlarge).forEach { (scale, label) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label), color = if (settings.fontScale == scale) c.primary else c.ink) },
                            onClick = { fontMenu = false; scope.launch { container.settings.setFontScale(scale) } },
                        )
                    }
                }
            }
        }
        LazyColumn(state = list, modifier = Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Kicker(container.bookName(book, lang))
                    Text(chapter.toString(), style = MaterialTheme.typography.displayMedium, color = c.primary)
                    Ornament()
                    val g = guide
                    if (showGuide && g != null) {
                        Spacer(Modifier.height(14.dp))
                        AgapeCard(tonal = true) {
                            Text(g.title, style = MaterialTheme.typography.titleLarge, color = c.ink)
                            Spacer(Modifier.height(6.dp))
                            Text(g.summary, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
                            if (g.themes.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(g.themes.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = c.accent)
                            }
                        }
                    }
                }
            }
            val vs = verses
            if (vs == null) item { Spacer(Modifier.height(200.dp)) }
            else items(vs, key = { it.verse }) { v ->
                val hl = highlights.firstOrNull { it.verse == v.verse }?.color?.let { c.highlights.getOrNull(it) }
                val target = when {
                    flash == v.verse || selected == v.verse -> c.accent.copy(alpha = 0.18f)
                    hl != null -> hl
                    else -> Color.Transparent
                }
                val bg by animateColorAsState(target, label = "verse")
                val marked = bookmarks.any { it.book == book && it.chapter == chapter && it.verse == v.verse }
                val hasNote = notes.any { it.book == book && it.chapter == chapter && it.verse == v.verse }
                val verseLabel = stringResource(R.string.reader_verse_a11y, v.verse)
                Row(
                    Modifier.fillMaxWidth().background(bg).clickable { selected = v.verse }
                        .semantics { contentDescription = "$verseLabel. ${v.text}" }
                        .padding(vertical = 3.dp, horizontal = 4.dp),
                ) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = c.accent, fontSize = AgapeTheme.type.verseNumber.fontSize, fontFamily = AgapeTheme.type.verseNumber.fontFamily)) {
                                append("${v.verse}  ")
                            }
                            append(v.text)
                            if (marked || hasNote) withStyle(SpanStyle(color = c.accent)) { append(if (marked) "  ◆" else "  ✎") }
                        },
                        style = AgapeTheme.type.scripture, color = c.ink,
                    )
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Ornament()
                    Spacer(Modifier.height(6.dp))
                    if (!isRead) {
                        PrimaryButton(stringResource(R.string.reader_mark_read), {
                            scope.launch { container.user.markChapterRead(book, chapter, title) }
                        })
                    } else {
                        Text(stringResource(R.string.reader_read_done), style = MaterialTheme.typography.labelLarge, color = c.primary)
                    }
                    if (quizKey != null) SecondaryButton(stringResource(R.string.reader_take_quiz), { openQuiz(quizKey, title) })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (chapter > 1) SecondaryButton("‹ ${chapter - 1}", { openChapter(book, chapter - 1, 0) }, Modifier.width(120.dp))
                        else Spacer(Modifier.width(1.dp))
                        if (chapter < chapterCount) SecondaryButton("${chapter + 1} ›", { openChapter(book, chapter + 1, 0) }, Modifier.width(120.dp))
                    }
                }
            }
        }
    }

    selected?.let { verse ->
        val v = verses?.firstOrNull { it.verse == verse } ?: return@let
        LaunchedEffect(verse) { container.settings.setLastPosition(ReadingPosition(book, chapter, verse)) }
        VerseSheet(
            verse = v,
            tradition = tradition,
            highlight = highlights.firstOrNull { it.verse == verse }?.color,
            bookmarked = bookmarks.any { it.book == book && it.chapter == chapter && it.verse == verse },
            note = notes.firstOrNull { it.book == book && it.chapter == chapter && it.verse == verse }?.text.orEmpty(),
            onDismiss = { selected = null },
            openReference = { b, ch, vv -> selected = null; openReference(b, ch, vv) },
        )
    }
}
