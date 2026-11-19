package agape.app.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import agape.app.ui.theme.Fixed
import agape.app.R
import agape.app.data.plans.PrayerClock
import agape.app.notifications.HourReminder
import agape.app.data.rosary.PrayerRope
import agape.app.data.saints.SaintOfDay
import agape.app.data.schedule.SunSchedule
import agape.app.ui.components.AgapeIcons
import agape.app.ui.components.Section
import agape.app.ui.components.SaintIcon
import agape.app.ui.components.Stat
import agape.app.ui.components.buttonShape
import agape.app.ui.main.PrayerRoute
import agape.app.ui.prayer.RopeMark
import agape.app.ui.prayer.title
import agape.app.ui.prayer.RosaryMark
import agape.app.ui.prayer.ropeSubtitle
import agape.app.ui.theme.Radius
import agape.app.ui.theme.Spacing
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import java.time.LocalTime
import agape.app.core.model.Passage
import agape.app.core.model.Testament
import agape.app.core.model.Tradition
import agape.app.data.calendar.ChurchCalendar
import agape.app.data.calendar.Fasting
import agape.app.ui.components.DayStatusPill
import agape.app.data.content.chapterCount
import agape.app.data.daily.DailyInfo
import agape.app.data.plans.planDays
import agape.app.data.rosary.MysterySet
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.LocalSettings
import agape.app.ui.bookName
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.Backdrop
import agape.app.ui.components.Kicker
import agape.app.ui.main.BibleRoute
import agape.app.ui.passageLabel
import agape.app.ui.plans.MyPlanCard
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import android.app.Activity
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Cream on the dark backdrop, whatever the style: the backdrop is always dark. */
private val OnBackdrop = Fixed.onImage

