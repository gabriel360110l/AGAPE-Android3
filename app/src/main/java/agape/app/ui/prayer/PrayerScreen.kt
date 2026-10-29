package agape.app.ui.prayer

import agape.app.R
import agape.app.core.model.Tradition
import agape.app.core.prefs.TourStop
import agape.app.data.rosary.MysterySet
import agape.app.data.rosary.PrayerRope
import agape.app.data.schedule.SunSchedule
import agape.app.data.content.PrayerHour
import agape.app.data.plans.PrayerClock
import agape.app.notifications.HourReminder
import agape.app.notifications.Notifications
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.LocalSettings
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.Flame
import agape.app.ui.components.cardShape
import agape.app.ui.components.daySky
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import agape.app.ui.components.AgapeIcons
import agape.app.ui.components.Divider
import agape.app.ui.components.Kicker
import agape.app.ui.components.ListRow
import agape.app.ui.components.Ornament
import agape.app.ui.components.PrimaryButton
import agape.app.ui.components.QuietButton
import agape.app.ui.components.TabHeader
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.absoluteValue
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun PrayerScreen(
    openHour: (String) -> Unit,
    openPrayer: (String) -> Unit,
    openRosary: () -> Unit,
    openRope: (PrayerRope) -> Unit,
    openDay: () -> Unit,
    openIntentions: () -> Unit,
) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val settings = LocalSettings.current
    val tradition = tradition
    val scope = rememberCoroutineScope()
    val book = remember(tradition) { container.content.prayerBook(tradition) }
    val prayers = remember(tradition) { container.content.listedPrayers(tradition) }
    val favorites by container.user.favorites.collectAsStateWithLifecycle(emptyList())
    val now = remember { LocalTime.now() }
    val current = remember(book) { PrayerClock.current(book.hours, now) }
    val next = remember(book) { PrayerClock.next(book.hours, now) }
    val reminders = settings.hourReminders.mapNotNull { HourReminder.decode(it) }.associateBy { it.hourId }
    val c = AgapeTheme.colors

    fun toggleReminder(hour: PrayerHour) = scope.launch {
        val updated = if (hour.id in reminders) settings.hourReminders.filterNot { it.startsWith("${hour.id}@") }.toSet()
        else settings.hourReminders + HourReminder(hour.id, LocalTime.parse(hour.time)).encode()
        container.settings.setHourReminders(updated)
        if (!settings.prayerNotifications) container.settings.setPrayerNotifications(true)
        Notifications.reschedule(container.appContext())
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TabHeader(kicker = book.book[lang], title = stringResource(R.string.tab_prayer)) }
        current?.let { hour ->
            item {
                val sky = remember(now, c.isDark) { daySky(now, c.isDark) }
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
                        .shadow(20.dp, cardShape, spotColor = sky.bottom.copy(alpha = 0.5f))
                        .clip(cardShape).background(Brush.verticalGradient(listOf(sky.top, sky.bottom)))
                        .clickable { openHour(hour.id) }.padding(22.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Kicker(stringResource(R.string.prayer_now), color = sky.muted)
                            Spacer(Modifier.height(8.dp))
                            Text(hour.name[lang], style = MaterialTheme.typography.displaySmall, color = sky.text)
                            Text(hour.subtitle[lang], style = MaterialTheme.typography.titleMedium, color = sky.muted)
                        }
                        Flame(size = 56.dp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(hour.theme[lang], style = MaterialTheme.typography.bodyLarge, color = sky.text, maxLines = 4)
                    Spacer(Modifier.height(20.dp))
                    Box(
                        Modifier.clip(CircleShape).background(sky.text).clickable { openHour(hour.id) }.padding(horizontal = 22.dp, vertical = 12.dp),
                    ) { Text(stringResource(R.string.prayer_begin), style = MaterialTheme.typography.labelLarge, color = sky.bottom) }
                    next?.takeIf { it.id != hour.id }?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.prayer_next, it.name[lang], it.time), style = MaterialTheme.typography.bodySmall, color = sky.muted)
                    }
                }
            }
        }
        // Every rope is open to everyone; the user's own church comes first. Swipe to choose.
        item { Kicker(stringResource(R.string.ropes_title), Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)) }
        item {
            val ropes = if (tradition == Tradition.CATHOLIC) listOf(null, PrayerRope.CHOTKI) else listOf(PrayerRope.CHOTKI, null)
            RopePicker(ropes, openRosary, openRope)
        }
        // Two shortcuts side by side, always the same size.
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShortcutCard(stringResource(R.string.day_title), stringResource(R.string.day_card_hint), Icons.Outlined.Schedule, openDay, Modifier.weight(1f).fillMaxHeight())
                ShortcutCard(stringResource(R.string.intentions_title), stringResource(R.string.intentions_card_hint), Icons.Outlined.FavoriteBorder, openIntentions, Modifier.weight(1f).fillMaxHeight())
            }
        }
        if (!settings.prayerNotifications && settings.prayerIntroSeen) {
            item {
                AgapeCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), tonal = true) {
                    Text(stringResource(R.string.prayer_notify_off), style = MaterialTheme.typography.bodyMedium, color = c.ink)
                    QuietButton(stringResource(R.string.prayer_notify_enable), {
                        scope.launch { enableNotifications(container, book.hours) }
                    })
                }
            }
        }
        val favHours = book.hours.filter { h -> favorites.any { it.id == "hour:${h.id}" } }
        val favPrayers = prayers.filter { p -> favorites.any { it.id == "prayer:${p.id}" } }
        if (favHours.isNotEmpty() || favPrayers.isNotEmpty()) {
            item { Kicker(stringResource(R.string.prayer_favorites), Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp)) }
            items(favHours, key = { "fh" + it.id }) { h -> ListRow(h.name[lang], subtitle = h.time, onClick = { openHour(h.id) }) }
            items(favPrayers, key = { "fp" + it.id }) { p -> ListRow(p.title[lang], subtitle = p.subtitle[lang], onClick = { openPrayer(p.id) }) }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Kicker(stringResource(R.string.prayer_hours), Modifier.weight(1f))
                TextButton(onClick = openDay) { Text(stringResource(R.string.day_edit_times), style = MaterialTheme.typography.labelLarge, color = c.primary) }
            }
        }
        itemsIndexed(book.hours, key = { _, h -> h.id }) { i, hour ->
            val fixed = reminders[hour.id]?.time ?: LocalTime.parse(hour.time)
            val time = remember(settings, fixed) { SunSchedule.timeOn(hour.id, fixed, settings, java.time.LocalDate.now()) }
            HourRow(
                name = hour.name[lang], subtitle = hour.subtitle[lang], time = time.format(clock),
                isNow = hour.id == current?.id, first = i == 0, last = i == book.hours.lastIndex,
                reminderOn = settings.prayerNotifications && hour.id in reminders,
                onOpen = { openHour(hour.id) }, onReminder = { toggleReminder(hour) },
            )
        }
        if (book.extras.isNotEmpty()) {
            item { Kicker(stringResource(R.string.prayer_services), Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp)) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    book.extras.forEach { e ->
                        AgapeCard(Modifier.width(200.dp), onClick = { openHour(e.id) }, contentPadding = PaddingValues(16.dp)) {
                            Text(e.name[lang], style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 2)
                            Spacer(Modifier.height(4.dp))
                            Text(e.subtitle[lang], style = MaterialTheme.typography.bodySmall, color = c.inkMuted, maxLines = 2)
                        }
                    }
                }
            }
        }
        item { Kicker(stringResource(R.string.prayer_more), Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp)) }
        items(prayers, key = { it.id }) { p ->
            ListRow(p.title[lang], subtitle = p.subtitle[lang], onClick = { openPrayer(p.id) })
        }
        item {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Ornament()
                Spacer(Modifier.height(10.dp))
                Text(book.intro[lang], style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
            }
        }
    }

    // First visit: explain the hours and invite the user to turn on notifications.
    if (!settings.prayerIntroSeen) PrayerIntroSheet(
        title = book.book[lang],
        intro = book.intro[lang],
        onEnable = { scope.launch { enableNotifications(container, book.hours); finishIntro(container) } },
        onLater = { scope.launch { finishIntro(container) } },
    )
}

