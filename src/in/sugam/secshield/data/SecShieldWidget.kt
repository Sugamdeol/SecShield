package `in`.sugam.secshield.data

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import `in`.sugam.secshield.MainActivity
import `in`.sugam.secshield.R
import `in`.sugam.secshield.data.Store

/** Home screen widget: the live score and band. Tap opens SecShield. */
class SecShieldWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) {
        updateAll(ctx)
    }

    companion object {
        fun updateAll(ctx: Context) {
            try {
                val store = Store(ctx)
                val score = store.lastScore()
                val band = store.lastBand()
                val views = RemoteViews(ctx.packageName, R.layout.widget_score)
                if (score >= 0) {
                    views.setTextViewText(R.id.widget_score, "$score/100")
                    views.setTextViewText(R.id.widget_band, band)
                } else {
                    views.setTextViewText(R.id.widget_score, "—/100")
                    views.setTextViewText(R.id.widget_band, "Not scanned yet")
                }
                val i = Intent(ctx, MainActivity::class.java)
                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                var flags = PendingIntent.FLAG_UPDATE_CURRENT
                if (Build.VERSION.SDK_INT >= 23) flags = flags or PendingIntent.FLAG_IMMUTABLE
                val pi = PendingIntent.getActivity(ctx, 9, i, flags)
                views.setOnClickPendingIntent(R.id.widget_root, pi)
                val awm = AppWidgetManager.getInstance(ctx)
                awm.updateAppWidget(ComponentName(ctx, SecShieldWidget::class.java), views)
            } catch (ignored: Throwable) {
            }
        }
    }
}
