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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.ExperimentalFoundationApi
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
    val reminders = settings.hourReminders.mapNotNull { HourReminder.decode(it) }.associateBy { it.hourId }
    val c = AgapeTheme.colors

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TabHeader(kicker = book.book[lang], title = stringResource(R.string.tab_prayer)) }
        // The hours, one card each: swipe from hour to hour; it opens on the hour of now.
        if (book.hours.isNotEmpty()) {
            item { Kicker(stringResource(R.string.prayer_hours), Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp)) }
            item {
                HourPager(
                    hours = book.hours,
                    start = book.hours.indexOfFirst { it.id == current?.id }.coerceAtLeast(0),
                    nowId = current?.id,
                    timeOf = { hour ->
                        val fixed = reminders[hour.id]?.time ?: LocalTime.parse(hour.time)
                        SunSchedule.timeOn(hour.id, fixed, settings, java.time.LocalDate.now())
                    },
                    openHour = openHour,
                )
            }
        }
        // Every rope is open to everyone; the user's own church comes first. Swipe to choose.
        item { Kicker(stringResource(R.string.ropes_title), Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp)) }
        item {
            val ropes = if (tradition == Tradition.CATHOLIC) listOf(null, PrayerRope.CHOTKI) else listOf(PrayerRope.CHOTKI, null)
            RopePicker(ropes, openRosary, openRope)
        }
        // Two shortcuts side by side, always the same size.
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShortcutCard(stringResource(R.string.day_title), stringResource(R.string.day_card_hint), Icons.Outlined.Schedule, openDay, Modifier.weight(1f).fillMaxHeight())
                ShortcutCard(stringResource(R.string.intentions_title), stringResource(R.string.intentions_card_hint), Icons.Outlined.FavoriteBorder, openIntentions, Modifier.weight(1f).fillMaxHeight())
            }
        }
        val favHours = book.hours.filter { h -> favorites.any { it.id == "hour:${h.id}" } }
        val favPrayers = prayers.filter { p -> favorites.any { it.id == "prayer:${p.id}" } }
        if (favHours.isNotEmpty() || favPrayers.isNotEmpty()) {
            item { Kicker(stringResource(R.string.prayer_favorites), Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)) }
            items(favHours, key = { "fh" + it.id }) { h -> ListRow(h.name[lang], subtitle = h.time, onClick = { openHour(h.id) }) }
            items(favPrayers, key = { "fp" + it.id }) { p -> ListRow(p.title[lang], subtitle = p.subtitle[lang], onClick = { openPrayer(p.id) }) }
        }
        // Services and rules of prayer, one card each, swiped like the ropes.
        if (book.extras.isNotEmpty()) {
            item { Kicker(stringResource(R.string.prayer_services), Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp)) }
            item { ServicePager(book.extras, openHour) }
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
            val title = if (rope == null) stringResource(R.string.rosary_title) else stringResource(rope.title)
            val sub = if (rope == null) MysterySet.forDay(java.time.LocalDate.now().dayOfWeek).title[lang] else stringResource(rope.subtitle)
            val open = { if (rope == null) openRosary() else openRope(rope) }
            AgapeCard(
                Modifier.riseIn(pager.offsetOf(page)),
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
        PagerDots(ropes.size, pager.currentPage)
    }
}

/** Dots under a pager: the page shown is a longer gold dash. */
@Composable
private fun PagerDots(count: Int, current: Int) {
    val c = AgapeTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            val on = i == current
            Box(Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (on) 18.dp else 6.dp).clip(CircleShape).background(if (on) c.accent else c.line))
        }
    }
}

/** How far a page is from the centre, 0 when shown, for the rise-and-fade of cards coming in. */
@OptIn(ExperimentalFoundationApi::class)
private fun PagerState.offsetOf(page: Int) = ((currentPage - page) + currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)

