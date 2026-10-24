package `in`.sugam.secshield.data

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import `in`.sugam.secshield.admin.PolicyAdmin
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Reads device posture. Everything here is best effort: some values are unreadable
 * without privileged access, and those are reported as such rather than guessed.
 */
object DeviceProbe {

    const val LOCK_NONE = 0
    const val LOCK_PIN = 1
    const val LOCK_BIOMETRIC = 2

    /** Reads a system property. Hidden API, so reflection with a fallback to `getprop`. */
    fun systemProperty(key: String): String? {
        try {
            val cls = Class.forName("android.os.SystemProperties")
            val m = cls.getMethod("get", String::class.java)
            val v = m.invoke(null, key)
            if (v != null) return v.toString()
        } catch (ignored: Throwable) {
        }
        try {
            val p = ProcessBuilder("getprop", key).redirectErrorStream(true).start()
            val r = BufferedReader(InputStreamReader(p.inputStream))
            val line = r.readLine()
            r.close()
            p.waitFor()
            if (line != null) return line.trim()
        } catch (ignored: Throwable) {
        }
        return null
    }

    fun isDeviceEncrypted(ctx: Context): Boolean {
        try {
            return Build.VERSION.SDK_INT >= 24 && "1" == systemProperty("ro.crypto.state")
        } catch (t: Throwable) {
            return false
        }
    }

    /** LOCK_NONE, LOCK_PIN or LOCK_BIOMETRIC. */
    fun lockState(ctx: Context): Int {
        try {
            val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return LOCK_NONE
            if (Build.VERSION.SDK_INT >= 23 && km.isDeviceSecure) {
                if (hasEnrolledBiometrics(ctx)) return LOCK_BIOMETRIC
                return LOCK_PIN
            }
            return LOCK_NONE
        } catch (t: Throwable) {
            return LOCK_NONE
        }
    }

    fun hasEnrolledBiometrics(ctx: Context): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val bm = ctx.getSystemService(Context.BIOMETRIC_SERVICE) as? android.hardware.biometrics.BiometricManager
                if (bm != null) {
                    val r = bm.canAuthenticate(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    return r == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
                }
            } else if (Build.VERSION.SDK_INT == 29) {
                val bm = ctx.getSystemService(Context.BIOMETRIC_SERVICE) as? android.hardware.biometrics.BiometricManager
                if (bm != null) {
                    @Suppress("DEPRECATION")
                    return bm.canAuthenticate() == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
                }
            }
            @Suppress("DEPRECATION")
            val fm = ctx.getSystemService(Context.FINGERPRINT_SERVICE) as? FingerprintManager
            if (fm != null && fm.isHardwareDetected && fm.hasEnrolledFingerprints()) return true
            if (Build.VERSION.SDK_INT >= 30) {
                val bm = ctx.getSystemService(Context.BIOMETRIC_SERVICE) as? android.hardware.biometrics.BiometricManager
                if (bm != null) {
                    return bm.canAuthenticate(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                            android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
                }
            }
        } catch (ignored: Throwable) {
        }
        return false
    }

    /** Lock screen auto-lock timeout in seconds, 0 when unreadable. */
    fun lockTimeoutSeconds(ctx: Context): Int {
        try {
            val ms = Settings.Secure.getInt(ctx.contentResolver, "lock_screen_lock_after_timeout", -1)
            if (ms >= 0) return ms / 1000
        } catch (ignored: Throwable) {
        }
        try {
            return Settings.Secure.getInt(ctx.contentResolver, "lock_screen_timeout_seconds", 0)
        } catch (t: Throwable) {
            return 0
        }
    }

    fun developerOptionsEnabled(ctx: Context): Boolean {
        try {
            return Settings.Global.getInt(ctx.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        } catch (t: Throwable) {
            return false
        }
    }

    fun usbDebuggingEnabled(ctx: Context): Boolean {
        try {
            return Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (t: Throwable) {
            return false
        }
    }

    fun playProtectEnabled(ctx: Context): Boolean {
        try {
            val v = Settings.Global.getInt(ctx.contentResolver, "package_verifier_enable", 1)
            return v != 0
        } catch (t: Throwable) {
            return true
        }
    }

    fun playProtectVerifyOff(ctx: Context): Boolean {
        try {
            val v = Settings.Global.getInt(ctx.contentResolver, "verifier_verify_adb_installs", 1)
            return v == 0
        } catch (t: Throwable) {
            return false
        }
    }

    /** "green", "orange", "red", "yellow" or null when unknown. */
    fun verifiedBootState(): String? {
        val v = systemProperty("ro.boot.verifiedbootstate")
        if (v == null || TextUtils.isEmpty(v)) return null
        return v.lowercase()
    }

    fun selinuxEnforcing(): Boolean {
        val v = systemProperty("ro.build.selinux")
        if (v != null && v.equals("enforcing", ignoreCase = true)) return true
        try {
            val p = ProcessBuilder("getenforce").redirectErrorStream(true).start()
            val r = BufferedReader(InputStreamReader(p.inputStream))
            val line = r.readLine()
            r.close()
            p.waitFor()
            return line != null && line.trim().equals("Enforcing", ignoreCase = true)
        } catch (t: Throwable) {
            return false
        }
    }

    /** Root indicators. Non-empty list means something is wrong. */
    fun rootIndicators(): List<String> {
        val hits = ArrayList<String>()
        val paths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/system/sd/xbin/su",
            "/system/app/Superuser.apk", "/system/app/SuperSU", "/data/adb/magisk", "/data/adb/modules",
            "/system/framework/XposedBridge.jar", "/system/bin/.ext/.su", "/system/etc/init.d/00magisk"
        )
        for (p in paths) {
            try {
                if (File(p).exists()) hits.add(p)
            } catch (ignored: Throwable) {
            }
        }
        val which = systemProperty("ro.debuggable")
        if ("1" == which) hits.add("ro.debuggable=1")
        return hits
    }

    fun isBatteryUnrestricted(ctx: Context): Boolean {
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
            return pm.isIgnoringBatteryOptimizations(ctx.packageName)
        } catch (t: Throwable) {
            return false
        }
    }

    /** Third party apps holding device admin rights (SecShield itself excluded). */
    fun thirdPartyDeviceAdmins(ctx: Context): List<String> {
        val out = ArrayList<String>()
        try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return out
            val admins = dpm.activeAdmins ?: return out
            for (cn in admins) {
                val pkg = cn.packageName ?: continue
                if (pkg == ctx.packageName) continue
                out.add(pkg)
            }
        } catch (ignored: Throwable) {
        }
        return out
    }

    fun isDeviceAdminActive(ctx: Context): Boolean {
        try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
            return dpm.isAdminActive(ComponentName(ctx, PolicyAdmin::class.java))
        } catch (t: Throwable) {
            return false
        }
    }

    fun securityPatch(): String {
        val v = Build.VERSION.SECURITY_PATCH
        return if (v == null || v.isEmpty()) "unknown" else v
    }

    fun deviceLabel(ctx: Context): String {
        return (Build.MANUFACTURER + " " + Build.MODEL).trim()
    }

    fun isSystemPackage(ctx: Context, pkg: String): Boolean {
        try {
            val pm = ctx.packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            return (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (t: Throwable) {
            return false
        }
    }

    fun isExternalStorage(): Boolean {
        return Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()
    }
}
