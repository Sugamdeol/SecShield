package `in`.sugam.secshield.domain

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import `in`.sugam.secshield.data.CapabilityProbe
import `in`.sugam.secshield.data.DeviceProbe
import `in`.sugam.secshield.data.WifiProbe

/**
 * Runs the device posture checks and produces the score.
 * 22 checks: the v1.4 set plus the v2.4 offline known-bad list check.
 * Every check states what it looked at, so the screen never shows a verdict without a reason.
 */
class ScoreEngine {
    val checks = ArrayList<Check>()
    var score: Int = 100
    var criticalCount: Int = 0
    var highCount: Int = 0
    var mediumCount: Int = 0
    var lowCount: Int = 0
    var passCount: Int = 0
    var totalCount: Int = 0
    var scannedAt: Long = 0

    fun run(ctx: Context) {
        checks.clear()
        criticalCount = 0; highCount = 0; mediumCount = 0; lowCount = 0; passCount = 0

        val lock = DeviceProbe.lockState(ctx)
        if (lock == DeviceProbe.LOCK_NONE) {
            add(Check("lock", "No screen lock is set",
                    "Without a PIN, pattern or biometric, anyone holding the phone can open everything on it.",
                    Severity.CRITICAL, FIX_LOCK, "Set up"))
        } else {
            add(Check("lock", "Screen lock is on",
                    if (lock == DeviceProbe.LOCK_BIOMETRIC) "Biometric unlock is enrolled" else "A PIN or pattern is set",
                    Severity.OK, null, null))
        }

        if (DeviceProbe.hasEnrolledBiometrics(ctx)) {
            add(Check("biometric", "Biometric unlock is enrolled",
                    "A fingerprint or face unlock is registered on this device.",
                    Severity.OK, null, null))
        } else {
            add(Check("biometric", "No biometric unlock enrolled",
                    "Biometric unlock is faster than typing a PIN and harder to shoulder surf.",
                    Severity.LOW, FIX_BIOMETRIC, "Set up"))
        }

        val timeout = DeviceProbe.lockTimeoutSeconds(ctx)
        if (timeout <= 0) {
            add(Check("timeout", "Screen timeout could not be read",
                    "Android did not report the auto-lock delay.",
                    Severity.LOW, FIX_TIMEOUT, "Open"))
        } else if (timeout > 60) {
            add(Check("timeout", "Auto-lock waits " + (if (timeout >= 60) (timeout / 60).toString() + " minute" + (if (timeout >= 120) "s" else "") else "$timeout seconds"),
                    "A 30 second timeout is the usual recommendation for a personal phone.",
                    Severity.HIGH, FIX_TIMEOUT, "Open"))
        } else {
            add(Check("timeout", "Auto-lock is under a minute",
                    "The screen locks after $timeout seconds.",
                    Severity.OK, null, null))
        }

        if (DeviceProbe.developerOptionsEnabled(ctx)) {
            add(Check("devoptions", "Developer options are enabled",
                    "This is the master switch for ADB, debugging and unlocking tools.",
                    Severity.HIGH, FIX_DEV_OPTIONS, "Open"))
        } else {
            add(Check("devoptions", "Developer options are off",
                    "ADB and the engineering menus are locked down.",
                    Severity.OK, null, null))
        }

        if (DeviceProbe.usbDebuggingEnabled(ctx)) {
            add(Check("usbdebug", "USB debugging is enabled",
                    "A computer you have authorised can install apps and read data on this phone.",
                    Severity.CRITICAL, FIX_USB_DEBUG, "Turn off"))
        } else {
            add(Check("usbdebug", "USB debugging is off",
                    "No authorised computer can drive this device over USB.",
                    Severity.OK, null, null))
        }

        if (DeviceProbe.isDeviceEncrypted(ctx)) {
            add(Check("encryption", "Storage is encrypted",
                    "Files on this device are unreadable without your lock credential.",
                    Severity.OK, null, null))
        } else {
            add(Check("encryption", "Storage encryption is off",
                    "Files can be read off the device without the lock credential.",
                    Severity.CRITICAL, FIX_ENCRYPTION, "Open"))
        }

        val vbs = DeviceProbe.verifiedBootState()
        if (vbs == null) {
            add(Check("verifiedboot", "Verified boot state is unknown",
                    "This device does not expose the verified boot property to apps.",
                    Severity.LOW, null, null))
        } else if (vbs == "green") {
            add(Check("verifiedboot", "Verified boot is green",
                    "The running system matches the signed image.",
                    Severity.OK, null, null))
        } else {
            add(Check("verifiedboot", "Verified boot is $vbs",
                    "The running system does not fully match the signed image.",
                    Severity.CRITICAL, null, null))
        }

        if (DeviceProbe.selinuxEnforcing()) {
            add(Check("selinux", "SELinux is enforcing",
                    "Android's mandatory access control is active.",
                    Severity.OK, null, null))
        } else {
            add(Check("selinux", "SELinux is not enforcing",
                    "Android's mandatory access control is relaxed.",
                    Severity.HIGH, null, null))
        }

        val rootHits = DeviceProbe.rootIndicators()
        if (rootHits.isEmpty()) {
            add(Check("root", "No root indicators found",
                    "No su binary or known root framework path was found.",
                    Severity.OK, null, null))
        } else {
            add(Check("root", "Root indicators found",
                    rootHits[0] + (if (rootHits.size > 1) " and " + (rootHits.size - 1) + " more" else ""),
                    Severity.CRITICAL, FIX_ROOT, "Details"))
        }

        if (DeviceProbe.playProtectEnabled(ctx)) {
            add(Check("playprotect", "Play Protect is on",
                    "Google still verifies apps you install.",
                    Severity.OK, null, null))
        } else {
            add(Check("playprotect", "Play Protect is off",
                    "Apps you install are no longer scanned by Google.",
                    Severity.HIGH, FIX_PLAY_PROTECT, "Open"))
        }

        if (DeviceProbe.playProtectVerifyOff(ctx)) {
            add(Check("adbverify", "Install verification is relaxed",
                    "Android is not verifying apps installed over ADB.",
                    Severity.MEDIUM, FIX_PLAY_PROTECT, "Open"))
        } else {
            add(Check("adbverify", "Install verification is on",
                    "Android verifies apps installed over ADB.",
                    Severity.OK, null, null))
        }

        val acc = filterThirdParty(ctx, CapabilityProbe.accessibilityServicePackages(ctx))
        if (acc.isEmpty()) {
            add(Check("accessibility", "No third party accessibility services",
                    "No other app can read your screen or act inside it.",
                    Severity.OK, null, null))
        } else {
            add(Check("accessibility", "${acc.size} third party accessibility service" + (if (acc.size == 1) "" else "s") + " enabled",
                    "Accessibility access lets an app read your screen and tap on your behalf. " + acc[0],
                    Severity.CRITICAL, FIX_ACCESSIBILITY, "Open"))
        }

        val notif = filterThirdParty(ctx, CapabilityProbe.notificationListenerPackages(ctx))
        if (notif.isEmpty()) {
            add(Check("notiflistener", "No third party notification listeners",
                    "No other app can read your notification content.",
                    Severity.OK, null, null))
        } else {
            add(Check("notiflistener", "${notif.size} third party notification listener" + (if (notif.size == 1) "" else "s") + " enabled",
                    "Notification access can expose one time codes and message previews. " + notif[0],
                    Severity.HIGH, FIX_NOTIF_LISTENER, "Open"))
        }

        val overlay = filterThirdParty(ctx, CapabilityProbe.overlayPackages(ctx))
        if (overlay.isEmpty()) {
            add(Check("overlay", "No third party overlay access",
                    "No other app can draw on top of your other apps.",
                    Severity.OK, null, null))
        } else {
            add(Check("overlay", "${overlay.size} app" + (if (overlay.size == 1) "" else "s") + " can draw over other apps",
                    "Overlay access enables tapjacking. " + overlay[0],
                    Severity.MEDIUM, FIX_OVERLAY, "Open"))
        }

        val admins = DeviceProbe.thirdPartyDeviceAdmins(ctx)
        if (admins.isEmpty()) {
            add(Check("deviceadmin", "No third party device administrators",
                    "No other app can lock, wipe or change your password policy.",
                    Severity.OK, null, null))
        } else {
            add(Check("deviceadmin", "${admins.size} third party device administrator" + (if (admins.size == 1) "" else "s"),
                    "Device admin rights allow locking or wiping the phone. " + admins[0],
                    Severity.HIGH, FIX_ADMIN, "Open"))
        }

        val installers = CapabilityProbe.installerPackages(ctx)
        if (installers.isEmpty()) {
            add(Check("unknownsources", "No app can install other apps",
                    "Nothing on this phone is allowed to install new packages.",
                    Severity.OK, null, null))
        } else {
            add(Check("unknownsources", "${installers.size} app" + (if (installers.size == 1) "" else "s") + " can install other apps",
                    "Sideloading through those apps bypasses the store's checks. " + installers[0],
                    Severity.MEDIUM, FIX_UNKNOWN_SOURCES, "Open"))
        }

        if (DeviceProbe.isBatteryUnrestricted(ctx)) {
            add(Check("battery", "SecShield is excluded from battery optimisation",
                    "The install monitor keeps running in the background.",
                    Severity.OK, null, null))
        } else {
            add(Check("battery", "SecShield may be killed in the background",
                    "Allow background activity so the install monitor keeps working.",
                    Severity.MEDIUM, FIX_BATTERY, "Open"))
        }

        val patchAge = securityPatchAgeDays(DeviceProbe.securityPatch())
        if (patchAge < 0) {
            add(Check("patch", "Security patch level is unknown",
                    "Android did not report a patch date for this device.",
                    Severity.LOW, null, null))
        } else if (patchAge > 180) {
            add(Check("patch", "Security patch is over 6 months old",
                    "Patch level " + DeviceProbe.securityPatch() + ". Known exploits stay open until the vendor ships an update.",
                    Severity.HIGH, FIX_SECURITY, "Open"))
        } else if (patchAge > 90) {
            add(Check("patch", "Security patch is over 3 months old",
                    "Patch level " + DeviceProbe.securityPatch() + ". Check for a system update.",
                    Severity.MEDIUM, FIX_SECURITY, "Open"))
        } else {
            add(Check("patch", "Security patch is current",
                    "Patch level " + DeviceProbe.securityPatch() + ".",
                    Severity.OK, null, null))
        }

        val wifi = WifiProbe.check(ctx)
        val wifiName = wifi.ssid ?: "this network"
        when (wifi.state) {
            WifiProbe.OFF -> add(Check("wifi", "Wi-Fi is off",
                    "No wireless network is in range of attack.",
                    Severity.OK, null, null))
            WifiProbe.OPEN -> add(Check("wifi", "Connected to an open Wi-Fi network",
                    "Anyone nearby can read unencrypted traffic on $wifiName.",
                    Severity.HIGH, FIX_WIFI, "Open"))
            WifiProbe.WEP -> add(Check("wifi", "Wi-Fi uses WEP encryption",
                    "WEP is broken in minutes. $wifiName should move to WPA2 or WPA3.",
                    Severity.HIGH, FIX_WIFI, "Open"))
            WifiProbe.WPA -> add(Check("wifi", "Wi-Fi uses modern encryption",
                    "Connected to $wifiName with WPA2 or better.",
                    Severity.OK, null, null))
            else -> add(Check("wifi", "Wi-Fi encryption could not be verified",
                    "Android did not report the current network's security type, so nothing is assumed.",
                    Severity.LOW, FIX_WIFI, "Open"))
        }

        if (WifiProbe.vpnActive(ctx)) {
            add(Check("vpn", "A VPN is active",
                    "Traffic is routed through an encrypted tunnel right now.",
                    Severity.OK, null, null))
        } else {
            add(Check("vpn", "No VPN active",
                    "Fine on networks you trust. Use a VPN on public Wi-Fi.",
                    Severity.OK, null, null))
        }

        var sideloaded = 0
        var firstSideloaded: String? = null
        try {
            val pm = ctx.packageManager
            val all = pm.getInstalledPackages(0)
            for (pi in all) {
                val ai = pi.applicationInfo ?: continue
                if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
                if ((ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) continue
                if (ai.packageName == ctx.packageName) continue
                if (!CapabilityProbe.isFromPlayStore(ctx, ai.packageName)) {
                    sideloaded++
                    if (firstSideloaded == null) firstSideloaded = ai.packageName
                }
            }
        } catch (ignored: Throwable) {
        }
        if (sideloaded == 0) {
            add(Check("sideloaded", "Every app came from the Play Store",
                    "No user app was installed from an APK file or another store.",
                    Severity.OK, null, null))
        } else {
            add(Check("sideloaded", "$sideloaded app" + (if (sideloaded == 1) " was" else "s were") + " not installed by the Play Store",
                    "Sideloaded apps skip the store's review. First found: $firstSideloaded",
                    Severity.MEDIUM, FIX_UNKNOWN_SOURCES, "Open"))
        }

        // v2.4: offline known-bad list match. A list match, not a
        // confirmed-malware verdict, so it scores HIGH, never CRITICAL.
        val badMatches = ArrayList<String>()
        try {
            val pm = ctx.packageManager
            val all = pm.getInstalledPackages(0)
            for (pi in all) {
                val ai = pi.applicationInfo ?: continue
                if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
                if ((ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) continue
                if (ai.packageName == ctx.packageName) continue
                val entry = KnownBad.lookup(ai.packageName)
                if (entry != null) badMatches.add(entry.displayName)
            }
        } catch (ignored: Throwable) {
        }
        if (badMatches.isEmpty()) {
            add(Check("knownbad", "No reported-bad apps installed",
                    "No installed package matches the bundled offline list of publicly reported Joker and SpyLoan packages.",
                    Severity.OK, null, null))
        } else {
            add(Check("knownbad", badMatches.size.toString() + " app" +
                    (if (badMatches.size == 1) "" else "s") + " match a public reported-bad list",
                    "Matched: " + badMatches.joinToString(", ") +
                    ". This is a list match, not a confirmed-malware verdict. Open the app in the Apps tab to uninstall or mark it trusted.",
                    Severity.HIGH, null, null))
        }

        totalCount = checks.size
        score = 100
        for (c in checks) score -= Severity.weight(c.severity)
        if (score < 0) score = 0
        scannedAt = System.currentTimeMillis()
    }

    /** Days since the security patch date ("yyyy-MM-dd"), or -1 when unparseable. */
    private fun securityPatchAgeDays(patch: String?): Long {
        if (patch == null || patch.length < 10 || patch == "unknown") return -1
        try {
            val f = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            f.isLenient = false
            val d = f.parse(patch.substring(0, 10)) ?: return -1
            val diff = System.currentTimeMillis() - d.time
            return diff / 86_400_000L
        } catch (t: Throwable) {
            return -1
        }
    }

    private fun add(c: Check) {
        checks.add(c)
        when (c.severity) {
            Severity.CRITICAL -> criticalCount++
            Severity.HIGH -> highCount++
            Severity.MEDIUM -> mediumCount++
            Severity.LOW -> lowCount++
            else -> passCount++
        }
    }

    /** Drops system apps so the counts only describe apps the user installed. */
    private fun filterThirdParty(ctx: Context, pkgs: List<String>): List<String> {
        val out = ArrayList<String>()
        for (p in pkgs) {
            if (p == ctx.packageName) continue
            if (DeviceProbe.isSystemPackage(ctx, p)) continue
            if (!out.contains(p)) out.add(p)
        }
        return out
    }

    /** Immutable snapshot of the last run, for the UI to render from. */
    fun snapshot(): ScanSnapshot {
        return ScanSnapshot(
            score = score,
            band = bandLabel(),
            critical = criticalCount,
            high = highCount,
            medium = mediumCount,
            low = lowCount,
            passing = passCount,
            total = checks.size,
            checks = ArrayList(checks),
            scannedAt = scannedAt
        )
    }

    /**
     * Builds the intent that opens the exact settings screen for a fix action.
     * pkg may be null; when present the intent lands on that app's page.
     */
    fun bandLabel(): String {
        return when {
            criticalCount > 0 -> "At risk"
            highCount > 0 -> "Needs work"
            mediumCount > 0 || lowCount > 0 -> "Mostly protected"
            else -> "Protected"
        }
    }

    companion object {
        const val FIX_SECURITY = "security"
        const val FIX_LOCK = "lock"
        const val FIX_TIMEOUT = "timeout"
        const val FIX_DEV_OPTIONS = "devoptions"
        const val FIX_USB_DEBUG = "usbdebug"
        const val FIX_ACCESSIBILITY = "accessibility"
        const val FIX_NOTIF_LISTENER = "notiflistener"
        const val FIX_OVERLAY = "overlay"
        const val FIX_APP_SETTINGS = "appsettings"
        const val FIX_ADMIN = "deviceadmin"
        const val FIX_BATTERY = "battery"
        const val FIX_PLAY_PROTECT = "playprotect"
        const val FIX_UNKNOWN_SOURCES = "unknownsources"
        const val FIX_ROOT = "root"
        const val FIX_VERIFIED_BOOT = "verifiedboot"
        const val FIX_ENCRYPTION = "encryption"
        const val FIX_BIOMETRIC = "biometric"
        const val FIX_WIFI = "wifi"

        fun fixIntent(action: String?, pkg: String?): Intent {
            val i: Intent = when (action) {
                FIX_ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                FIX_NOTIF_LISTENER -> Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                FIX_OVERLAY -> {
                    val x = Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION")
                    if (pkg != null) x.data = Uri.parse("package:$pkg")
                    x
                }
                FIX_UNKNOWN_SOURCES -> {
                    if (pkg != null && Build.VERSION.SDK_INT >= 26) {
                        Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:$pkg"))
                    } else {
                        Intent(Settings.ACTION_SECURITY_SETTINGS)
                    }
                }
                FIX_ADMIN -> Intent(Settings.ACTION_SECURITY_SETTINGS)
                FIX_BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                FIX_DEV_OPTIONS -> Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                FIX_USB_DEBUG -> Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                FIX_PLAY_PROTECT -> Intent(Settings.ACTION_SECURITY_SETTINGS)
                FIX_TIMEOUT -> Intent("android.settings.action.LOCK_SETTINGS")
                FIX_ENCRYPTION -> Intent(Settings.ACTION_SECURITY_SETTINGS)
                FIX_BIOMETRIC -> Intent(Settings.ACTION_SECURITY_SETTINGS)
                FIX_WIFI -> Intent(Settings.ACTION_WIFI_SETTINGS)
                FIX_ROOT -> Intent(Settings.ACTION_SECURITY_SETTINGS)
                else -> Intent(Settings.ACTION_SETTINGS)
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return i
        }

        fun fixIntent(action: String?): Intent = fixIntent(action, null)
    }
}
