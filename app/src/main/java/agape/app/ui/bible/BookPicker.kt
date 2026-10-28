package agape.app.ui.bible

import agape.app.R
import agape.app.core.model.Testament
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.Kicker
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Jump to any book and chapter from the reader, without going back to the Bible tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookPickerSheet(currentBook: String, currentChapter: Int, onPick: (String, Int) -> Unit, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val tradition = tradition
    val books = remember(tradition, lang) { container.content.canon(tradition, lang) }
    val start = books.firstOrNull { it.id == currentBook }
    var query by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf(if (start?.testament == Testament.NT) BookSection.NT else BookSection.OT) }
    var expanded by rememberSaveable { mutableStateOf<String?>(currentBook) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = AgapeTheme.colors.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).navigationBarsPadding()) {
            Kicker(stringResource(R.string.reader_jump), Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            BookFilterBar(query, { query = it }, section, { section = it })
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(filterBooks(books, query, section, lang), key = { it.id }) { book ->
                    BookRow(
                        book = book,
                        expanded = expanded == book.id,
                        onToggle = { expanded = if (expanded == book.id) null else book.id },
                        onChapter = { ch -> onDismiss(); onPick(book.id, ch) },
                        current = currentChapter.takeIf { book.id == currentBook },
                    )
                }
            }
        }
    }
}