private val clock = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun ShortcutCard(title: String, hint: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AgapeTheme.colors
    AgapeCard(modifier, onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(hint, style = MaterialTheme.typography.bodySmall, color = c.inkMuted, minLines = 3, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The prayer ropes one at a time, a large card per rope. Swiping brings the next one up from below as the current
 * one sinks away; the dots show which rope is shown.
 */
@Composable
private fun RopePicker(ropes: List<PrayerRope?>, openRosary: () -> Unit, openRope: (PrayerRope) -> Unit) {
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    val pager = rememberPagerState { ropes.size }
    Column {
        HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 12.dp) { page ->
            val rope = ropes[page]
            val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            val title = if (rope == null) stringResource(R.string.rosary_title) else stringResource(rope.title)
            val sub = if (rope == null) MysterySet.forDay(java.time.LocalDate.now().dayOfWeek).title[lang] else stringResource(rope.subtitle)
            val open = { if (rope == null) openRosary() else openRope(rope) }
            AgapeCard(
                Modifier.graphicsLayer {
                    // The card coming in rises from below and grows; the one leaving sinks and fades.
                    translationY = offset * 46.dp.toPx()
                    alpha = 1f - offset * 0.55f
                    scaleX = 1f - offset * 0.08f
                    scaleY = 1f - offset * 0.08f
                },
                onClick = open, contentPadding = PaddingValues(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, color = c.ink, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        Text(sub, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(14.dp))
                        Box(Modifier.clip(CircleShape).background(c.primary).clickable(onClick = open).padding(horizontal = 18.dp, vertical = 9.dp)) {
                            Text(stringResource(R.string.prayer_begin), style = MaterialTheme.typography.labelLarge, color = c.onPrimary)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    if (rope == null) RosaryMark(Modifier.size(84.dp)) else RopeMark(rope, Modifier.size(84.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
            repeat(ropes.size) { i ->
                val on = i == pager.currentPage
                Box(Modifier.padding(horizontal = 4.dp).height(6.dp).width(if (on) 18.dp else 6.dp).clip(CircleShape).background(if (on) c.accent else c.line))
            }
        }
    }
}

/**
 * One hour on the day's line: the time, a dot on a thread running through all the hours, the name, and its bell.
 * The hour of now is lit; a hairline separates each hour from the next.
 */
@Composable
private fun HourRow(
    name: String, subtitle: String, time: String, isNow: Boolean, first: Boolean, last: Boolean,
    reminderOn: Boolean, onOpen: () -> Unit, onReminder: () -> Unit,
) {
    val c = AgapeTheme.colors
    Column(Modifier.fillMaxWidth().background(if (isNow) c.accent.copy(alpha = 0.10f) else Color.Transparent)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onOpen).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                time, style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium),
                color = if (isNow) c.primary else c.inkMuted, modifier = Modifier.width(52.dp),
            )
            Box(Modifier.width(22.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).width(1.5.dp).background(if (first) Color.Transparent else c.line))
                    Box(Modifier.weight(1f).width(1.5.dp).background(if (last) Color.Transparent else c.line))
                }
                Box(
                    Modifier.size(if (isNow) 14.dp else 10.dp).clip(CircleShape).background(if (isNow) c.accent else c.surface)
                        .border(1.5.dp, if (isNow) c.accent else c.inkFaint, CircleShape),
                )
            }
            Column(Modifier.weight(1f).padding(start = 12.dp, top = 16.dp, bottom = 16.dp)) {
                if (isNow) Kicker(stringResource(R.string.prayer_now))
                Text(name, style = MaterialTheme.typography.titleMedium, color = c.ink)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onReminder) {
                Icon(
                    if (reminderOn) Icons.Outlined.Notifications else Icons.Outlined.NotificationsNone,
                    stringResource(if (reminderOn) R.string.prayer_reminder_on else R.string.prayer_reminder_off, name),
                    tint = if (reminderOn) c.primary else c.inkFaint,
                )
            }
        }
        if (!last) Box(Modifier.padding(start = 102.dp, end = 16.dp).fillMaxWidth().height(0.5.dp).background(c.line))
    }
}

private suspend fun finishIntro(container: agape.app.AppContainer) {
    container.settings.setPrayerIntroSeen()
    container.settings.markTourSeen(TourStop.PRAYER)
}

private suspend fun enableNotifications(container: agape.app.AppContainer, hours: List<PrayerHour>) {
    val s = container.settings.current()
    container.settings.setPrayerNotifications(true)
    container.settings.setInactivityReminders(true)
    if (s.hourReminders.isEmpty()) container.settings.setHourReminders(HourReminder.defaults(hours))
    Notifications.reschedule(container.appContext())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrayerIntroSheet(title: String, intro: String, onEnable: () -> Unit, onLater: () -> Unit) {
    val c = AgapeTheme.colors
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onEnable() }
    ModalBottomSheet(onDismissRequest = onLater, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(AgapeIcons.Candle, null, tint = c.primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(10.dp))
            Kicker(stringResource(R.string.tour_kicker_prayer))
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.prayer_intro_title, title), style = MaterialTheme.typography.headlineSmall, color = c.ink)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.tour_prayer_body), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
            Spacer(Modifier.height(8.dp))
            Text(intro, style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
            Spacer(Modifier.height(16.dp))
            Ornament()
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.prayer_notify_title), style = MaterialTheme.typography.titleLarge, color = c.ink)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.prayer_notify_body), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
            Spacer(Modifier.height(18.dp))
            PrimaryButton(stringResource(R.string.prayer_notify_enable), {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(context)) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else onEnable()
            })
            QuietButton(stringResource(R.string.not_now), onLater)
        }
    }
}

@Composable
fun FavoriteButton(id: String) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val favorites by container.user.favorites.collectAsStateWithLifecycle(emptyList())
    val on = favorites.any { it.id == id }
    IconButton(onClick = { scope.launch { container.user.setFavorite(id, !on) } }) {
        Icon(
            if (on) Icons.Outlined.Star else Icons.Outlined.StarBorder,
            stringResource(if (on) R.string.favorite_remove else R.string.favorite_add),
            tint = if (on) AgapeTheme.colors.accent else AgapeTheme.colors.inkMuted,
        )
    }
}
