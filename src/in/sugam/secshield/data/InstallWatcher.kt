package `in`.sugam.secshield.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import `in`.sugam.secshield.domain.AppRisk
import `in`.sugam.secshield.domain.Severity
import `in`.sugam.secshield.data.Store

/**
 * Watches for new packages. A newly installed app is risk-scored immediately and,
 * if it holds elevated capabilities, the user is told about it.
 */
class InstallWatcher : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == null) return
        val action = intent.action
        if (action != Intent.ACTION_PACKAGE_ADDED
                && action != Intent.ACTION_PACKAGE_REPLACED
                && action != Intent.ACTION_PACKAGE_REMOVED) {
            return
        }
        if (action == Intent.ACTION_PACKAGE_REMOVED) {
            if (intent.getBooleanExtra("android.intent.extra.REMOVING", false)) {
                Store(ctx).log(Severity.OK, "App removed", "A package was uninstalled from this phone")
            }
            return
        }
        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg == ctx.packageName) return

        try {
            val all = AppRisk.scanAll(ctx)
            val found = all.firstOrNull { it.pkg == pkg } ?: return
            val store = Store(ctx)
            if (found.severity >= Severity.HIGH) {
                val title = "New app needs review: " + found.label
                val text = "Risk " + found.score + "/100 · " + found.shortReason()
                store.log(Severity.HIGH, title, text)
                if (store.toggle("alerts", true)) {
                    Notifier.alert(ctx, title, text)
                }
            } else {
                // Verdict for every new app, not only the risky ones.
                val verdict = when {
                    found.severity >= Severity.MEDIUM -> "needs a look"
                    found.severity >= Severity.LOW -> "looks mostly safe"
                    else -> "looks safe"
                }
                val title = "New app installed: " + found.label
                val text = "Verdict: $verdict · Risk ${found.score}/100 · ${found.shortReason()}"
                store.log(found.severity, title, text)
                if (store.toggle("alerts", true)) {
                    Notifier.verdict(ctx, title, text)
                }
            }
        } catch (t: Throwable) {
            // Never let a receiver crash the package installer
        }
    }
}
