package agape.app.ui.me

import agape.app.R
import agape.app.core.model.Tradition
import agape.app.core.model.VisualStyle
import agape.app.notifications.HourReminder
import agape.app.notifications.Notifications
import agape.app.ui.LocalContainer
import agape.app.ui.LocalSettings
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Kicker
import agape.app.ui.components.Pill
import agape.app.ui.components.QuietButton
import agape.app.ui.onboarding.LanguageSwitch
import agape.app.ui.onboarding.StyleSwatches
import agape.app.ui.onboarding.ThemeModeSwitch
import agape.app.ui.onboarding.styleName
import agape.app.ui.onboarding.traditionName
import agape.app.ui.theme.AgapeTheme
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, openLegal: (String) -> Unit) {
    val container = LocalContainer.current
    val settings = LocalSettings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = AgapeTheme.colors
    var pendingTradition by remember { mutableStateOf<Tradition?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var fontScale by remember(settings.fontScale) { mutableFloatStateOf(settings.fontScale) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { setPrayerNotifications(container, true) }
    }

    pendingTradition?.let { t ->
        AlertDialog(
            onDismissRequest = { pendingTradition = null },
            title = { Text(stringResource(R.string.settings_change_church)) },
            text = { Text(stringResource(R.string.settings_change_church_text, stringResource(traditionName(t)))) },
            confirmButton = {
                TextButton({
                    pendingTradition = null
                    scope.launch {
                        container.settings.setTradition(t)
                        // Hours differ per tradition, so reminders start again from the new defaults.
                        container.settings.setHourReminders(HourReminder.defaults(container.content.prayerBook(t).hours))
                        Notifications.reschedule(context)
                    }
                }) { Text(stringResource(R.string.continue_label)) }
            },
            dismissButton = { TextButton({ pendingTradition = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.settings_delete_account)) },
            text = { Text(stringResource(R.string.settings_delete_text)) },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    scope.launch {
                        runCatching { container.auth.deleteAccount() }
                        container.user.clear()
                        container.settings.clearPersonal()
                    }
                }) { Text(stringResource(R.string.settings_delete_confirm), color = c.danger) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(stringResource(R.string.me_settings), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingsCard(R.string.settings_language) {
                LanguageSwitch()
            }
            SettingsCard(R.string.settings_style) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VisualStyle.entries.forEach { s ->
                        Pill(stringResource(styleName(s)), settings.style == s, { scope.launch { container.settings.setStyle(s) } })
                    }
                }
                Spacer(Modifier.height(12.dp))
                settings.style?.let { StyleSwatches(it) }
                Spacer(Modifier.height(12.dp))
                ThemeModeSwitch(settings.themeMode) { m -> scope.launch { container.settings.setThemeMode(m) } }
            }
            SettingsCard(R.string.settings_church) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tradition.entries.forEach { t ->
                        Pill(stringResource(traditionName(t)), settings.tradition == t, { if (settings.tradition != t) pendingTradition = t })
                    }
                }
            }
            SettingsCard(R.string.settings_notifications) {
                ToggleRow(stringResource(R.string.settings_notify_hours), stringResource(R.string.settings_notify_hours_hint), settings.prayerNotifications) { on ->
                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(context)) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else scope.launch { setPrayerNotifications(container, on) }
                }
                ToggleRow(stringResource(R.string.settings_notify_inactive), stringResource(R.string.settings_notify_inactive_hint), settings.inactivityReminders) { on ->
                    scope.launch {
                        container.settings.setInactivityReminders(on)
                        Notifications.reschedule(context)
                    }
                }
            }
            SettingsCard(R.string.settings_font_size) {
                Text(
                    "Aa — " + stringResource(R.string.settings_font_preview),
                    style = AgapeTheme.type.scripture.copy(fontSize = AgapeTheme.type.scripture.fontSize * (fontScale / settings.fontScale)),
                    color = c.ink,
                )
                Slider(
                    value = fontScale, onValueChange = { fontScale = it }, valueRange = 0.85f..1.6f, steps = 6,
                    onValueChangeFinished = { scope.launch { container.settings.setFontScale(fontScale) } },
                    colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary),
                )
            }
            SettingsCard(R.string.settings_more) {
                QuietButton(stringResource(R.string.settings_replay_tour), { scope.launch { container.settings.resetTour() } })
                QuietButton(stringResource(R.string.legal_privacy), { openLegal("privacy") })
                QuietButton(stringResource(R.string.legal_terms), { openLegal("terms") })
                QuietButton(stringResource(R.string.settings_delete_account), { confirmDelete = true })
            }
        }
    }
}

private suspend fun setPrayerNotifications(container: agape.app.AppContainer, on: Boolean) {
    val s = container.settings.current()
    container.settings.setPrayerNotifications(on)
    if (on && s.hourReminders.isEmpty() && s.tradition != null) {
        container.settings.setHourReminders(HourReminder.defaults(container.content.prayerBook(s.tradition).hours))
    }
    Notifications.reschedule(container.appContext())
}

@Composable
private fun SettingsCard(title: Int, content: @Composable () -> Unit) {
    AgapeCard {
        Kicker(stringResource(title))
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun ToggleRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = AgapeTheme.colors
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary))
    }
}
