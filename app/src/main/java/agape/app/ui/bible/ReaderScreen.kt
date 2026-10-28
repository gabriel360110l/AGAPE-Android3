package agape.app.ui.bible

import agape.app.R
import agape.app.core.prefs.ReadingPosition
import agape.app.data.bible.Verse
import agape.app.data.content.chapterCount
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.LocalSettings
import agape.app.ui.bookName
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Ornament
import agape.app.ui.components.PrimaryButton
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
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import agape.app.ui.components.AgapeCard
import androidx.compose.runtime.DisposableEffect
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
    val highlights by remember(book, chapter) { container.user.highlightsIn(book, chapter) }.collectAsStateWithLifecycle(emptyList())
    val bookmarks by container.user.bookmarks.collectAsStateWithLifecycle(emptyList())
    val notes by container.user.notes.collectAsStateWithLifecycle(emptyList())
    val read by container.user.chaptersRead.collectAsStateWithLifecycle(emptyList())
    val isRead = read.any { it.book == book && it.chapter == chapter }

    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var flash by remember { mutableStateOf(focusVerse) }
    var fontMenu by remember { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var imageVerse by rememberSaveable { mutableStateOf<Int?>(null) }
    val list = rememberLazyListState()

    // Reading aloud: one part per verse, the chapter title before the first. The verse being read is marked and kept in view.
    val speaker = container.speaker
    val audio by speaker.state.collectAsStateWithLifecycle()
    val audioKey = "chapter:$book:$chapter:${lang.tag}"
    val reading = audio.key == audioKey
    val readingVerse = if (reading) verses?.getOrNull(audio.index)?.verse else null
    DisposableEffect(audioKey) { onDispose { speaker.stopIf(audioKey) } }
    LaunchedEffect(readingVerse) {
        val index = verses?.indexOfFirst { it.verse == readingVerse } ?: -1
        if (index >= 0 && list.layoutInfo.visibleItemsInfo.none { it.index == index + 1 && it.offset >= 0 }) list.animateScrollToItem(index + 1)
    }
    fun listen(from: Int = 0) {
        val vs = verses ?: return
        speaker.play(audioKey, vs.mapIndexed { i, v -> if (i == 0) "$title. ${v.text}" else v.text }, lang, from)
    }

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
            IconButton(onClick = { if (reading) speaker.stop() else listen() }) {
                Icon(
                    if (reading) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp,
                    stringResource(if (reading) R.string.listen_stop else R.string.listen),
                    tint = if (reading) c.accent else c.primary,
                )
            }
            IconButton(onClick = { showInfo = true }) {
                Icon(Icons.Outlined.Info, stringResource(R.string.reader_guide), tint = c.primary)
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
        Box(Modifier.weight(1f)) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp)) {
            item {
                // A title page for the chapter: large serif title, a gold rule and a quiet hint.
                Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 18.dp)) {
                    Text(title, style = MaterialTheme.typography.displaySmall, color = c.ink, modifier = Modifier.semantics { heading() })
                    Box(Modifier.padding(top = 10.dp, bottom = 10.dp).width(32.dp).height(2.dp).background(c.accent))
                    Text(stringResource(R.string.reader_select_hint), style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
                }
            }
            val vs = verses
            if (vs == null) item { Spacer(Modifier.height(200.dp)) }
            else items(vs, key = { it.verse }) { v ->
                val hl = highlights.firstOrNull { it.verse == v.verse }?.color?.let { c.highlights.getOrNull(it) }
                val target = when {
                    flash == v.verse || selected == v.verse || readingVerse == v.verse -> c.accent.copy(alpha = 0.18f)
                    hl != null -> hl
                    else -> Color.Transparent
                }
                val bg by animateColorAsState(target, label = "verse")
                val marked = bookmarks.any { it.book == book && it.chapter == chapter && it.verse == v.verse }
                val hasNote = notes.any { it.book == book && it.chapter == chapter && it.verse == v.verse }
                val verseLabel = stringResource(R.string.reader_verse_a11y, v.verse)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(10.dp)).background(bg).clickable { selected = v.verse }
                        .semantics { contentDescription = "$verseLabel. ${v.text}" }
                        .padding(vertical = 4.dp, horizontal = 8.dp),
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
            // The end of the chapter: mark it read and the quizzes, in one quiet card.
            if (vs != null) item {
                AgapeCard(Modifier.padding(top = 28.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Ornament(width = 60.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.reader_end_of, title), style = MaterialTheme.typography.titleLarge, color = c.ink)
                        Spacer(Modifier.height(14.dp))
                        if (!isRead) {
                            PrimaryButton(stringResource(R.string.reader_mark_read), {
                                scope.launch { container.user.markChapterRead(book, chapter, title) }
                            })
                        } else {
                            Text(stringResource(R.string.reader_read_done), style = MaterialTheme.typography.labelLarge, color = c.primary)
                            TextButton(onClick = { scope.launch { container.user.unmarkChapterRead(book, chapter) } }) {
                                Text(stringResource(R.string.reader_mark_unread), style = MaterialTheme.typography.labelMedium, color = c.inkMuted)
                            }
                        }
                        ChapterQuizRow(book, chapter, bookInfo.takeIf { chapter == chapterCount }, openQuiz)
                    }
                }
            }
        }
            Column(
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (reading) AudioBar(
                    label = stringResource(R.string.reader_audio, "$title:${readingVerse ?: 1}"),
                    playing = audio.playing,
                    toggle = { if (audio.playing) speaker.pause() else listen(audio.index) },
                    previous = { speaker.skip(-1) },
                    next = { speaker.skip(1) },
                    stop = { speaker.stop() },
                )
                ChapterPill(
                    title = title,
                    previous = if (chapter > 1) ({ openChapter(book, chapter - 1, 0) }) else null,
                    next = if (chapter < chapterCount) ({ openChapter(book, chapter + 1, 0) }) else null,
                    pick = { picker = true },
                )
            }
        }
    }

    if (showInfo) ChapterInfoSheet(book, chapter, onDismiss = { showInfo = false }, openReference = openReference)

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
            shareImage = { selected = null; imageVerse = verse },
        )
    }

    imageVerse?.let { verse ->
        val v = verses?.firstOrNull { it.verse == verse } ?: return@let
        VerseImageSheet(v.text, "$title:$verse", tradition, onDismiss = { imageVerse = null })
    }

    if (picker) BookPickerSheet(book, chapter, onPick = { b, ch -> openChapter(b, ch, 0) }, onDismiss = { picker = false })
}

