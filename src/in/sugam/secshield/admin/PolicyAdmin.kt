package `in`.sugam.secshield.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import `in`.sugam.secshield.domain.Severity
import `in`.sugam.secshield.data.Store

/**
 * Device administrator. Grants the ability to set a password policy, wipe on failed
 * unlocks and lock the screen. Every one of those still needs the user's consent
 * in Settings before it can act.
 */
class PolicyAdmin : DeviceAdminReceiver() {

    override fun onEnabled(ctx: Context, intent: Intent) {
        super.onEnabled(ctx, intent)
        Store(ctx).log(Severity.OK,
                "SecShield became device administrator",
                "Password policy and wipe on failed unlock are now available")
        Toast.makeText(ctx, "SecShield can now set password policy", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(ctx: Context, intent: Intent) {
        super.onDisabled(ctx, intent)
        Store(ctx).log(Severity.HIGH,
                "SecShield lost device administrator rights",
                "Password policy and wipe on failed unlock are no longer available")
    }

    override fun onDisableRequested(ctx: Context, intent: Intent): CharSequence {
        return "Removing SecShield as device administrator means it can no longer enforce a password policy or wipe on failed unlocks."
    }
}
