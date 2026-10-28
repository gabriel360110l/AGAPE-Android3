package agape.app.notifications

import agape.app.ui.LocalSettings
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Quiet mode while praying: Do Not Disturb (priority only) for as long as a prayer screen is open, and the screen
 * stays awake. Android lets an app change Do Not Disturb only after the user allows it once in system settings.
 */
object QuietPrayer {
    fun allowed(context: Context): Boolean = context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun openPermission(context: Context) {
        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Turns Do Not Disturb on if it was off; true when this call did so (and [end] should undo it). */
    fun begin(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted || nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return false
        return runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY) }.isSuccess
    }

    /** Undoes [begin], unless the user changed Do Not Disturb themselves in the meantime. */
    fun end(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted && nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
        }
    }
}

/** Put on a prayer screen: quiet mode for as long as it is shown, when the user turned it on. */
@Composable
fun QuietWhilePraying() {
    val on = LocalSettings.current.quietPrayer
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(on) {
        view.keepScreenOn = true
        val changed = on && QuietPrayer.begin(context)
        onDispose {
            view.keepScreenOn = false
            if (changed) QuietPrayer.end(context)
        }
    }
}
