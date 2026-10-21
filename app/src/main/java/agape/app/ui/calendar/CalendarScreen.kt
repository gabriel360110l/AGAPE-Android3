package agape.app.ui.calendar

import agape.app.R
import agape.app.core.model.AppLanguage
import agape.app.core.model.Tradition
import agape.app.data.calendar.FeastRank
import agape.app.data.calendar.LiturgicalCalendar
import agape.app.data.daily.DailyInfo
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Kicker
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** The secondary date shown under the civil one: Coptic for the Oriental Orthodox, Julian (old style) for the Orthodox. */
fun secondaryDate(tradition: Tradition, date: LocalDate, lang: AppLanguage): String? = when (tradition) {
    Tradition.ORIENTAL_ORTHODOX -> LiturgicalCalendar.coptic(date).let { "${it.day} ${it.monthName(lang == AppLanguage.DUTCH)} ${it.year}" }
    Tradition.EASTERN_ORTHODOX -> LiturgicalCalendar.julian(date).let {
        "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag(lang.tag))} " + if (lang == AppLanguage.DUTCH) "(oude stijl)" else "(old style)"
    }
    Tradition.CATHOLIC -> null
}

@Composable
fun CalendarScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val tradition = tradition
    val locale = remember(lang) { Locale.forLanguageTag(lang.tag) }
    val today = remember { LocalDate.now() }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selected by rememberSaveable { mutableStateOf(today.toString()) }
    val ym = YearMonth.parse(month)
    val day = LocalDate.parse(selected)
    val activity by container.user.activity.collectAsStateWithLifecycle(emptyList())
    val activeDays = remember(activity) { activity.map { it.epochDay }.toSet() }
    val c = AgapeTheme.colors

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(stringResource(R.string.calendar_title), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ month = ym.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.calendar_previous), tint = c.ink) }
                Text(
                    ym.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } + " ${ym.year}",
                    style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                )
                IconButton({ month = ym.plusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.calendar_next), tint = c.ink) }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                DayOfWeek.entries.forEach {
                    Text(it.getDisplayName(TextStyle.NARROW, locale), style = MaterialTheme.typography.labelSmall, color = c.inkFaint, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
            val first = ym.atDay(1)
            val cells = (0 until 42).map { first.minusDays((first.dayOfWeek.value - 1).toLong()).plusDays(it.toLong()) }
            cells.chunked(7).forEach { week ->
                if (week.first().monthValue != ym.monthValue && week.last().monthValue != ym.monthValue) return@forEach
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { d ->
                        val inMonth = d.monthValue == ym.monthValue
                        val feasts = remember(d, tradition) { LiturgicalCalendar.feasts(tradition, d) }
                        val isSel = d == day
                        val isToday = d == today
                        Column(
                            Modifier.weight(1f).aspectRatio(0.82f).padding(2.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (isSel) c.primary else Color.Transparent)
                                .then(if (isToday && !isSel) Modifier.border(1.dp, c.accent, RoundedCornerShape(14.dp)) else Modifier)
                                .clickable { selected = d.toString() }
                                .semantics { contentDescription = d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)) },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                d.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium.copy(fontFamily = AgapeTheme.type.ui),
                                color = when { isSel -> c.onPrimary; inMonth -> c.ink; else -> c.inkFaint.copy(alpha = 0.5f) },
                            )
                            if (tradition == Tradition.ORIENTAL_ORTHODOX) {
                                Text(LiturgicalCalendar.coptic(d).day.toString(), style = MaterialTheme.typography.labelSmall, color = if (isSel) c.onPrimary.copy(alpha = 0.8f) else c.inkFaint)
                            }
                            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                if (feasts.any { it.rank != FeastRank.SEASON }) Box(Modifier.size(5.dp).clip(CircleShape).background(if (isSel) c.onPrimary else c.accent))
                                if (d.toEpochDay() in activeDays) Box(Modifier.size(5.dp).clip(CircleShape).background(if (isSel) c.onPrimary else c.primary.copy(alpha = 0.6f)))
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent)); Text("  " + stringResource(R.string.calendar_legend_feast) + "     ", style = MaterialTheme.typography.labelSmall, color = c.inkMuted)
                Box(Modifier.size(6.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.6f))); Text("  " + stringResource(R.string.calendar_legend_active), style = MaterialTheme.typography.labelSmall, color = c.inkMuted)
            }
            Spacer(Modifier.height(8.dp))
            DayDetails(day, tradition, lang, activity.filter { it.epochDay == day.toEpochDay() }.map { it.title })
        }
    }
}

@Composable
private fun DayDetails(day: LocalDate, tradition: Tradition, lang: AppLanguage, done: List<String>) {
    val container = LocalContainer.current
    val c = AgapeTheme.colors
    val locale = Locale.forLanguageTag(lang.tag)
    val feasts = remember(day, tradition) { LiturgicalCalendar.feasts(tradition, day) }
    val info by produceState<DailyInfo?>(null, day, tradition, lang) { value = container.daily.forDate(tradition, lang, day) }
    AgapeCard {
        Kicker(day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)))
        secondaryDate(tradition, day, lang)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.inkMuted, modifier = Modifier.padding(top = 2.dp)) }
        Spacer(Modifier.height(10.dp))
        feasts.forEach { f ->
            Text(f.name[lang], style = if (f.rank == FeastRank.SEASON) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, color = if (f.rank == FeastRank.SEASON) c.accent else c.primary)
        }
        val i = info
        if (i != null && i.title.isNotBlank()) {
            if (feasts.isNotEmpty()) Spacer(Modifier.height(8.dp))
            Text(i.title, style = MaterialTheme.typography.titleLarge, color = c.ink)
            if (i.subtitle.isNotBlank()) Text(i.subtitle, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
            i.saints.filter { it != i.title }.take(5).forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, modifier = Modifier.padding(top = 3.dp)) }
        } else if (i == null && feasts.isEmpty()) {
            Text(stringResource(R.string.calendar_loading), style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
        }
        if (done.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Kicker(stringResource(R.string.calendar_your_day), color = c.inkMuted)
            done.distinct().take(6).forEach { Text("✓ $it", style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.padding(top = 3.dp)) }
        }
    }
}

/** This week at a glance, for Home; tap to open the full calendar. */
@Composable
fun WeekStrip(onOpen: () -> Unit) {
    val lang = LocalLanguage.current
    val tradition = tradition
    val c = AgapeTheme.colors
    val locale = Locale.forLanguageTag(lang.tag)
    val today = remember { LocalDate.now() }
    val week = remember(today) { (0L..6L).map { today.minusDays((today.dayOfWeek.value - 1).toLong()).plusDays(it) } }
    val todayFeast = remember(tradition) { LiturgicalCalendar.feasts(tradition, today).firstOrNull() }
    AgapeCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker(stringResource(R.string.calendar_title), Modifier.weight(1f))
            secondaryDate(tradition, today, lang)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = c.inkMuted) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            week.forEach { d ->
                val isToday = d == today
                val feast = LiturgicalCalendar.feasts(tradition, d).any { it.rank != FeastRank.SEASON }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(2), style = MaterialTheme.typography.labelSmall, color = c.inkFaint)
                    Box(
                        Modifier.padding(top = 4.dp).size(36.dp).clip(CircleShape).background(if (isToday) c.primary else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall, color = if (isToday) c.onPrimary else c.ink) }
                    Box(Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape).background(if (feast) c.accent else Color.Transparent))
                }
            }
        }
        todayFeast?.let {
            Spacer(Modifier.height(10.dp))
            Text(it.name[lang], style = MaterialTheme.typography.titleMedium, color = c.primary)
        }
    }
}
