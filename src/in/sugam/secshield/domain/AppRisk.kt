package `in`.sugam.secshield.domain

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.text.TextUtils
import `in`.sugam.secshield.data.CapabilityProbe
import `in`.sugam.secshield.data.DeviceProbe

/**
 * Scores a single installed app by the capabilities it actually holds on this device.
 * Same model as v1: what the app declares, what the user granted, what it can therefore do.
 */
class AppRisk {
    var pkg: String = ""
    var label: String = ""
    var versionName: String? = null
    var installSource: String? = null
    var systemApp: Boolean = false
    var fromPlayStore: Boolean = false
    var debuggable: Boolean = false
    var firstInstallTime: Long = 0
    var lastUpdateTime: Long = 0
    var lastUsedTime: Long = 0

    var accessibilityEnabled: Boolean = false
    var declaresAccessibility: Boolean = false
    var notificationListenerEnabled: Boolean = false
    var declaresNotificationListener: Boolean = false
    var overlayAllowed: Boolean = false
    var canInstallPackages: Boolean = false
    var isDeviceAdmin: Boolean = false
    var usageAccess: Boolean = false

    val grantedDangerous = ArrayList<String>()
    val declaredPermissions = ArrayList<String>()
    val reasons = ArrayList<String>()

    var score: Int = 0
    var severity: Int = Severity.OK
    /** Offline known-bad list match, or null. A list match, never a verdict. */
    var knownBad: KnownBad.Entry? = null

    /** Dangerous permission combinations, from granted permissions only. */
    fun privacyCombos(): List<String> {
        val out = ArrayList<String>()
        val cam = grantedDangerous.contains(Manifest.permission.CAMERA)
        val mic = grantedDangerous.contains(Manifest.permission.RECORD_AUDIO)
        val fine = grantedDangerous.contains(Manifest.permission.ACCESS_FINE_LOCATION)
        val bgLoc = grantedDangerous.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        val sms = grantedDangerous.contains(Manifest.permission.READ_SMS)
                || grantedDangerous.contains(Manifest.permission.RECEIVE_SMS)
        val contacts = grantedDangerous.contains(Manifest.permission.READ_CONTACTS)
        val calls = grantedDangerous.contains(Manifest.permission.READ_CALL_LOG)
        if (cam && mic && fine) {
            out.add("Camera, microphone and precise location together")
        } else if (cam && mic) {
            out.add("Camera and microphone together")
        }
        if (bgLoc && mic) out.add("Background location plus microphone")
        if (sms && contacts) out.add("Reads messages and contacts together")
        if (calls && mic) out.add("Call history plus microphone")
        return out
    }

    /** One line summary used in the app list. */
    fun shortReason(): String = if (reasons.isEmpty()) "No elevated capabilities found" else reasons[0]

    fun installSourceLabel(): String {
        if (systemApp) return "System app"
        if (installSource == null) return "Unknown source"
        if (fromPlayStore) return "Play Store"
        return installSource!!
    }

    fun pkgLabel(): String {
        return if (TextUtils.isEmpty(versionName)) pkg else "$pkg · v$versionName"
    }

    private fun scoreRisk() {
        var s = 0
        if (knownBad != null) {
            s += 40
            reasons.add("Matches a public offline list of reported-bad packages (" +
                knownBad!!.family + ", " + knownBad!!.source +
                ") — a list match, not a confirmed-malware verdict")
        }
        if (accessibilityEnabled) {
            s += 40; reasons.add("Accessibility service is enabled")
        } else if (declaresAccessibility) {
            s += 6; reasons.add("Declares an accessibility service")
        }
        if (overlayAllowed) { s += 20; reasons.add("Can draw over other apps") }
        if (notificationListenerEnabled) {
            s += 18; reasons.add("Notification access is allowed")
        } else if (declaresNotificationListener) {
            s += 6; reasons.add("Declares a notification listener")
        }
        if (isDeviceAdmin) { s += 15; reasons.add("Holds device administrator rights") }
        if (canInstallPackages) { s += 12; reasons.add("Can install other apps") }
        if (grantedDangerous.contains(Manifest.permission.READ_SMS)
                || grantedDangerous.contains(Manifest.permission.RECEIVE_SMS)) {
            s += 14; reasons.add("Reads or receives SMS")
        }
        if (grantedDangerous.contains(Manifest.permission.RECORD_AUDIO)) {
            s += 8; reasons.add("Records audio")
        }
        if (grantedDangerous.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            s += 8; reasons.add("Tracks location in the background")
        }
        if (grantedDangerous.contains(Manifest.permission.READ_CONTACTS)) {
            s += 6; reasons.add("Reads contacts")
        }
        if (grantedDangerous.contains(Manifest.permission.CAMERA)) {
            s += 6; reasons.add("Uses the camera")
        }
        if (!fromPlayStore && !systemApp) {
            s += 10; reasons.add("Installed from outside the Play Store")
        }
        if (debuggable) { s += 10; reasons.add("Shipped as a debuggable build") }
        if (usageAccess) { s += 8; reasons.add("Can see which other apps you use") }

        // Combination bonus: the shape Android banking trojans rely on.
        if (accessibilityEnabled && overlayAllowed) {
            s += 22; reasons.add("Accessibility plus overlay is the core remote control pattern")
        }
        if (accessibilityEnabled && notificationListenerEnabled) {
            s += 12; reasons.add("Accessibility plus notification access can capture one time codes")
        }

        if (s > 100) s = 100
        score = s
        severity = when {
            s >= 70 -> Severity.CRITICAL
            s >= 45 -> Severity.HIGH
            s >= 25 -> Severity.MEDIUM
            s > 0 -> Severity.LOW
            else -> Severity.OK
        }
    }

