package agape.app.ui.calendar

import agape.app.R
import agape.app.core.model.AppLanguage
import agape.app.core.model.Passage
import agape.app.core.model.Testament
import agape.app.core.model.Tradition
import agape.app.data.calendar.FeastRank
import agape.app.data.calendar.LiturgicalCalendar
import agape.app.data.calendar.FastLevel
import agape.app.data.calendar.Fasting
import agape.app.ui.components.DayStatusPill
import agape.app.ui.components.FastMark
import agape.app.data.saints.SaintDayView
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Kicker
import agape.app.ui.components.SaintIcon
import agape.app.ui.components.TabHeader
import agape.app.ui.passageLabel
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
fun CalendarScreen(onBack: (() -> Unit)? = null, openPassage: (Passage) -> Unit = {}) {
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
        // A tab of its own; with onBack it is opened from elsewhere as a detail screen.
        if (onBack != null) DetailTopBar(stringResource(R.string.calendar_title), onBack)
        // The tab header scrolls with the month, so no week is cut off under a fixed title.
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (onBack == null) TabHeader(kicker = secondaryDate(tradition, today, lang).orEmpty(), title = stringResource(R.string.calendar_title))
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AgapeCard(contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ month = ym.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.calendar_previous), tint = c.ink) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                ym.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } + " ${ym.year}",
                                style = MaterialTheme.typography.titleLarge, color = c.ink, textAlign = TextAlign.Center,
                            )
                            if (ym != YearMonth.from(today) || day != today) {
                                Text(
                                    stringResource(R.string.calendar_today), style = MaterialTheme.typography.labelMedium, color = c.primary,
                                    modifier = Modifier.clip(CircleShape).clickable { month = YearMonth.from(today).toString(); selected = today.toString() }.padding(horizontal = 10.dp, vertical = 2.dp),
                                )
                            }
                        }
                        IconButton({ month = ym.plusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.calendar_next), tint = c.ink) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp)) {
                        DayOfWeek.entries.forEach {
                            Text(
                                it.getDisplayName(TextStyle.SHORT, locale).take(2).replaceFirstChar { ch -> ch.titlecase(locale) },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (it == DayOfWeek.SUNDAY) c.accent else c.inkFaint,
                                modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.line))
                    val first = ym.atDay(1)
                    val cells = (0 until 42).map { first.minusDays((first.dayOfWeek.value - 1).toLong()).plusDays(it.toLong()) }
                    cells.chunked(7).forEach { week ->
                        if (week.first().monthValue != ym.monthValue && week.last().monthValue != ym.monthValue) return@forEach
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            week.forEach { d ->
                                DayCell(
                                    d, inMonth = d.monthValue == ym.monthValue, isSelected = d == day, isToday = d == today,
                                    active = d.toEpochDay() in activeDays, locale = locale, modifier = Modifier.weight(1f),
                                ) { selected = d.toString(); if (d.monthValue != ym.monthValue) month = YearMonth.from(d).toString() }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    LegendItem(stringResource(R.string.calendar_legend_feast)) { Box(Modifier.size(7.dp).clip(CircleShape).background(c.accent)) }
                    LegendItem(stringResource(R.string.calendar_legend_fast)) { Box(Modifier.width(12.dp).height(3.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.7f))) }
                    LegendItem(stringResource(R.string.calendar_legend_active)) { Box(Modifier.size(7.dp).border(1.2.dp, c.inkMuted, CircleShape)) }
                }
                DayDetails(day, tradition, lang, activity.filter { it.epochDay == day.toEpochDay() }.map { it.title }, openPassage)
            }
        }
    }
}

/**
 * One day in the month: the number in a circle, gold for a feast, a short bar beneath on a fast day and a ring on
 * the days the reader read or prayed. Today has a gold ring; the chosen day is filled.
 */
@Composable
private fun DayCell(d: LocalDate, inMonth: Boolean, isSelected: Boolean, isToday: Boolean, active: Boolean, locale: Locale, modifier: Modifier, onClick: () -> Unit) {
    val tradition = tradition
    val c = AgapeTheme.colors
    val feast = remember(d, tradition) { LiturgicalCalendar.feasts(tradition, d).any { it.rank != FeastRank.SEASON } }
    val fast = remember(d, tradition) { Fasting.status(tradition, d).level }
    val isFast = fast == FastLevel.FAST || fast == FastLevel.NO_MEAT || fast == FastLevel.STRICT
    Column(
        modifier.padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .semantics { contentDescription = d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape)
                .background(if (isSelected) c.primary else if (feast && inMonth) c.accent.copy(alpha = 0.16f) else Color.Transparent)
                .then(if (isToday && !isSelected) Modifier.border(1.5.dp, c.accent, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                d.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = AgapeTheme.type.ui, fontWeight = if (feast || isToday) FontWeight.Bold else FontWeight.Medium),
                color = when {
                    isSelected -> c.onPrimary
                    !inMonth -> c.inkFaint.copy(alpha = 0.45f)
                    feast -> c.accent
                    d.dayOfWeek == DayOfWeek.SUNDAY -> c.primary
                    else -> c.ink
                },
            )
        }
        if (tradition == Tradition.ORIENTAL_ORTHODOX) {
            Text(LiturgicalCalendar.coptic(d).day.toString(), style = MaterialTheme.typography.labelSmall, color = c.inkFaint.copy(alpha = if (inMonth) 1f else 0.45f))
        }
        Row(Modifier.padding(top = 3.dp).height(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isFast && inMonth) Box(Modifier.width(if (fast == FastLevel.STRICT) 14.dp else 10.dp).height(3.dp).clip(CircleShape).background(c.primary.copy(alpha = if (fast == FastLevel.STRICT) 0.95f else 0.6f)))
            if (active && inMonth) Box(Modifier.size(5.dp).border(1.dp, c.inkMuted, CircleShape))
        }
    }
}

