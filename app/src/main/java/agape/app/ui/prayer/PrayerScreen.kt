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
        // Every rope is open to everyone; the user's own church comes first.
        item { Kicker(stringResource(R.string.ropes_title), Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)) }
        item {
            val ropes = when (tradition) {
                Tradition.CATHOLIC -> listOf(null, PrayerRope.CHOTKI, PrayerRope.KYRIE)
                Tradition.ORIENTAL_ORTHODOX -> listOf(PrayerRope.KYRIE, PrayerRope.CHOTKI, null)
                else -> listOf(PrayerRope.CHOTKI, PrayerRope.KYRIE, null)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ropes.forEach { rope ->
                    val title = if (rope == null) stringResource(R.string.rosary_title) else stringResource(rope.title)
                    val sub = if (rope == null) MysterySet.forDay(java.time.LocalDate.now().dayOfWeek).title[lang] else stringResource(rope.subtitle)
                    AgapeCard(Modifier.width(236.dp), onClick = { if (rope == null) openRosary() else openRope(rope) }, contentPadding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 1)
                                Spacer(Modifier.height(4.dp))
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = c.inkMuted, maxLines = 2)
                            }
                            Spacer(Modifier.width(10.dp))
                            if (rope == null) RosaryMark(Modifier.size(44.dp)) else RopeMark(rope, Modifier.size(44.dp))
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShortcutCard(stringResource(R.string.day_title), stringResource(R.string.day_card_hint), Icons.Outlined.Schedule, openDay, Modifier.weight(1f))
                ShortcutCard(stringResource(R.string.intentions_title), stringResource(R.string.intentions_card_hint), Icons.Outlined.FavoriteBorder, openIntentions, Modifier.weight(1f))
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
        items(book.hours, key = { it.id }) { hour ->
            val isNow = hour.id == current?.id
            val fixed = reminders[hour.id]?.time ?: LocalTime.parse(hour.time)
            val time = remember(settings, fixed) { SunSchedule.timeOn(hour.id, fixed, settings, java.time.LocalDate.now()) }
            ListRow(
                title = hour.name[lang],
                subtitle = hour.subtitle[lang],
                onClick = { openHour(hour.id) },
                leading = {
                    Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(time.format(clock), style = MaterialTheme.typography.labelLarge, color = if (isNow) c.primary else c.inkMuted)
                        Box(Modifier.padding(top = 4.dp).size(if (isNow) 10.dp else 6.dp).clip(CircleShape).background(if (isNow) c.accent else c.line))
                    }
                },
                trailing = {
                    val on = settings.prayerNotifications && hour.id in reminders
                    IconButton(onClick = { toggleReminder(hour) }) {
                        Icon(
                            if (on) Icons.Outlined.Notifications else Icons.Outlined.NotificationsNone,
                            stringResource(if (on) R.string.prayer_reminder_on else R.string.prayer_reminder_off, hour.name[lang]),
                            tint = if (on) c.primary else c.inkFaint,
                        )
                    }
                },
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
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 2)
        Spacer(Modifier.height(4.dp))
        Text(hint, style = MaterialTheme.typography.bodySmall, color = c.inkMuted, maxLines = 3)
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