    companion object {
        val DANGEROUS = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.GET_ACCOUNTS
        )

        fun analyze(
            ctx: Context, pkg: String,
            accessibilityEnabled: Set<String>, notificationListeners: Set<String>,
            overlay: Set<String>, deviceAdmins: Set<String>, installers: Set<String>,
            usage: Map<String, Long>?
        ): AppRisk? {
            val r = AppRisk()
            r.pkg = pkg
            val pm = ctx.packageManager
            val pi = try {
                pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
            } catch (t: Throwable) {
                return null
            }
            val ai = pi.applicationInfo ?: return null
            r.label = pm.getApplicationLabel(ai).toString()
            r.versionName = pi.versionName
            r.systemApp = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    || (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            r.debuggable = (ai.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            r.firstInstallTime = pi.firstInstallTime
            r.lastUpdateTime = pi.lastUpdateTime
            r.installSource = CapabilityProbe.installSource(ctx, pkg)
            r.fromPlayStore = CapabilityProbe.isFromPlayStore(ctx, pkg)

            if (pi.requestedPermissions != null) {
                for (p in pi.requestedPermissions) {
                    if (p == null) continue
                    r.declaredPermissions.add(p)
                    if (DANGEROUS.contains(p)) {
                        try {
                            if (pm.checkPermission(p, pkg) == PackageManager.PERMISSION_GRANTED) {
                                r.grantedDangerous.add(p)
                            }
                        } catch (ignored: Throwable) {
                        }
                    }
                }
            }

            r.accessibilityEnabled = accessibilityEnabled.contains(pkg)
            r.declaresAccessibility = CapabilityProbe.declaresAccessibilityService(ctx, pkg)
            r.notificationListenerEnabled = notificationListeners.contains(pkg)
            r.declaresNotificationListener = CapabilityProbe.declaresNotificationListener(ctx, pkg)
            r.overlayAllowed = overlay.contains(pkg)
            r.canInstallPackages = installers.contains(pkg)
            r.isDeviceAdmin = deviceAdmins.contains(pkg)
            r.usageAccess = CapabilityProbe.appHasUsageAccess(ctx, pkg)
            if (usage != null) r.lastUsedTime = usage[pkg] ?: 0L
            r.knownBad = KnownBad.lookup(pkg)

            r.scoreRisk()
            return r
        }

        fun scanAll(ctx: Context): List<AppRisk> {
            val out = ArrayList<AppRisk>()
            val acc = CapabilityProbe.accessibilityServicePackages(ctx).toSet()
            val notif = CapabilityProbe.notificationListenerPackages(ctx).toSet()
            val overlay = CapabilityProbe.overlayPackages(ctx).toSet()
            val admins = DeviceProbe.thirdPartyDeviceAdmins(ctx).toSet()
            val installers = CapabilityProbe.installerPackages(ctx).toSet()
            val usage = CapabilityProbe.lastUsedMap(ctx)

            val pm = ctx.packageManager
            val pkgs = try {
                pm.getInstalledPackages(0)
            } catch (t: Throwable) {
                return out
            }
            for (pi in pkgs) {
                val ai = pi.applicationInfo ?: continue
                if (ai.packageName == ctx.packageName) continue
                if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
                if ((ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) continue
                val r = analyze(ctx, ai.packageName, acc, notif, overlay, admins, installers, usage)
                if (r != null) out.add(r)
            }
            out.sortByDescending { it.score }
            return out
        }

        fun prettyTime(ts: Long): String {
            if (ts <= 0) return "never"
            val f = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
            return f.format(java.util.Date(ts))
        }
    }
}
