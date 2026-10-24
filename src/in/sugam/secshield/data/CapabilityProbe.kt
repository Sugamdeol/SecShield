package `in`.sugam.secshield.data

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager

/**
 * Enumerates the high-impact capabilities a third party app can hold on this device:
 * enabled accessibility services, notification listeners, overlay grants, special access.
 */
object CapabilityProbe {

    /** Package names of third party apps with an enabled accessibility service. */
    fun accessibilityServicePackages(ctx: Context): List<String> {
        val out = ArrayList<String>()
        try {
            val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return out
            val list = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK) ?: return out
            val seen = HashSet<String>()
            for (info in list) {
                if (info == null) continue
                val id = info.id ?: continue
                if (!id.contains("/")) continue
                val pkg = id.substring(0, id.indexOf('/'))
                if (pkg == ctx.packageName) continue
                if (seen.add(pkg)) out.add(pkg)
            }
        } catch (ignored: Throwable) {
        }
        return out
    }

    /** Package names of third party apps allowed to read notifications. */
    fun notificationListenerPackages(ctx: Context): List<String> {
        val out = ArrayList<String>()
        try {
            val raw = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
            if (raw.isNullOrEmpty()) return out
            val parts = raw.split(":")
            for (p in parts) {
                var pkg = p.trim()
                val slash = pkg.indexOf('/')
                if (slash >= 0) pkg = pkg.substring(0, slash)
                if (pkg.isEmpty()) continue
                if (pkg == ctx.packageName) continue
                if (!out.contains(pkg)) out.add(pkg)
            }
        } catch (ignored: Throwable) {
        }
        return out
    }

    /** Third party apps currently allowed to draw over other apps. */
    fun overlayPackages(ctx: Context): List<String> {
        val out = ArrayList<String>()
        try {
            val pm = ctx.packageManager
            val pkgs = pm.getInstalledPackages(0)
            val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return out
            for (pi in pkgs) {
                val ai = pi.applicationInfo ?: continue
                if (ai.packageName == ctx.packageName) continue
                if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
                val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, ai.uid, ai.packageName)
                if (mode == AppOpsManager.MODE_ALLOWED) out.add(ai.packageName)
            }
        } catch (ignored: Throwable) {
        }
        return out
    }

    /** Third party apps allowed to install other packages. */
    fun installerPackages(ctx: Context): List<String> {
        val out = ArrayList<String>()
        try {
            val pm = ctx.packageManager
            val pkgs = pm.getInstalledPackages(0)
            for (pi in pkgs) {
                val ai = pi.applicationInfo ?: continue
                if (ai.packageName == ctx.packageName) continue
                if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
                try {
                    if (pm.checkPermission(android.Manifest.permission.REQUEST_INSTALL_PACKAGES, ai.packageName)
                            == PackageManager.PERMISSION_GRANTED) {
                        out.add(ai.packageName)
                    }
                } catch (ignored: Throwable) {
                }
            }
        } catch (ignored: Throwable) {
        }
        return out
    }

    /** Package name of the installer for a given app, or null when unknown. */
    fun installSource(ctx: Context, pkg: String): String? {
        try {
            val pm = ctx.packageManager
            if (Build.VERSION.SDK_INT >= 30) {
                val info = pm.getInstallSourceInfo(pkg)
                var s = info.installingPackageName
                if (!TextUtils.isEmpty(s)) return s
                s = info.initiatingPackageName
                if (!TextUtils.isEmpty(s)) return s
                return null
            }
            val s = pm.getInstallerPackageName(pkg)
            return if (TextUtils.isEmpty(s)) null else s
        } catch (t: Throwable) {
            return null
        }
    }

    fun isFromPlayStore(ctx: Context, pkg: String): Boolean {
        val src = installSource(ctx, pkg) ?: return false
        return src == "com.android.vending" || src == "com.google.android.feedback"
    }

    /** Services inside an app's manifest that request accessibility or listener binding. */
    fun declaresAccessibilityService(ctx: Context, pkg: String): Boolean {
        try {
            val pm = ctx.packageManager
            val pi = pm.getPackageInfo(pkg, PackageManager.GET_SERVICES)
            val services = pi.services ?: return false
            for (si in services) {
                val perm = si.permission ?: continue
                if (perm.contains("BIND_ACCESSIBILITY_SERVICE")) return true
            }
        } catch (ignored: Throwable) {
        }
        return false
    }

    fun declaresNotificationListener(ctx: Context, pkg: String): Boolean {
        try {
            val pm = ctx.packageManager
            val pi = pm.getPackageInfo(pkg, PackageManager.GET_SERVICES)
            val services = pi.services ?: return false
            for (si in services) {
                val perm = si.permission ?: continue
                if (perm.contains("BIND_NOTIFICATION_LISTENER_SERVICE")) return true
            }
        } catch (ignored: Throwable) {
        }
        return false
    }

    /** Whether one specific app holds usage access on this device. */
    fun appHasUsageAccess(ctx: Context, pkg: String): Boolean {
        try {
            val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val ai = ctx.packageManager.getApplicationInfo(pkg, 0)
            val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, ai.uid, pkg)
            return mode == AppOpsManager.MODE_ALLOWED
        } catch (t: Throwable) {
            return false
        }
    }

    /**
     * Last used time per package, from UsageStats. Only returns data when
     * SecShield itself holds usage access; otherwise the map is empty.
     */
    fun lastUsedMap(ctx: Context): Map<String, Long> {
        val out = HashMap<String, Long>()
        try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager ?: return out
            val now = System.currentTimeMillis()
            val list = usm.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_MONTHLY,
                now - 30L * 86_400_000L, now) ?: return out
            for (s in list) {
                if (s == null || s.packageName == null) continue
                val t = s.lastTimeUsed
                val prev = out[s.packageName]
                if (prev == null || t > prev) out[s.packageName] = t
            }
        } catch (ignored: Throwable) {
        }
        return out
    }
}