/** The controls while a chapter is read aloud: previous verse, play or pause, next verse, stop. */
@Composable
private fun AudioBar(label: String, playing: Boolean, toggle: () -> Unit, previous: () -> Unit, next: () -> Unit, stop: () -> Unit) {
    val c = AgapeTheme.colors
    val bar = if (c.isDark) c.surfaceRaised else c.ink
    val on = if (c.isDark) c.ink else c.background
    Row(
        Modifier.padding(horizontal = 16.dp).shadow(14.dp, CircleShape).clip(CircleShape).background(bar.copy(alpha = 0.94f)).padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.accent, maxLines = 1, modifier = Modifier.padding(end = 6.dp))
        IconButton(onClick = previous) { Icon(Icons.Outlined.SkipPrevious, stringResource(R.string.reader_previous_verse), tint = on) }
        IconButton(onClick = toggle) {
            Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, stringResource(if (playing) R.string.reader_pause else R.string.reader_play), tint = on)
        }
        IconButton(onClick = next) { Icon(Icons.Outlined.SkipNext, stringResource(R.string.reader_next_verse), tint = on) }
        IconButton(onClick = stop) { Icon(Icons.Outlined.Stop, stringResource(R.string.listen_stop), tint = on.copy(alpha = 0.7f)) }
    }
}

/** A floating pill at the foot of the chapter: previous chapter, where you are, next chapter. */
@Composable
private fun ChapterPill(title: String, previous: (() -> Unit)?, next: (() -> Unit)?, pick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AgapeTheme.colors
    val bar = if (c.isDark) c.surfaceRaised else c.ink
    val on = if (c.isDark) c.ink else c.background
    Row(
        modifier.shadow(18.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.4f))
            .clip(CircleShape).background(bar.copy(alpha = 0.94f)).border(0.8.dp, on.copy(alpha = 0.10f), CircleShape)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { previous?.invoke() }, enabled = previous != null) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.reader_previous_chapter), tint = on.copy(alpha = if (previous != null) 0.9f else 0.25f))
        }
        Text(
            title, style = MaterialTheme.typography.labelLarge, color = on,
            modifier = Modifier.clip(CircleShape).clickable(onClickLabel = stringResource(R.string.reader_jump), onClick = pick).padding(horizontal = 10.dp, vertical = 10.dp),
        )
        IconButton(onClick = { next?.invoke() }, enabled = next != null) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.reader_next_chapter), tint = on.copy(alpha = if (next != null) 0.9f else 0.25f))
        }
    }
}