@Composable
private fun LegendItem(text: String, mark: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        mark()
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = AgapeTheme.colors.inkMuted, maxLines = 1)
    }
}

@Composable
private fun DayDetails(day: LocalDate, tradition: Tradition, lang: AppLanguage, done: List<String>, openPassage: (Passage) -> Unit) {
    val container = LocalContainer.current
    val c = AgapeTheme.colors
    val locale = Locale.forLanguageTag(lang.tag)
    val view = remember(day, tradition) { container.saints.day(tradition, day) }
    val principal = view.commemorations.firstOrNull()
    // A moveable or fixed great feast from the liturgical calendar, unless the menologion already names it.
    val feasts = remember(day, tradition, lang) {
        LiturgicalCalendar.feasts(tradition, day).filter { f -> principal?.entry?.name?.get(lang)?.contains(f.name[lang], ignoreCase = true) != true }
    }
    // The day's own icon, else the icon of All Saints, so the day never shows an empty frame.
    val icon = view.icon ?: remember { container.saints.generalIcon }
    val title = principal?.entry?.name?.get(lang) ?: feasts.firstOrNull()?.name?.get(lang)

    // The day itself: its icon large, then who is remembered and their life.
    AgapeCard(contentPadding = PaddingValues(0.dp)) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SaintIcon(icon, title.orEmpty(), width = 118.dp)
            Column(Modifier.weight(1f)) {
                Kicker(day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)))
                Spacer(Modifier.height(6.dp))
                feasts.forEach { f ->
                    Text(f.name[lang], style = if (f.rank == FeastRank.SEASON) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge, color = if (f.rank == FeastRank.SEASON) c.accent else c.primary)
                }
                if (principal != null) Text(principal.entry.name[lang], style = MaterialTheme.typography.headlineSmall, color = c.ink)
                Spacer(Modifier.height(10.dp))
                DayStatusPill(remember(day, tradition) { Fasting.status(tradition, day) }, tradition)
            }
        }
        principal?.entry?.life?.let { life ->
            Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(0.5.dp).background(c.line))
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Kicker(stringResource(R.string.calendar_life), color = c.inkMuted)
                LifeText(life.text[lang], collapsedLines = 8, style = MaterialTheme.typography.bodyLarge, color = c.ink)
            }
        }
        icon?.let {
            Text(
                stringResource(R.string.icon_credit, it.title, it.licence) + if (view.icon == null) " · " + stringResource(R.string.calendar_general_icon) else "",
                style = MaterialTheme.typography.labelSmall, color = c.inkFaint, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
            )
        }
    }

    // Everyone else remembered on this day.
    val others = view.commemorations.drop(1)
    if (others.isNotEmpty()) {
        AgapeCard {
            Kicker(stringResource(R.string.saint_also), color = c.inkMuted)
            others.forEachIndexed { i, o ->
                if (i > 0) Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(0.5.dp).background(c.line))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // A small icon only where the saint has one of their own; the general icons would repeat on every row.
                    if (o.ownIcon) SaintIcon(o.icon, o.entry.name[lang], width = 46.dp)
                    Column(Modifier.weight(1f)) {
                        Text(o.entry.name[lang], style = MaterialTheme.typography.titleMedium, color = c.ink)
                        o.entry.life?.let { LifeText(it.text[lang], collapsedLines = 3) }
                    }
                }
            }
        }
    }

    DayReadings(view, tradition, lang, openPassage)

    // The date in the three calendars, and what the reader did on this day.
    AgapeCard(tonal = true) {
        ThreeDates(view, tradition, lang)
        if (done.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Kicker(stringResource(R.string.calendar_your_day), color = c.inkMuted)
            done.distinct().take(6).forEach { Text("✓ $it", style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.padding(top = 3.dp)) }
        }
    }
}

