package `in`.sugam.secshield.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import `in`.sugam.secshield.R

/**
 * Score ring, modern edition: a slim track with a slim progress arc and
 * round caps. Colour is set by the caller (the accent for progress, a soft
 * grey for the track). No text is drawn here; the number sits centred on
 * top of it from the layout.
 */
class RingView(ctx: Context) : View(ctx) {

    private var progress: Int = 0
    private var ringColor: Int = Neo.color(ctx, R.color.accent)
    private var trackColor: Int = Neo.color(ctx, R.color.surface_sunken)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcRect = RectF()

    init {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
    }

    fun setProgress(p: Int) {
        progress = p.coerceIn(0, 100)
        invalidate()
    }

    fun setRingColor(c: Int) {
        ringColor = c
        invalidate()
    }

    fun setTrackColor(c: Int) {
        trackColor = c
        invalidate()
    }

    private fun dp(v: Float): Float {
        return v * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        val stroke = dp(10f)
        paint.strokeWidth = stroke
        val inset = stroke / 2f + dp(2f)
        arcRect.set(inset, inset, width - inset, height - inset)
        paint.color = trackColor
        canvas.drawArc(arcRect, 0f, 360f, false, paint)
        if (progress > 0) {
            paint.color = ringColor
            canvas.drawArc(arcRect, -90f, progress * 3.6f, false, paint)
        }
    }
}