private fun Modifier.riseIn(offset: Float) = graphicsLayer {
    translationY = offset * 46.dp.toPx()
    alpha = 1f - offset * 0.55f
    scaleX = 1f - offset * 0.08f
    scaleY = 1f - offset * 0.08f
}

/**
 * The hours of prayer, one large card each in the colours of its time of day: the time, "Pray" with the hour's
 * name, a word on what it prays, and a button to begin. Above, the times as chips to jump to an hour.
 */
@Composable
private fun HourPager(hours: List<PrayerHour>, start: Int, nowId: String?, timeOf: (PrayerHour) -> LocalTime, openHour: (String) -> Unit) {
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = start) { hours.size }
    val times = hours.map(timeOf)
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            hours.forEachIndexed { i, h ->
                val on = i == pager.currentPage
                Text(
                    times[i].format(clock), style = MaterialTheme.typography.labelLarge,
                    color = if (on) c.onPrimary else if (h.id == nowId) c.primary else c.inkMuted,
                    modifier = Modifier.clip(CircleShape).background(if (on) c.primary else Color.Transparent)
                        .border(1.dp, if (on) c.primary else c.line, CircleShape)
                        .clickable { scope.launch { pager.animateScrollToPage(i) } }.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 12.dp) { page ->
            val hour = hours[page]
            val time = times[page]
            val sky = remember(time, c.isDark) { daySky(time, c.isDark) }
            val isNow = hour.id == nowId
            Column(
                Modifier.riseIn(pager.offsetOf(page)).fillMaxWidth().height(330.dp)
                    .shadow(16.dp, cardShape, spotColor = sky.bottom.copy(alpha = 0.45f))
                    .clip(cardShape).background(Brush.verticalGradient(listOf(sky.top, sky.bottom)))
                    .clickable { openHour(hour.id) }.padding(24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(time.format(clock), style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold), color = sky.text, modifier = Modifier.weight(1f))
                    if (isNow) Flame(size = 48.dp)
                }
                Kicker(stringResource(if (isNow) R.string.prayer_now_pray else R.string.prayer_pray_this), color = sky.muted, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(4.dp))
                Text(hour.name[lang], style = MaterialTheme.typography.headlineMedium, color = sky.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (hour.subtitle[lang].isNotBlank()) Text(hour.subtitle[lang], style = MaterialTheme.typography.titleSmall, color = sky.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                Text(hour.theme[lang], style = MaterialTheme.typography.bodyMedium, color = sky.text, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(CircleShape).background(sky.text).clickable { openHour(hour.id) }.padding(horizontal = 24.dp, vertical = 12.dp),
                ) { Text(stringResource(R.string.prayer_begin), style = MaterialTheme.typography.labelLarge, color = sky.bottom) }
            }
        }
        PagerDots(hours.size, pager.currentPage)
    }
}

/** The services and rules of prayer, one card each, swiped like the prayer ropes. */
@Composable
private fun ServicePager(extras: List<PrayerHour>, openHour: (String) -> Unit) {
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    val pager = rememberPagerState { extras.size }
    Column {
        HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 12.dp) { page ->
            val e = extras[page]
            AgapeCard(Modifier.riseIn(pager.offsetOf(page)).height(210.dp), onClick = { openHour(e.id) }, contentPadding = PaddingValues(20.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Kicker(stringResource(R.string.prayer_service_n, page + 1, extras.size), color = c.inkMuted)
                        Spacer(Modifier.height(6.dp))
                        Text(e.name[lang], style = MaterialTheme.typography.headlineSmall, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(AgapeIcons.Candle, null, tint = c.accent, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(e.subtitle[lang].ifBlank { e.theme[lang] }, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box(Modifier.clip(CircleShape).background(c.primary).clickable { openHour(e.id) }.padding(horizontal = 18.dp, vertical = 9.dp)) {
                    Text(stringResource(R.string.prayer_open), style = MaterialTheme.typography.labelLarge, color = c.onPrimary)
                }
            }
        }
        PagerDots(extras.size, pager.currentPage)
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
