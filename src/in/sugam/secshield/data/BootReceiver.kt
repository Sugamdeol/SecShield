package `in`.sugam.secshield.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import `in`.sugam.secshield.domain.Severity
import `in`.sugam.secshield.data.Store

/** Re-arms the app after a reboot and records it in the log. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == null) return
        if (intent.action != Intent.ACTION_BOOT_COMPLETED
                && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        Notifier.ensureChannel(ctx)
        try {
            val store = Store(ctx)
            store.log(Severity.OK,
                    if (intent.action == Intent.ACTION_BOOT_COMPLETED)
                        "Phone restarted, SecShield re-armed"
                    else
                        "SecShield was updated",
                    "The install monitor is running again")
            if (store.toggle("daily_scan", false)) {
                ScanScheduler.schedule(ctx)
            }
        } catch (ignored: Throwable) {
        }
    }
}
