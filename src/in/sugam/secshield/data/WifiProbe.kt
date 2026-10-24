package `in`.sugam.secshield.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build

/**
 * Wi-Fi audit without root and without location permission. Everything here is
 * best effort: when Android will not report the current network's security
 * type, the result says so instead of guessing.
 */
object WifiProbe {

    const val OFF = 0
    const val OPEN = 1
    const val WEP = 2
    const val WPA = 3
    const val UNKNOWN = 4

    data class Result(val state: Int, val ssid: String?)

    fun check(ctx: Context): Result {
        try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    ?: return Result(UNKNOWN, null)
            if (!wm.isWifiEnabled) return Result(OFF, null)
            val info = wm.connectionInfo
            if (info == null || info.networkId == -1) return Result(UNKNOWN, null)
            var ssid: String? = info.ssid
            if (ssid != null) {
                ssid = ssid.replace("\"", "")
                if (ssid == "<unknown ssid>") ssid = null
            }
            if (Build.VERSION.SDK_INT >= 31) {
                val sec = info.currentSecurityType
                if (sec == WifiInfo.SECURITY_TYPE_OPEN) return Result(OPEN, ssid)
                if (sec == WifiInfo.SECURITY_TYPE_WEP) return Result(WEP, ssid)
                if (sec == WifiInfo.SECURITY_TYPE_PSK || sec == WifiInfo.SECURITY_TYPE_SAE
                        || sec == WifiInfo.SECURITY_TYPE_EAP
                        || sec == WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE
                        || sec == WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT
                        || sec == WifiInfo.SECURITY_TYPE_OWE) {
                    return Result(WPA, ssid)
                }
                return Result(UNKNOWN, ssid)
            }
            return Result(UNKNOWN, ssid)
        } catch (t: Throwable) {
            return Result(UNKNOWN, null)
        }
    }

    /** True when traffic currently routes through a VPN transport. */
    fun vpnActive(ctx: Context): Boolean {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (t: Throwable) {
            return false
        }
    }
}
