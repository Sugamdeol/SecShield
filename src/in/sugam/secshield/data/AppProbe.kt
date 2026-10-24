package `in`.sugam.secshield.data

import android.content.Context
import `in`.sugam.secshield.domain.AppRisk

/** Holds the result of the last app scan so the UI does not rescan on every frame. */
class AppProbe(ctx: Context) {

    val allApps = ArrayList<AppRisk>()
    var thirdPartyCount: Int = 0
    var lastAlertAt: Long = 0

    init {
        lastAlertAt = ctx.getSharedPreferences("secshield", Context.MODE_PRIVATE)
                .getLong("last_alert", 0L)
    }

    /** Rescans every user installed app. Runs off the UI thread when called from a background task. */
    fun scan(ctx: Context) {
        val result = AppRisk.scanAll(ctx)
        allApps.clear()
        allApps.addAll(result)
        thirdPartyCount = allApps.size
    }
}