private val clock = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun HomeScreen(
    openReader: (String, Int, Int) -> Unit,
    openPlans: () -> Unit,
    openPlan: (String) -> Unit,
    openTab: (Any) -> Unit,
    openHelp: () -> Unit,
    openProfile: () -> Unit,
    openRosary: () -> Unit,
    openRope: () -> Unit,
    openHour: (String) -> Unit,
    openCalendar: () -> Unit,
) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val settings = LocalSettings.current
    val tradition = tradition
    val today = remember { LocalDate.now() }
    val c = AgapeTheme.colors
    val context = LocalContext.current
    val shareVia = stringResource(R.string.home_share_via)
    val hour = remember(tradition) { PrayerClock.current(container.content.prayerBook(tradition).hours, LocalTime.now()) }

    val verseRef = remember(tradition, today) { Passage.parse(container.content.verseOfDay(tradition, today)) }
    val verseText by produceState<String?>(null, verseRef, lang) {
        value = verseRef?.let { container.bible.passage(it, lang).joinToString(" ") { v -> v.text } }
    }
    val daily by produceState<DailyInfo?>(null, tradition, lang) { value = container.daily.today(tradition, lang) }
    val saint = remember(tradition, lang, today) { container.saints.forDate(tradition, lang, today) }
    val dayStatus = remember(tradition, today) { Fasting.status(tradition, today) }
    val plans by container.user.plans.collectAsStateWithLifecycle(emptyList())
    val stats by container.user.stats.collectAsStateWithLifecycle(null)
    val totalChapters = remember(tradition, lang) { container.content.canon(tradition, lang).sumOf { it.chapterCount(lang) } }
    val background = remember(today) { container.backgrounds.forDate(today) }
    val verseLabel = verseRef?.let { container.passageLabel(it, lang) }.orEmpty()
    // The verse may open or close a quotation of its own; the page supplies the outer quotes.
    val quoted = verseText?.let { "“${it.trim(' ', '“', '”', '"')}”" }

    // The picture behind the status bar is dark in every style, so the clock and icons turn light while Home shows.
    val view = LocalView.current
    DisposableEffect(view, c.isDark) {
        val window = (view.context as? Activity)?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.isAppearanceLightStatusBars = false
        onDispose { insets?.isAppearanceLightStatusBars = !c.isDark }
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // The day: date, the feast or saint of the day, fasting and the Easter greeting, over the picture of the day.
        item {
            Backdrop(background, Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = Spacing.xl)) {
                    // Help and settings sit in the top corner, as in a top bar, clear of the picture and the day.
                    Row(Modifier.fillMaxWidth().padding(top = Spacing.s), horizontalArrangement = Arrangement.End) {
                        RoundIcon(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.home_help), openHelp)
                        Spacer(Modifier.width(Spacing.m))
                        RoundIcon(Icons.Outlined.Settings, stringResource(R.string.home_profile), openProfile)
                    }
                    Spacer(Modifier.height(Spacing.xxl * 2))
                    DayHeader(
                        date = today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.forLanguageTag(lang.tag)))
                            .replaceFirstChar { it.titlecase(Locale.forLanguageTag(lang.tag)) },
                        saint = saint,
                        openCalendar = openCalendar,
                    )
                    Spacer(Modifier.height(Spacing.l))
                    DayStatusPill(dayStatus, tradition, onDark = true)
                    if (ChurchCalendar.isPaschalSeason(tradition, today)) {
                        Spacer(Modifier.height(Spacing.l))
                        Text(
                            stringResource(if (tradition == Tradition.CATHOLIC) R.string.home_paschal_catholic else R.string.home_paschal_orthodox),
                            style = MaterialTheme.typography.titleLarge, color = Fixed.paleGold,
                        )
                    }
                    Spacer(Modifier.height(Spacing.xxl))
                }
            }
        }

        // The three things to do: read on, pray the hour, take the rope or the Rosary.
        item {
            val pos = settings.lastPosition
            val ropeLike = if (tradition == Tradition.CATHOLIC) {
                MainAction(
                    stringResource(R.string.rosary_title),
                    stringResource(R.string.home_rosary_today, MysterySet.forDay(today.dayOfWeek).title[lang]),
                    { RosaryMark(Modifier.size(28.dp)) }, openRosary,
                )
            } else {
                MainAction(
                    stringResource(PrayerRope.CHOTKI.title),
                    stringResource(ropeSubtitle(tradition)),
                    { RopeMark(PrayerRope.CHOTKI, Modifier.size(28.dp)) }, openRope,
                )
            }
            val hourLine = hour?.let { h ->
                val fixed = settings.hourReminders.mapNotNull { HourReminder.decode(it) }.firstOrNull { it.hourId == h.id }?.time ?: LocalTime.parse(h.time)
                stringResource(R.string.home_action_pray_hour, h.name[lang], SunSchedule.timeOn(h.id, fixed, settings, today).format(clock))
            }
            AgapeCard(Modifier.padding(horizontal = Spacing.l).padding(top = Spacing.l, bottom = Spacing.s), contentPadding = PaddingValues(0.dp)) {
                listOf(
                    MainAction(
                        stringResource(R.string.home_action_read),
                        if (pos != null) stringResource(R.string.home_action_read_on, "${container.bookName(pos.book, lang)} ${pos.chapter}")
                        else stringResource(R.string.home_start_reading),
                        { Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = c.primary, modifier = Modifier.size(24.dp)) },
                        { if (pos != null) openReader(pos.book, pos.chapter, pos.verse) else openTab(BibleRoute) },
                    ),
                    MainAction(
                        stringResource(R.string.home_action_pray),
                        hourLine ?: stringResource(R.string.home_action_pray_day),
                        { Icon(AgapeIcons.Candle, null, tint = c.primary, modifier = Modifier.size(24.dp)) },
                        { if (hour != null) openHour(hour.id) else openTab(PrayerRoute) },
                    ),
                    ropeLike,
                ).forEachIndexed { i, a ->
                    if (i > 0) Box(Modifier.padding(start = 72.dp).fillMaxWidth().height(0.5.dp).background(c.line))
                    MainActionRow(a)
                }
            }
        }

        // Verse of the day.
        item {
            AgapeCard(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
                Kicker(stringResource(R.string.home_verse_of_day))
                Spacer(Modifier.height(Spacing.s))
                Text(quoted ?: "…", style = AgapeTheme.type.scripture, color = c.ink)
                Spacer(Modifier.height(Spacing.s))
                Text(verseLabel, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = c.inkMuted)
                Spacer(Modifier.height(Spacing.m))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    VerseButton(stringResource(R.string.home_read), filled = true) {
                        verseRef?.let { r -> openReader(r.book, r.chapter, r.startVerse ?: 0) }
                    }
                    VerseButton(stringResource(R.string.home_share), filled = false) {
                        if (quoted != null) {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "$quoted\n— $verseLabel")
                            context.startActivity(Intent.createChooser(send, shareVia))
                        }
                    }
                }
            }
        }

        // Readings of the day
        item {
            val readings = daily?.readings.orEmpty().mapNotNull { Passage.parse(it) }
            ReadingsCard(readings, Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s), onOpen = { p -> openReader(p.book, p.chapter, p.startVerse ?: 0) })
        }

        // Reading plan: the one being followed, with its cover, or an invitation to start one.
        item {
            val active = plans.firstOrNull { it.active }
            val def = active?.let { container.content.plan(it.planId) }
            if (def != null) {
                val days = remember(def, tradition, lang) { container.content.planDays(def, tradition, lang) }
                MyPlanCard(def, active, days.size, Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) { openPlan(def.id) }
            } else {
                AgapeCard(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s), onClick = openPlans) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Kicker(stringResource(R.string.home_plan), color = c.inkMuted)
                            Spacer(Modifier.height(Spacing.s))
                            Text(stringResource(R.string.home_plan_start), style = MaterialTheme.typography.headlineSmall, color = c.ink)
                            Text(stringResource(R.string.home_plan_start_hint), style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                        }
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.inkFaint)
                    }
                }
            }
        }

        // Progress
        item {
            val s = stats
            Section(stringResource(R.string.home_progress), Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.m), titleColor = c.inkMuted) {
                Row(Modifier.fillMaxWidth().padding(top = Spacing.s), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Stat("${s?.currentStreak ?: 0}", stringResource(R.string.home_stat_streak))
                    Stat("${s?.chaptersRead ?: 0}/$totalChapters", stringResource(R.string.home_stat_read))
                    Stat("${s?.hoursPrayed ?: 0}", stringResource(R.string.home_stat_prayed))
                }
            }
        }

        saint?.icon?.let { icon ->
            item {
                Text(
                    stringResource(R.string.icon_credit, icon.title, icon.licence),
                    style = MaterialTheme.typography.labelSmall, color = c.inkFaint, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Spacing.xl).padding(top = Spacing.m),
                )
            }
        }
        background?.let { b ->
            item {
                Text(
                    stringResource(R.string.home_background_credit, b.title, b.licence),
                    style = MaterialTheme.typography.labelSmall, color = c.inkFaint, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.m),
                )
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(Fixed.scrim.copy(alpha = 0.28f))
            .border(1.dp, OnBackdrop.copy(alpha = 0.35f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = OnBackdrop, modifier = Modifier.size(22.dp)) }
}