/** The day in the Gregorian, Julian and Coptic calendars; the tradition's own calendar stands out. */
@Composable
private fun ThreeDates(view: SaintDayView, tradition: Tradition, lang: AppLanguage) {
    val c = AgapeTheme.colors
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.forLanguageTag(lang.tag))
    val coptic = view.coptic
    listOf(
        Triple(R.string.calendar_gregorian, view.gregorian.format(format), tradition == Tradition.CATHOLIC),
        Triple(R.string.calendar_julian, view.julian.format(format), tradition == Tradition.EASTERN_ORTHODOX),
        Triple(R.string.calendar_coptic, "${coptic.day} ${coptic.monthName(lang == AppLanguage.DUTCH)} ${coptic.year}", tradition == Tradition.ORIENTAL_ORTHODOX),
    ).forEach { (label, date, own) ->
        Row(Modifier.padding(top = 2.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = c.inkFaint, modifier = Modifier.width(132.dp))
            Text(
                date, style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (own) FontWeight.SemiBold else FontWeight.Normal),
                color = if (own) c.ink else c.inkMuted,
            )
        }
    }
}

/** A life or summary that folds open when it is longer than a few lines. */
@Composable
private fun LifeText(text: String, collapsedLines: Int, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium, color: Color = AgapeTheme.colors.inkMuted) {
    if (text.isBlank()) return
    val c = AgapeTheme.colors
    var open by rememberSaveable(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Text(
        text, style = style, color = color,
        maxLines = if (open) Int.MAX_VALUE else collapsedLines, overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!open) overflows = it.hasVisualOverflow },
        modifier = Modifier.padding(top = 4.dp).animateContentSize(),
    )
    if (open || overflows) {
        Text(
            stringResource(if (open) R.string.calendar_less else R.string.calendar_more),
            style = MaterialTheme.typography.labelMedium, color = c.primary,
            modifier = Modifier.clickable { open = !open }.padding(vertical = 4.dp),
        )
    }
}

/** One reading as shown: its place in the service, the passage to open (if it could be read) and the citation. */
private data class ShownReading(val role: String, val passage: Passage?, val display: String)

/**
 * The readings of the selected day: from the bundled dataset where it has them (the Roman lectionary, 2025-2027),
 * otherwise, for the Byzantine calendar, from orthocal.info when online.
 */
@Composable
private fun DayReadings(view: SaintDayView, tradition: Tradition, lang: AppLanguage, openPassage: (Passage) -> Unit) {
    val container = LocalContainer.current
    val c = AgapeTheme.colors
    val bundled = remember(view) { view.readings.map { ShownReading(it.role, it.ref?.let { ref -> Passage.parse(ref) }, it.display) } }
    val online by produceState<List<ShownReading>?>(null, view, lang) {
        value = if (bundled.isEmpty() && tradition == Tradition.EASTERN_ORTHODOX) {
            container.daily.forDate(tradition, lang, view.gregorian)?.readings.orEmpty().mapNotNull { Passage.parse(it) }.map { p ->
                val testament = container.content.book(p.book)?.testament
                val role = when {
                    p.book == "PSA" -> "psalm"
                    p.book in setOf("MAT", "MRK", "LUK", "JHN") -> "gospel"
                    testament == Testament.NT -> "apostle"
                    else -> "old_testament"
                }
                ShownReading(role, p, p.toString())
            }
        } else emptyList()
    }
    val readings = bundled.ifEmpty { online.orEmpty() }
    AgapeCard(contentPadding = PaddingValues(0.dp)) {
        Kicker(stringResource(R.string.calendar_readings), Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp))
        if (readings.isEmpty()) {
            Text(
                stringResource(if (online == null) R.string.calendar_readings_loading else R.string.calendar_readings_none),
                style = MaterialTheme.typography.bodyMedium, color = c.inkMuted,
                modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 18.dp),
            )
        }
        readings.forEachIndexed { i, r ->
            if (i > 0) Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(0.5.dp).background(c.line))
            val p = r.passage
            Row(
                Modifier.fillMaxWidth().then(if (p != null) Modifier.clickable { openPassage(p) } else Modifier).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(readingRole(r.role)), style = MaterialTheme.typography.labelMedium, color = c.accent)
                    Text(p?.let { container.passageLabel(it, lang) } ?: r.display, style = MaterialTheme.typography.titleMedium, color = c.ink)
                    // The full citation when the passage opened is only its first piece (the Psalm verses, a reading in parts).
                    if (p != null && Regex("""[,;]|\band\b|\d[a-eA-E]""").containsMatchIn(r.display)) {
                        Text(r.display, style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
                    }
                }
                if (p != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.inkFaint)
            }
        }
        if (readings.isNotEmpty()) Spacer(Modifier.height(6.dp))
    }
}

private fun readingRole(role: String): Int = when (role) {
    "first_reading" -> R.string.reading_first
    "second_reading" -> R.string.reading_second
    "psalm", "psalm_gospel" -> R.string.reading_psalm
    "acclamation" -> R.string.reading_acclamation
    "gospel", "matins_gospel" -> R.string.reading_gospel
    "epistle", "apostle" -> R.string.reading_apostle
    "old_testament", "vespers" -> R.string.reading_old_testament
    "pauline" -> R.string.reading_pauline
    "catholic_epistle" -> R.string.reading_catholic_epistle
    "acts" -> R.string.reading_acts
    else -> R.string.reading_other
}
