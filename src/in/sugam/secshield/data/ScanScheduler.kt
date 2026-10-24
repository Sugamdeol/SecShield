package `in`.sugam.secshield.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** Daily automatic scan, fired at 09:00 local time. Re-armed on boot. */
object ScanScheduler {

    private const val REQUEST = 9001

    private fun intent(ctx: Context, flags: Int): PendingIntent {
        val i = Intent(ctx, ScanReceiver::class.java)
        var f = flags
        if (Build.VERSION.SDK_INT >= 23) f = f or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, REQUEST, i, f)
    }

    fun isScheduled(ctx: Context): Boolean {
        try {
            return PendingIntent.getBroadcast(ctx, REQUEST,
                    Intent(ctx, ScanReceiver::class.java),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) != null
        } catch (t: Throwable) {
            return false
        }
    }

    fun schedule(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val next = Calendar.getInstance()
            next.set(Calendar.HOUR_OF_DAY, 9)
            next.set(Calendar.MINUTE, 0)
            next.set(Calendar.SECOND, 0)
            next.set(Calendar.MILLISECOND, 0)
            if (next.timeInMillis <= System.currentTimeMillis()) {
                next.add(Calendar.DAY_OF_YEAR, 1)
            }
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.timeInMillis,
                    AlarmManager.INTERVAL_DAY, intent(ctx, PendingIntent.FLAG_UPDATE_CURRENT))
        } catch (ignored: Throwable) {
        }
    }

    fun cancel(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.cancel(intent(ctx, PendingIntent.FLAG_UPDATE_CURRENT))
            intent(ctx, PendingIntent.FLAG_UPDATE_CURRENT).cancel()
        } catch (ignored: Throwable) {
        }
    }
}
