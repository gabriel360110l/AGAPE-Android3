package agape.app.ui.prayer

import agape.app.R
import agape.app.core.prefs.Intention
import agape.app.core.prefs.IntentionKind
import agape.app.core.prefs.intentions
import agape.app.ui.LocalContainer
import agape.app.ui.LocalSettings
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Kicker
import agape.app.ui.theme.AgapeTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.UUID

/** What each list is called, and the words that bring its names before God at the end of an hour. */
val IntentionKind.title: Int get() = when (this) {
    IntentionKind.LIVING -> R.string.intentions_living
    IntentionKind.DEPARTED -> R.string.intentions_departed
    IntentionKind.THANKS -> R.string.intentions_thanks
}
val IntentionKind.prayer: Int get() = when (this) {
    IntentionKind.LIVING -> R.string.intentions_living_prayer
    IntentionKind.DEPARTED -> R.string.intentions_departed_prayer
    IntentionKind.THANKS -> R.string.intentions_thanks_prayer
}

/** The diptychs: names of the living, of the departed, and things to give thanks for, remembered in every hour. */
@Composable
fun IntentionsScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val c = AgapeTheme.colors
    val all = settings.intentions

    fun store(list: List<Intention>) = scope.launch { container.settings.setPrayerIntentions(list.map { it.encode() }.toSet()) }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(stringResource(R.string.intentions_title), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.intentions_intro), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, modifier = Modifier.padding(horizontal = 4.dp))
            IntentionKind.entries.forEach { kind ->
                var draft by rememberSaveable(kind) { mutableStateOf("") }
                fun add() {
                    val text = draft.trim()
                    if (text.isNotEmpty()) store(all + Intention(UUID.randomUUID().toString().take(8), kind, text))
                    draft = ""
                }
                AgapeCard {
                    Kicker(stringResource(kind.title))
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(kind.prayer), style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), color = c.inkMuted)
                    all.filter { it.kind == kind }.forEach { i ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(i.text, style = AgapeTheme.type.scripture, color = c.ink, modifier = Modifier.weight(1f))
                            IconButton(onClick = { store(all - i) }) { Icon(Icons.Outlined.Close, stringResource(R.string.delete), tint = c.inkFaint) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it.take(60) }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.intentions_add_hint)) },
                        trailingIcon = { IconButton(::add, enabled = draft.isNotBlank()) { Icon(Icons.Outlined.Add, stringResource(R.string.intentions_add), tint = c.primary) } },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = c.line, cursorColor = c.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text(stringResource(R.string.intentions_private), style = MaterialTheme.typography.bodySmall, color = c.inkFaint, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

/** At the end of an hour: the names, each list with its own words. Nothing is shown when the lists are empty. */
@Composable
fun IntentionsInHour() {
    val all = LocalSettings.current.intentions
    if (all.isEmpty()) return
    val c = AgapeTheme.colors
    AgapeCard(Modifier.padding(top = 24.dp), tonal = true) {
        Kicker(stringResource(R.string.intentions_title))
        IntentionKind.entries.forEach { kind ->
            val names = all.filter { it.kind == kind }
            if (names.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(kind.prayer), style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = c.inkMuted)
                Text(names.joinToString(", ") { it.text }, style = AgapeTheme.type.scripture, color = c.ink, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
