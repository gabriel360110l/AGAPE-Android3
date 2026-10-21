package agape.app.ui.home

import agape.app.R
import agape.app.core.model.Passage
import agape.app.core.model.Tradition
import agape.app.data.calendar.ChurchCalendar
import agape.app.data.daily.DailyInfo
import agape.app.data.plans.PlanEngine
import agape.app.data.plans.PrayerClock
import agape.app.data.plans.planDays
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.LocalSettings
import agape.app.ui.bookName
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.AgapeIcons
import agape.app.ui.components.Kicker
import agape.app.ui.components.Ornament
import agape.app.ui.components.ProgressLine
import agape.app.ui.components.QuietButton
import agape.app.ui.components.TabHeader
import agape.app.ui.components.cardShape
import agape.app.ui.main.BibleRoute
import agape.app.ui.main.PrayerRoute
import agape.app.ui.passageLabel
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun HomeScreen(
    openReader: (String, Int, Int) -> Unit,
    openPlans: () -> Unit,
    openPlan: (String) -> Unit,
    openTab: (Any) -> Unit,
    openSaved: () -> Unit,
    openHour: (String) -> Unit,
) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val settings = LocalSettings.current
    val tradition = tradition
    val today = remember { LocalDate.now() }
    val c = AgapeTheme.colors

    val verseRef = remember(tradition, today) { Passage.parse(container.content.verseOfDay(tradition, today)) }
    val verseText by produceState<String?>(null, verseRef, lang) {
        value = verseRef?.let { container.bible.passage(it, lang).joinToString(" ") { v -> v.text } }
    }
    val daily by produceState<DailyInfo?>(null, tradition, lang) { value = container.daily.today(tradition, lang) }
    val plans by container.user.plans.collectAsStateWithLifecycle(emptyList())
    val stats by container.user.stats.collectAsStateWithLifecycle(null)
    val hours = remember(tradition) { container.content.prayerBook(tradition).hours }
    val currentHour = remember(hours) { PrayerClock.current(hours, LocalTime.now()) }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            val date = today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.forLanguageTag(lang.tag)))
            val paschal = ChurchCalendar.isPaschalSeason(tradition, today)
            val title = when {
                !paschal -> stringResource(greeting())
                tradition == Tradition.CATHOLIC -> stringResource(R.string.home_paschal_catholic)
                else -> stringResource(R.string.home_paschal_orthodox)
            }
            TabHeader(kicker = date, title = title)
        }

        // Verse of the day
        item {
            AgapeCard(Modifier.padding(horizontal = 16.dp), onClick = verseRef?.let { r -> { openReader(r.book, r.chapter, r.startVerse ?: 0) } }) {
                Kicker(stringResource(R.string.home_verse_of_day))
                Spacer(Modifier.height(12.dp))
                Text(
                    verseText ?: "…",
                    style = AgapeTheme.type.scripture.copy(fontSize = AgapeTheme.type.scripture.fontSize * 1.08f),
                    color = c.ink,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(verseRef?.let { container.passageLabel(it, lang) }.orEmpty(), style = MaterialTheme.typography.labelLarge, color = c.primary, modifier = Modifier.weight(1f))
                    Ornament(width = 60.dp)
                }
            }
        }

        // Continue reading
        item {
            val pos = settings.lastPosition
            AgapeCard(
                Modifier.padding(horizontal = 16.dp),
                onClick = { if (pos != null) openReader(pos.book, pos.chapter, pos.verse) else openReader("JHN", 1, 0) },
            ) {
                Kicker(stringResource(R.string.home_continue))
                Spacer(Modifier.height(6.dp))
                Text(
                    if (pos != null) "${container.bookName(pos.book, lang)} ${pos.chapter}" else stringResource(R.string.home_start_reading),
                    style = MaterialTheme.typography.headlineSmall, color = c.ink,
                )
                Text(
                    stringResource(if (pos != null) R.string.home_continue_hint else R.string.home_start_reading_hint),
                    style = MaterialTheme.typography.bodySmall, color = c.inkMuted,
                )
            }
        }

        // Reading plan
        item {
            val active = plans.firstOrNull { it.active }
            val def = active?.let { container.content.plan(it.planId) }
            AgapeCard(Modifier.padding(horizontal = 16.dp), onClick = { if (def != null) openPlan(def.id) else openPlans() }, tonal = def == null) {
                Kicker(stringResource(R.string.home_plan))
                Spacer(Modifier.height(6.dp))
                if (def != null) {
                    val days = remember(def, tradition, lang) { container.content.planDays(def, tradition, lang) }
                    val next = PlanEngine.nextDay(days.size, active.completedDays)
                    Text(def.title[lang], style = MaterialTheme.typography.headlineSmall, color = c.ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (next != null) stringResource(R.string.plan_day_of, next, days.size) else stringResource(R.string.plan_finished),
                        style = MaterialTheme.typography.bodySmall, color = c.inkMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    ProgressLine(active.completedDays.size / days.size.coerceAtLeast(1).toFloat())
                } else {
                    Text(stringResource(R.string.home_plan_start), style = MaterialTheme.typography.headlineSmall, color = c.ink)
                    Text(stringResource(R.string.home_plan_start_hint), style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                }
            }
        }

        // Quick access
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile(Icons.AutoMirrored.Outlined.MenuBook, stringResource(R.string.tab_bible), Modifier.weight(1f)) { openTab(BibleRoute) }
                QuickTile(AgapeIcons.Candle, currentHour?.name?.get(lang) ?: stringResource(R.string.tab_prayer), Modifier.weight(1f)) {
                    currentHour?.let { openHour(it.id) } ?: openTab(PrayerRoute)
                }
                QuickTile(Icons.Outlined.BookmarkBorder, stringResource(R.string.home_saved), Modifier.weight(1f), onClick = openSaved)
                QuickTile(Icons.Outlined.DateRange, stringResource(R.string.home_plans), Modifier.weight(1f), onClick = openPlans)
            }
        }

        // Saint of the day (from the daily APIs, cached for offline)
        daily?.takeIf { it.title.isNotBlank() }?.let { d ->
            item {
                AgapeCard(Modifier.padding(horizontal = 16.dp)) {
                    Kicker(stringResource(R.string.home_saint))
                    Spacer(Modifier.height(6.dp))
                    Text(d.title, style = MaterialTheme.typography.headlineSmall, color = c.ink)
                    if (d.subtitle.isNotBlank()) Text(d.subtitle, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                    d.saints.filter { it != d.title }.take(3).forEach {
                        Text("· $it", style = MaterialTheme.typography.bodySmall, color = c.inkMuted, modifier = Modifier.padding(top = 4.dp))
                    }
                    val readings = d.readings.mapNotNull { Passage.parse(it) }
                    if (readings.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.home_readings), style = MaterialTheme.typography.labelMedium, color = c.ink)
                        readings.forEach { p ->
                            QuietButton(container.passageLabel(p, lang), { openReader(p.book, p.chapter, p.startVerse ?: 0) })
                        }
                    }
                    Text(stringResource(R.string.home_source, d.source), style = MaterialTheme.typography.labelSmall, color = c.inkFaint, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        stats?.takeIf { it.currentStreak > 0 }?.let { s ->
            item {
                Text(
                    stringResource(R.string.home_streak, s.currentStreak),
                    style = MaterialTheme.typography.bodyMedium, color = c.inkMuted,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

private fun greeting(): Int = when (LocalTime.now().hour) {
    in 5..11 -> R.string.home_greeting_morning
    in 12..17 -> R.string.home_greeting_afternoon
    in 18..23 -> R.string.home_greeting_evening
    else -> R.string.home_greeting_night
}

@Composable
private fun QuickTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = AgapeTheme.colors
    Column(
        modifier.clip(cardShape).background(c.surface).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.ink, maxLines = 1, textAlign = TextAlign.Center)
    }
}