/** Read and Share under the verse of the day: the first filled, the second outlined. */
@Composable
private fun VerseButton(text: String, filled: Boolean, onClick: () -> Unit) {
    val c = AgapeTheme.colors
    Surface(
        onClick = onClick,
        shape = buttonShape,
        color = if (filled) c.primary else c.surface,
        border = if (filled) null else BorderStroke(1.dp, c.line),
    ) {
        Text(
            text, style = MaterialTheme.typography.labelLarge, color = if (filled) c.onPrimary else c.ink,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.m),
        )
    }
}

/** The date, and under it the feast or saint of the day; the saint opens the calendar on today. */
@Composable
private fun DayHeader(date: String, saint: SaintOfDay?, openCalendar: () -> Unit) {
    Kicker(stringResource(R.string.home_today), color = OnBackdrop.copy(alpha = 0.8f))
    Spacer(Modifier.height(Spacing.xs))
    Text(date, style = MaterialTheme.typography.headlineSmall, color = OnBackdrop, modifier = Modifier.semantics { heading() })
    Box(Modifier.padding(top = Spacing.m).width(Spacing.xxl).height(2.dp).background(Fixed.paleGold))
    if (saint != null) {
        Spacer(Modifier.height(Spacing.m))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).clickable(onClick = openCalendar).padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (saint.icon != null) {
                SaintIcon(saint.icon, saint.name, width = 48.dp)
                Spacer(Modifier.width(Spacing.m))
            }
            Column(Modifier.weight(1f)) {
                Kicker(stringResource(if (saint.isFeast) R.string.saint_feast else R.string.home_saint), color = Fixed.paleGold)
                Spacer(Modifier.height(Spacing.xxs))
                Text(saint.name, style = MaterialTheme.typography.titleLarge, color = OnBackdrop, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (saint.others.isNotEmpty()) {
                    Text(
                        stringResource(R.string.home_saint_also, saint.others.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall, color = OnBackdrop.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = OnBackdrop.copy(alpha = 0.85f))
        }
    }
}

/** One of the three main actions on Home: a mark, a title, a line saying what it opens, and the action. */
private class MainAction(val title: String, val detail: String, val mark: @Composable () -> Unit, val onClick: () -> Unit)

@Composable
private fun MainActionRow(a: MainAction) {
    val c = AgapeTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = a.onClick).padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) { a.mark() }
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(a.title, style = MaterialTheme.typography.titleLarge, color = c.ink)
            Text(a.detail, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.inkFaint)
    }
}

/** The day's readings, labelled as in the lectionary: Law and Prophets, Psalm, Gospel, Apostle. Folds away. */
@Composable
private fun ReadingsCard(readings: List<Passage>, modifier: Modifier = Modifier, onOpen: (Passage) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    var open by rememberSaveable { mutableStateOf(true) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    AgapeCard(modifier, contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = Spacing.xl, vertical = Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).height(22.dp).clip(CircleShape).background(c.primary))
            Spacer(Modifier.width(Spacing.m))
            Text(stringResource(R.string.home_readings), style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.inkMuted, modifier = Modifier.rotate(turn))
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically()) {
            Column(Modifier.padding(bottom = Spacing.s)) {
                if (readings.isEmpty()) {
                    Text(
                        stringResource(R.string.home_readings_offline), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted,
                        modifier = Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.m),
                    )
                }
                readings.forEachIndexed { i, p ->
                    if (i > 0) Box(Modifier.padding(horizontal = Spacing.xl).fillMaxWidth().height(0.5.dp).background(c.line))
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(p) }.padding(horizontal = Spacing.xl, vertical = Spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Kicker(stringResource(readingLabel(container.content.book(p.book)?.testament, p.book)), color = c.inkMuted)
                            Spacer(Modifier.height(Spacing.xs))
                            Text(container.passageLabel(p, lang), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                        }
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.inkFaint)
                    }
                }
            }
        }
    }
}

private val gospels = setOf("MAT", "MRK", "LUK", "JHN")

private fun readingLabel(testament: Testament?, book: String): Int = when {
    book == "PSA" -> R.string.home_reading_psalm
    book in gospels -> R.string.home_reading_gospel
    testament == Testament.NT -> R.string.home_reading_apostle
    else -> R.string.home_reading_law
}
