package agape.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import agape.app.AgapeApplication

/**
 * Herstelt gebedsalarmen na een reboot van het apparaat.
 * Android annuleert alle alarmen bij reboot; dit ontvangt de
 * BOOT_COMPLETED broadcast en herplant ze via AppStore.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        runCatching {
            AgapeApplication.instance.store.refreshAlarms()
        }
    }
}
