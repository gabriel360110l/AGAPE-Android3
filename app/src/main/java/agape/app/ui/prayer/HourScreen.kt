package agape.app.ui.prayer

import agape.app.R
import agape.app.core.model.Passage
import agape.app.data.content.PrayerSection
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Kicker
import agape.app.ui.components.Ornament
import agape.app.ui.components.PrimaryButton
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun HourScreen(hourId: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val scope = rememberCoroutineScope()
    val tradition = tradition
    val hour = remember(hourId, tradition) { container.content.prayerBook(tradition).hours.firstOrNull { it.id == hourId } } ?: return
    var prayed by remember { mutableStateOf(false) }
    val c = AgapeTheme.colors

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(hour.name[lang], onBack) { FavoriteButton("hour:${hour.id}") }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Kicker(hour.time)
                Spacer(Modifier.height(4.dp))
                Text(hour.name[lang], style = MaterialTheme.typography.displaySmall, color = c.primary, textAlign = TextAlign.Center)
                Text(hour.subtitle[lang], style = MaterialTheme.typography.titleMedium, color = c.accent, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(hour.theme[lang], style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Ornament()
            }
            hour.sections.forEach { PrayerSectionView(it) }
            Spacer(Modifier.height(20.dp))
            Ornament(Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
            if (!prayed) PrimaryButton(stringResource(R.string.prayer_amen), {
                prayed = true
                scope.launch { container.user.markHourPrayed(hour.id, hour.name[lang]) }
            }) else Text(
                stringResource(R.string.prayer_prayed), style = MaterialTheme.typography.titleMedium, color = c.primary,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PrayerSectionView(section: PrayerSection) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    val textStyle = AgapeTheme.type.scripture
    when (section.kind) {
        PrayerSection.Kind.text -> {
            section.title?.let { SectionTitle(it[lang]) }
            section.body?.get(lang)?.forEach { Text(it, style = textStyle, color = c.ink, modifier = Modifier.padding(bottom = 10.dp)) }
        }
        PrayerSection.Kind.scripture -> {
            val passage = remember(section.ref) { section.ref?.let { Passage.parse(it) } }
            val verses by produceState(emptyList<String>(), passage, lang) {
                value = passage?.let { p -> container.bible.passage(p, lang).map { it.text } }.orEmpty()
            }
            section.title?.let { SectionTitle(it[lang]) }
            verses.forEach { Text(it, style = textStyle, color = c.ink, modifier = Modifier.padding(bottom = 6.dp)) }
            Spacer(Modifier.height(6.dp))
        }
        PrayerSection.Kind.prayer -> {
            val prayer = remember(section.prayerId) { section.prayerId?.let { container.content.prayer(it) } } ?: return
            SectionTitle(section.title?.get(lang) ?: prayer.title[lang])
            prayer.body[lang].forEach { Text(it, style = textStyle, color = c.ink, modifier = Modifier.padding(bottom = 10.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(), style = AgapeTheme.type.kicker, color = AgapeTheme.colors.accent,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
fun PrayerTextScreen(prayerId: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val scope = rememberCoroutineScope()
    val prayer = remember(prayerId) { container.content.prayer(prayerId) } ?: return
    var prayed by remember { mutableStateOf(false) }
    val c = AgapeTheme.colors
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(prayer.title[lang], onBack) { FavoriteButton("prayer:${prayer.id}") }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(prayer.title[lang], style = MaterialTheme.typography.displaySmall, color = c.primary, textAlign = TextAlign.Center)
                if (prayer.subtitle[lang].isNotBlank()) Text(prayer.subtitle[lang], style = MaterialTheme.typography.titleMedium, color = c.accent)
                Spacer(Modifier.height(14.dp))
                Ornament()
            }
            prayer.body[lang].forEach { Text(it, style = AgapeTheme.type.scripture, color = c.ink, modifier = Modifier.padding(bottom = 12.dp)) }
            Spacer(Modifier.height(16.dp))
            if (!prayed) PrimaryButton(stringResource(R.string.prayer_amen), {
                prayed = true
                scope.launch { container.user.markPrayed(prayer.id, prayer.title[lang]) }
            }) else Text(stringResource(R.string.prayer_prayed), style = MaterialTheme.typography.titleMedium, color = c.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(32.dp))
        }
    }
}
