package agape.app.ui.prayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import agape.app.ui.theme.Spacing
import agape.app.R
import agape.app.core.model.AppLanguage
import agape.app.core.model.Passage
import agape.app.data.content.PrayerSection
import agape.app.data.counter.AtEnd
import agape.app.ui.components.counterTapArea
import agape.app.ui.components.rememberPrayerCounter
import agape.app.ui.components.rememberReduceMotion
import agape.app.notifications.QuietWhilePraying
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Kicker
import agape.app.ui.components.Ornament
import agape.app.ui.components.PrimaryButton
import agape.app.ui.components.ProgressLine
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun HourScreen(hourId: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val scope = rememberCoroutineScope()
    val tradition = tradition
    val hour = remember(hourId, tradition) { container.content.prayerEntry(tradition, hourId) } ?: return
    // Sections bound to a weekday (Compline troparia, Rosary mysteries) show only on that day.
    val today = remember { LocalDate.now().dayOfWeek.value }
    val sections = remember(hour, today) { hour.sections.filter { it.weekday == null || it.weekday == today } }
    var prayed by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val progress by remember { derivedStateOf { list.firstVisibleItemIndex / sections.size.coerceAtLeast(1).toFloat() } }
    val c = AgapeTheme.colors
    val untranslated = lang == AppLanguage.DUTCH && sections.any { s -> s.kind in listOf(PrayerSection.Kind.text, PrayerSection.Kind.rubric) && s.body != null && s.body.nl.isEmpty() && s.body.en.isNotEmpty() }

    QuietWhilePraying()
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(hour.name[lang], onBack) {
            FavoriteButton("hour:${hour.id}")
        }
        ProgressLine(progress, Modifier.padding(horizontal = Spacing.xl))
        LazyColumn(state = list, contentPadding = PaddingValues(start = Spacing.xl, end = Spacing.xl, bottom = 48.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.l), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (hour.time.isNotBlank()) Kicker(hour.time)
                    Spacer(Modifier.height(Spacing.s))
                    Text(hour.name[lang], style = MaterialTheme.typography.displaySmall, color = c.primary, textAlign = TextAlign.Center)
                    Text(hour.subtitle[lang], style = MaterialTheme.typography.titleMedium, color = c.accentInk, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(Spacing.l))
                    Ornament()
                    if (untranslated) {
                        Spacer(Modifier.height(Spacing.m))
                        Text(stringResource(R.string.english_only_note), style = MaterialTheme.typography.bodySmall, color = c.inkFaint, textAlign = TextAlign.Center)
                    }
                }
            }
            items(sections.size) { i -> PrayerSectionView(sections[i], "hour:${hour.id}:$i") }
            item { IntentionsInHour() }
            item {
                Column(Modifier.fillMaxWidth().padding(top = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                    Ornament()
                    Spacer(Modifier.height(Spacing.l))
                    if (!prayed) PrimaryButton(stringResource(R.string.prayer_amen), {
                        prayed = true
                        scope.launch { container.user.markHourPrayed(hour.id, hour.name[lang]) }
                    }) else Text(stringResource(R.string.prayer_prayed), style = MaterialTheme.typography.titleMedium, color = c.primary)
                }
            }
        }
    }
}

@Composable
private fun PrayerSectionView(section: PrayerSection, key: String) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    val textStyle = AgapeTheme.type.scripture
    when (section.kind) {
        PrayerSection.Kind.text -> {
            section.title?.get(lang)?.takeIf { it.isNotBlank() }?.let { SectionTitle(it) }
            section.body?.get(lang)?.forEach { Text(it, style = textStyle, color = c.ink, modifier = Modifier.padding(bottom = Spacing.m)) }
        }
        PrayerSection.Kind.rubric -> {
            section.body?.get(lang)?.forEach {
                Text(
                    it, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = c.accentInk,
                    modifier = Modifier.padding(top = Spacing.m, bottom = Spacing.s),
                )
            }
        }
        PrayerSection.Kind.heading -> {
            Column(Modifier.fillMaxWidth().padding(top = Spacing.xxl, bottom = Spacing.s), horizontalAlignment = Alignment.CenterHorizontally) {
                Ornament(width = 90.dp)
                Spacer(Modifier.height(Spacing.m))
                Text(section.title?.get(lang).orEmpty(), style = MaterialTheme.typography.headlineSmall, color = c.primary, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() })
                section.body?.get(lang)?.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, textAlign = TextAlign.Center) }
            }
        }
        PrayerSection.Kind.scripture -> {
            // English inline text (e.g. the Agpeya's Septuagint psalter) is used when present; otherwise the Bible of the app language.
            val inline = section.body?.let { if (lang == AppLanguage.ENGLISH) it.en else it.nl }.orEmpty()
            val passage = remember(section.ref) { section.ref?.let { Passage.parse(it) } }
            val verses by produceState(inline, passage, lang) {
                if (inline.isEmpty() && passage != null) value = container.bible.passage(passage, lang).map { it.text }
            }
            section.title?.let { SectionTitle(it[lang]) }
            Text(verses.joinToString(" "), style = textStyle, color = c.ink, modifier = Modifier.padding(bottom = Spacing.m))
        }
        PrayerSection.Kind.prayer -> {
            val prayer = remember(section.prayerId) { section.prayerId?.let { container.content.prayer(it) } } ?: return
            SectionTitle(section.title?.get(lang) ?: prayer.title[lang])
            prayer.body[lang].forEach { Text(it, style = textStyle, color = c.ink, modifier = Modifier.padding(bottom = Spacing.m)) }
        }
        PrayerSection.Kind.counter -> PrayerCounter(section, key)
    }
}

