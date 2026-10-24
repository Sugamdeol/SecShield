package `in`.sugam.secshield.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import `in`.sugam.secshield.MainActivity

/** Local notifications. No network, no remote anything. */
object Notifier {

    const val CHANNEL = "secshield_alerts"
    const val ID_ALERT = 1001
    const val ID_VERDICT = 1002

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val ch = NotificationChannel(CHANNEL, "Security alerts", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "Alerts when a new app or a new risk is detected"
            ch.enableLights(true)
            nm.createNotificationChannel(ch)
        } catch (ignored: Throwable) {
        }
    }

    /** Install verdict: a quieter notification than a threat alert, its own id. */
    fun verdict(ctx: Context, title: String, text: String) {
        notify(ctx, ID_VERDICT, 8, title, text)
    }

    fun alert(ctx: Context, title: String, text: String) {
        notify(ctx, ID_ALERT, 7, title, text)
    }

    private fun notify(ctx: Context, id: Int, request: Int, title: String, text: String) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            ensureChannel(ctx)
            val i = Intent(ctx, MainActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= 23) flags = flags or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getActivity(ctx, request, i, flags)
            val b: Notification.Builder = if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(ctx, CHANNEL)
            } else {
                Notification.Builder(ctx)
            }
            b.setContentTitle(title)
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
            nm.notify(id, b.build())
        } catch (ignored: Throwable) {
        }
    }
}
