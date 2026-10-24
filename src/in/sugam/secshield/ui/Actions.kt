package `in`.sugam.secshield.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import `in`.sugam.secshield.domain.ScoreEngine

/** Shared intents used by the Guard and app detail screens. Every open is guarded. */
object Actions {

    /** The one guarded fix opener: never crashes when a settings screen is missing. */
    fun openFix(ctx: Context, action: String?, pkg: String? = null) {
        try {
            ctx.startActivity(ScoreEngine.fixIntent(action, pkg))
        } catch (t: Throwable) {
            toast(ctx, "Could not open settings")
        }
    }

    fun openAppSettings(ctx: Context, pkg: String) {
        try {
            val i = Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:$pkg"))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (t: Throwable) {
            toast(ctx, "Could not open settings")
        }
    }

    fun openAppPermissions(ctx: Context, pkg: String) {
        try {
            // App info page carries the Permissions row on every Android version.
            openAppSettings(ctx, pkg)
        } catch (t: Throwable) {
            toast(ctx, "Could not open settings")
        }
    }

    private fun toast(ctx: Context, msg: String) {
        try {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        } catch (ignored: Throwable) {
        }
    }
}
