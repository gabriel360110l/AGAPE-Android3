package agape.app.ui.bible

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import agape.app.ui.theme.Spacing
import agape.app.ui.components.Paragraph
import agape.app.ui.components.Section
import agape.app.R
import agape.app.core.model.Passage
import agape.app.core.model.Tradition
import agape.app.data.bible.FatherNote
import agape.app.data.bible.FatherQuote
import agape.app.data.bible.Verse
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.Kicker
import agape.app.ui.components.PrimaryButton
import agape.app.ui.components.cardShape
import agape.app.ui.passageLabel
import agape.app.ui.theme.AgapeTheme
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class CrossRefPreview(val passage: Passage, val text: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerseSheet(
    verse: Verse,
    tradition: Tradition,
    highlight: Int?,
    bookmarked: Boolean,
    note: String,
    onDismiss: () -> Unit,
    openReference: (String, Int, Int) -> Unit,
    shareImage: () -> Unit,
) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = AgapeTheme.colors
    val label = container.passageLabel(Passage(verse.book, verse.chapter, verse.verse), lang)
    var editingNote by remember { mutableStateOf(false) }
    var noteText by remember(note) { mutableStateOf(note) }

    val notes = remember(verse, tradition, lang) { container.content.verseNotes(verse.ref.key, tradition, lang) }
    val quotes by produceState(emptyList<FatherQuote>(), verse) { value = container.bible.fatherQuotes(verse.ref) }
    val fathers by produceState(emptyList<FatherNote>(), verse, lang) {
        value = container.bible.fatherNotes(verse.book, verse.chapter).filter { verse.verse in it.verseFrom..it.verseTo }
    }
    val crossRefs by produceState(emptyList<CrossRefPreview>(), verse, lang) {
        value = container.bible.crossRefs(verse.ref).map { r ->
            val p = Passage(r.book, r.chapter, r.verse, r.chapter, r.verseEnd)
            CrossRefPreview(p, container.bible.passage(p, lang).joinToString(" ") { it.text })
        }.filter { it.text.isNotBlank() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false), containerColor = c.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl).navigationBarsPadding().padding(bottom = Spacing.xl)) {
            Kicker(label)
            Spacer(Modifier.height(Spacing.s))
            Text(verse.text, style = AgapeTheme.type.scripture, color = c.ink)
            Spacer(Modifier.height(Spacing.l))

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                ActionChip(if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, stringResource(if (bookmarked) R.string.verse_bookmarked else R.string.verse_bookmark)) {
                    scope.launch { container.user.setBookmark(verse.book, verse.chapter, verse.verse, !bookmarked, label) }
                }
                ActionChip(Icons.Outlined.EditNote, stringResource(R.string.verse_note)) { editingNote = !editingNote }
                ActionChip(Icons.Outlined.Share, stringResource(R.string.verse_share)) {
                    val text = "“${verse.text}”\n— $label\n\nagape://bible/${verse.book}/${verse.chapter}?verse=${verse.verse}"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                }
                ActionChip(Icons.Outlined.Image, stringResource(R.string.verse_image), shareImage)
            }
            Spacer(Modifier.height(Spacing.m))
            Text(stringResource(R.string.verse_highlight), style = MaterialTheme.typography.labelMedium, color = c.inkMuted)
            Spacer(Modifier.height(Spacing.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                val names = listOf(R.string.color_gold, R.string.color_blue, R.string.color_green, R.string.color_rose)
                c.highlights.forEachIndexed { i, color ->
                    val chosen = highlight == i
                    val desc = stringResource(names[i])
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha = 0.9f))
                            .border(if (chosen) 3.dp else 1.dp, if (chosen) c.primary else c.line, CircleShape)
                            .clickable { scope.launch { container.user.setHighlight(verse.book, verse.chapter, verse.verse, if (chosen) null else i, label) } }
                            .semantics { contentDescription = desc },
                    )
                }
                if (highlight != null) {
                    val clear = stringResource(R.string.verse_clear_highlight)
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).border(1.dp, c.line, CircleShape)
                            .clickable { scope.launch { container.user.setHighlight(verse.book, verse.chapter, verse.verse, null, label) } }
                            .semantics { contentDescription = clear },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Close, null, tint = c.inkMuted, modifier = Modifier.size(18.dp)) }
                }
            }

            if (editingNote || note.isNotBlank()) {
                Spacer(Modifier.height(Spacing.l))
                OutlinedTextField(
                    value = noteText, onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.verse_note)) },
                    modifier = Modifier.fillMaxWidth(), shape = cardShape, minLines = 3,
                )
                if (noteText != note) {
                    Spacer(Modifier.height(Spacing.s))
                    PrimaryButton(stringResource(R.string.save), {
                        scope.launch { container.user.saveNote(verse.book, verse.chapter, verse.verse, noteText, label) }
                        editingNote = false
                    })
                }
            }

            Section(stringResource(R.string.verse_commentary), Modifier.padding(top = Spacing.xl)) {
                if (notes.isEmpty()) Text(stringResource(R.string.verse_commentary_soon), style = MaterialTheme.typography.bodyMedium, color = c.inkFaint)
                notes.forEach { Paragraph(it) }
            }

            if (quotes.isNotEmpty()) {
                Section(stringResource(R.string.verse_father_quotes), Modifier.padding(top = Spacing.xl)) {
                    quotes.forEach { q ->
                        Text("${q.father}" + if (q.year > 0) " · ${q.year}" else "", style = MaterialTheme.typography.titleSmall, color = c.primary)
                        Text(q.text, style = AgapeTheme.type.scripture.copy(fontSize = AgapeTheme.type.scripture.fontSize * 0.82f), color = c.ink, modifier = Modifier.padding(top = Spacing.xs))
                        if (q.source.isNotBlank()) Text(q.source, style = MaterialTheme.typography.labelSmall, color = c.inkFaint, modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.m))
                    }
                }
            }

            if (fathers.isNotEmpty()) {
                Section(stringResource(R.string.verse_fathers), Modifier.padding(top = Spacing.xl)) {
                    fathers.forEach { f ->
                        Text("${f.father} · ${f.work} (${f.date})", style = MaterialTheme.typography.titleSmall, color = c.ink)
                        Paragraph(f.note)
                        Text(f.source, style = MaterialTheme.typography.labelSmall, color = c.inkFaint, modifier = Modifier.padding(bottom = Spacing.m))
                    }
                }
            }

            if (crossRefs.isNotEmpty()) {
                Section(stringResource(R.string.verse_cross_refs), Modifier.padding(top = Spacing.xl)) {
                    crossRefs.forEach { r ->
                        Column(
                            Modifier.fillMaxWidth().clip(cardShape).clickable { openReference(r.passage.book, r.passage.chapter, r.passage.startVerse ?: 0) }
                                .padding(vertical = Spacing.s, horizontal = Spacing.xs),
                        ) {
                            Text(container.passageLabel(r.passage, lang), style = MaterialTheme.typography.labelLarge, color = c.primary)
                            Text(r.text, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, maxLines = 3)
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.close), color = c.primary) }
        }
    }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = AgapeTheme.colors
    Row(
        Modifier.clip(cardShape).border(1.dp, c.line, cardShape).clickable(onClick = onClick).padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.ink)
    }
}
