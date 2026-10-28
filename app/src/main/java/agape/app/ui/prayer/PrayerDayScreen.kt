package agape.app.ui.prayer

import agape.app.AppContainer
import agape.app.R
import agape.app.core.prefs.CustomReminder
import agape.app.core.prefs.Settings
import agape.app.data.content.PrayerHour
import agape.app.data.schedule.Place
import agape.app.data.schedule.RoughLocation
import agape.app.data.schedule.SunSchedule
import agape.app.data.schedule.SunTimes
import agape.app.notifications.HourReminder
import agape.app.notifications.Notifications
import agape.app.notifications.QuietPrayer
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.LocalSettings
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Divider
import agape.app.ui.components.Kicker
import agape.app.ui.components.QuietButton
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private val clock = DateTimeFormatter.ofPattern("HH:mm")

/**
 * The user's day of prayer: which hours ring and when, whether the hours follow the sun, own prayer moments,
 * the church bell, and quiet mode while praying.
 */
@Composable
fun PrayerDayScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val settings = LocalSettings.current
    val lang = LocalLanguage.current
    val tradition = tradition
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = AgapeTheme.colors
    val hours = remember(tradition) { container.content.prayerBook(tradition).hours }
    val reminders = settings.hourReminders.mapNotNull { HourReminder.decode(it) }.associateBy { it.hourId }
    val moments = settings.customReminders.mapNotNull { CustomReminder.decode(it) }.sortedBy { it.time }
    val today = remember { LocalDate.now() }
    val sun = settings.place?.let { remember(it, today) { SunTimes.of(today, it.latitude, it.longitude, java.time.ZoneId.systemDefault()) } }

    var editHour by remember { mutableStateOf<PrayerHour?>(null) }
    var editMoment by remember { mutableStateOf<CustomReminder?>(null) }
    var choosePlace by remember { mutableStateOf(false) }
    var quietAsked by remember { mutableStateOf(false) }

    fun save(block: suspend AppContainer.() -> Unit) = scope.launch {
        container.block()
        Notifications.reschedule(container.appContext())
    }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save { this.settings.setPrayerNotifications(true) }
    }
    fun enable(on: Boolean) {
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(context)) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else save {
            this.settings.setPrayerNotifications(on)
            if (on && this.settings.current().hourReminders.isEmpty()) this.settings.setHourReminders(HourReminder.defaults(hours))
        }
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(stringResource(R.string.day_title), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AgapeCard {
                Kicker(stringResource(R.string.day_reminders))
                Spacer(Modifier.height(6.dp))
                Toggle(stringResource(R.string.settings_notify_hours), stringResource(R.string.day_reminders_hint), settings.prayerNotifications, ::enable)
                QuietButton(stringResource(R.string.day_test_bell), { Notifications.test(context) })
            }

            AgapeCard {
                Kicker(stringResource(R.string.day_hours))
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(if (settings.sunTimes && settings.place != null) R.string.day_hours_sun_hint else R.string.day_hours_hint),
                    style = MaterialTheme.typography.bodySmall, color = c.inkMuted,
                )
                Spacer(Modifier.height(6.dp))
                hours.forEach { hour ->
                    val reminder = reminders[hour.id]
                    val fixed = reminder?.time ?: LocalTime.parse(hour.time)
                    val sunny = settings.sunTimes && settings.place != null && SunSchedule.follows(hour.id)
                    val time = SunSchedule.timeOn(hour.id, fixed, settings, today)
                    val on = settings.prayerNotifications && reminder != null
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(hour.name[lang], style = MaterialTheme.typography.titleSmall, color = c.ink)
                            Text(hour.subtitle[lang], style = MaterialTheme.typography.bodySmall, color = c.inkMuted, maxLines = 1)
                        }
                        Row(
                            Modifier.clickable(enabled = !sunny) { editHour = hour }.padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (sunny) Icon(Icons.Outlined.WbSunny, stringResource(R.string.day_follows_sun), tint = c.accent, modifier = Modifier.padding(end = 4.dp).width(16.dp))
                            Text(time.format(clock), style = MaterialTheme.typography.titleMedium, color = if (sunny) c.accent else c.primary)
                        }
                        IconButton(onClick = {
                            save {
                                val current = this.settings.current()
                                val rest = current.hourReminders.filterNot { it.startsWith("${hour.id}@") }.toSet()
                                this.settings.setHourReminders(if (reminder != null && current.prayerNotifications) rest else rest + HourReminder(hour.id, fixed).encode())
                                if (!current.prayerNotifications) this.settings.setPrayerNotifications(true)
                            }
                        }) {
                            Icon(
                                if (on) Icons.Outlined.Notifications else Icons.Outlined.NotificationsNone,
                                stringResource(if (on) R.string.prayer_reminder_on else R.string.prayer_reminder_off, hour.name[lang]),
                                tint = if (on) c.primary else c.inkFaint,
                            )
                        }
                    }
                }
            }

            AgapeCard {
                Kicker(stringResource(R.string.day_sun))
                Spacer(Modifier.height(6.dp))
                Toggle(stringResource(R.string.day_sun_toggle), stringResource(R.string.day_sun_hint), settings.sunTimes) { on ->
                    if (on && settings.place == null) choosePlace = true
                    save { this.settings.setSunTimes(on) }
                }
                if (settings.place != null) {
                    Row(Modifier.fillMaxWidth().clickable { choosePlace = true }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(settings.place.label, style = MaterialTheme.typography.titleSmall, color = c.ink)
                            sun?.let {
                                Text(
                                    stringResource(R.string.day_sun_today, it.sunrise?.format(clock) ?: "—", it.sunset?.format(clock) ?: "—"),
                                    style = MaterialTheme.typography.bodySmall, color = c.inkMuted,
                                )
                            }
                        }
                        Text(stringResource(R.string.day_place_change), style = MaterialTheme.typography.labelLarge, color = c.primary)
                    }
                } else if (settings.sunTimes) QuietButton(stringResource(R.string.day_place_choose), { choosePlace = true })
            }

            AgapeCard {
                Kicker(stringResource(R.string.day_moments))
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.day_moments_hint), style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                moments.forEach { m ->
                    Row(Modifier.fillMaxWidth().clickable { editMoment = m }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.time.format(clock), style = MaterialTheme.typography.titleMedium, color = c.primary, modifier = Modifier.width(64.dp))
                        Text(m.label.ifBlank { stringResource(R.string.moment_default) }, style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = Modifier.weight(1f))
                        IconButton(onClick = { save { this.settings.setCustomReminders(this.settings.current().customReminders.filterNot { it.startsWith("${m.id}|") }.toSet()) } }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = c.inkFaint)
                        }
                    }
                }
                QuietButton(stringResource(R.string.day_moment_add), { editMoment = CustomReminder(UUID.randomUUID().toString().take(8), LocalTime.of(12, 0), "") })
            }

            AgapeCard {
                Kicker(stringResource(R.string.day_quiet))
                Spacer(Modifier.height(6.dp))
                Toggle(stringResource(R.string.day_quiet_toggle), stringResource(R.string.day_quiet_hint), settings.quietPrayer) { on ->
                    if (on && !QuietPrayer.allowed(context)) quietAsked = true
                    scope.launch { container.settings.setQuietPrayer(on) }
                }
                if (settings.quietPrayer && !QuietPrayer.allowed(context)) QuietButton(stringResource(R.string.day_quiet_allow), { QuietPrayer.openPermission(context) })
            }
        }
    }

    editHour?.let { hour ->
        val fixed = reminders[hour.id]?.time ?: LocalTime.parse(hour.time)
        TimeDialog(hour.name[lang], fixed, label = null, onDismiss = { editHour = null }) { time, _ ->
            editHour = null
            save {
                val current = this.settings.current()
                this.settings.setHourReminders(current.hourReminders.filterNot { it.startsWith("${hour.id}@") }.toSet() + HourReminder(hour.id, time).encode())
                if (!current.prayerNotifications) this.settings.setPrayerNotifications(true)
            }
        }
    }
    editMoment?.let { m ->
        TimeDialog(stringResource(R.string.day_moment), m.time, label = m.label, onDismiss = { editMoment = null }) { time, label ->
            editMoment = null
            save {
                val current = this.settings.current()
                this.settings.setCustomReminders(current.customReminders.filterNot { it.startsWith("${m.id}|") }.toSet() + m.copy(time = time, label = label.orEmpty()).encode())
                if (!current.prayerNotifications) this.settings.setPrayerNotifications(true)
            }
        }
    }
    if (choosePlace) PlaceDialog(onDismiss = { choosePlace = false }) { place ->
        choosePlace = false
        save {
            this.settings.setPlace(place)
            this.settings.setSunTimes(true)
        }
    }
    if (quietAsked) AlertDialog(
        onDismissRequest = { quietAsked = false },
        title = { Text(stringResource(R.string.day_quiet)) },
        text = { Text(stringResource(R.string.day_quiet_explain)) },
        confirmButton = { TextButton({ quietAsked = false; QuietPrayer.openPermission(context) }) { Text(stringResource(R.string.day_quiet_allow)) } },
        dismissButton = { TextButton({ quietAsked = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Toggle(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = AgapeTheme.colors
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary))
    }
}

/** A clock face to choose a time, and a name when [label] is not null (own prayer moments). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, initial: LocalTime, label: String?, onDismiss: () -> Unit, onSave: (LocalTime, String?) -> Unit) {
    val c = AgapeTheme.colors
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    var name by remember { mutableStateOf(label.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(
                    state,
                    colors = TimePickerDefaults.colors(
                        clockDialSelectedContentColor = c.onPrimary, selectorColor = c.primary,
                        timeSelectorSelectedContainerColor = c.primary.copy(alpha = 0.14f), timeSelectorSelectedContentColor = c.primary,
                    ),
                )
                if (label != null) OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                    label = { Text(stringResource(R.string.day_moment_name)) }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton({ onSave(LocalTime.of(state.hour, state.minute), if (label != null) name.trim() else null) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Choose the city the sun times are for, or use the phone's rough location. */
@Composable
private fun PlaceDialog(onDismiss: () -> Unit, onPick: (Place) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = AgapeTheme.colors
    val here = stringResource(R.string.day_place_here)
    var failed by remember { mutableStateOf(false) }
    fun locate() = scope.launch { RoughLocation.find(context, here)?.let(onPick) ?: run { failed = true } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) locate() else failed = true }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(stringResource(R.string.day_place_choose)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { if (RoughLocation.permitted(context)) locate() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.MyLocation, null, tint = c.primary, modifier = Modifier.padding(end = 10.dp))
                        Column {
                            Text(here, style = MaterialTheme.typography.titleSmall, color = c.primary)
                            Text(stringResource(if (failed) R.string.day_place_failed else R.string.day_place_here_hint), style = MaterialTheme.typography.bodySmall, color = if (failed) c.danger else c.inkMuted)
                        }
                    }
                    Divider()
                }
                items(Place.cities, key = { it.label }) { p ->
                    Text(p.label, style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 12.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