/**
 * Tap the circle for each Κύριε ἐλέησον; the ring fills as you go, and a tap after the last starts again. The shared
 * prayer counter: the same tap, feel and TalkBack as the prayer rope and the Rosary, kept for the day under [key].
 */
@Composable
private fun PrayerCounter(section: PrayerSection, key: String) {
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    val total = section.count.coerceAtLeast(1)
    val counter = rememberPrayerCounter(key, total, AtEnd.RESTART, daily = true)
    val done = counter.count.done
    val still = rememberReduceMotion()
    val sweep by animateFloatAsState(done / total.toFloat(), if (still) snap() else spring(), label = "counter")
    val phrase = section.body?.get(lang)?.firstOrNull().orEmpty()
    val desc = stringResource(R.string.counter_a11y, phrase, done, total)
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        section.title?.get(lang)?.let { SectionTitle(it) }
        Box(
            Modifier.size(176.dp).clip(CircleShape).background(c.surface)
                .counterTapArea(counter, desc, stringResource(R.string.counter_tap), stringResource(R.string.counter_back)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(Spacing.m)) {
                val stroke = 6.dp.toPx()
                drawArc(c.line, 0f, 360f, false, style = Stroke(stroke), topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke))
                drawArc(c.accent, -90f, 360f * sweep, false, style = Stroke(stroke, cap = StrokeCap.Round), topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$done", style = MaterialTheme.typography.displayMedium.copy(fontFamily = AgapeTheme.type.ui), color = c.primary)
                Text("/ $total", style = MaterialTheme.typography.labelMedium, color = c.inkFaint)
            }
        }
        Spacer(Modifier.height(Spacing.m))
        Text(phrase, style = AgapeTheme.type.scripture, color = c.ink, textAlign = TextAlign.Center)
        // The Kyrie is prayed in Greek; its meaning is given once, quietly, beneath it.
        if ("Κύριε" in phrase) {
            Text(stringResource(R.string.kyrie_translation), style = MaterialTheme.typography.bodySmall, color = c.inkMuted, fontStyle = FontStyle.Italic)
        }
        Text(
            stringResource(if (done < total) R.string.counter_hint else R.string.counter_done),
            style = MaterialTheme.typography.bodySmall, color = c.inkFaint, textAlign = TextAlign.Center,
        )
    }
}

private val greek = Regex("[Ͱ-Ͽἀ-῿]")
private val accents = Regex("\\p{Mn}")

/** Upper case for section titles; Greek capitals are written without accents (Κύριε → ΚΥΡΙΕ). */
private fun String.headingCase(): String {
    val upper = uppercase()
    return if (greek.containsMatchIn(this)) accents.replace(java.text.Normalizer.normalize(upper, java.text.Normalizer.Form.NFD), "") else upper
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.headingCase(), style = AgapeTheme.type.kicker, color = AgapeTheme.colors.accentInk,
        modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.m).semantics { heading() },
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
    QuietWhilePraying()
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(prayer.title[lang], onBack) {
            FavoriteButton("prayer:${prayer.id}")
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl)) {
            Column(Modifier.fillMaxWidth().padding(vertical = Spacing.l), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(prayer.title[lang], style = MaterialTheme.typography.displaySmall, color = c.primary, textAlign = TextAlign.Center)
                if (prayer.subtitle[lang].isNotBlank()) Text(prayer.subtitle[lang], style = MaterialTheme.typography.titleMedium, color = c.accentInk)
                Spacer(Modifier.height(Spacing.l))
                Ornament()
            }
            prayer.body[lang].forEach { Text(it, style = AgapeTheme.type.scripture, color = c.ink, modifier = Modifier.padding(bottom = Spacing.m)) }
            Spacer(Modifier.height(Spacing.l))
            if (!prayed) PrimaryButton(stringResource(R.string.prayer_amen), {
                prayed = true
                scope.launch { container.user.markPrayed(prayer.id, prayer.title[lang]) }
            }) else Text(stringResource(R.string.prayer_prayed), style = MaterialTheme.typography.titleMedium, color = c.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(40.dp))
        }
    }
}
