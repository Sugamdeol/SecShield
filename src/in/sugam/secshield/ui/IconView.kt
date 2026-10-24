package `in`.sugam.secshield.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import `in`.sugam.secshield.R

/**
 * Clean line icons drawn in code. No emoji, no icon font, one stroke family:
 * 2dp ink strokes with round caps and joins. The shield is the one filled
 * brand mark (accent fill, ink check); every other icon is an outline so the
 * chrome stays quiet and the content carries the colour.
 */
class IconView(ctx: Context, private val kind: Int) : View(ctx) {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeJoin = Paint.Join.ROUND
        strokePaint.color = Neo.color(ctx, R.color.text_primary)
        fillPaint.style = Paint.Style.FILL
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        strokePaint.strokeWidth = dp(2f)
        when (kind) {
            SHIELD -> {
                fillPaint.color = Neo.color(context, R.color.accent)
                val p = Path()
                p.moveTo(w * 0.5f, h * 0.08f)
                p.lineTo(w * 0.88f, h * 0.22f)
                p.lineTo(w * 0.88f, h * 0.52f)
                p.quadTo(w * 0.88f, h * 0.82f, w * 0.5f, h * 0.94f)
                p.quadTo(w * 0.12f, h * 0.82f, w * 0.12f, h * 0.52f)
                p.lineTo(w * 0.12f, h * 0.22f)
                p.close()
                canvas.drawPath(p, fillPaint)
                canvas.drawPath(p, strokePaint)
                // Check mark
                val c = Path()
                c.moveTo(w * 0.30f, h * 0.50f)
                c.lineTo(w * 0.45f, h * 0.65f)
                c.lineTo(w * 0.72f, h * 0.35f)
                canvas.drawPath(c, strokePaint)
            }
            APPS -> {
                val s = w * 0.34f
                val g = w * 0.13f
                val r = dp(3f)
                val boxes = arrayOf(
                    floatArrayOf(g, g), floatArrayOf(w - g - s, g),
                    floatArrayOf(g, h - g - s), floatArrayOf(w - g - s, h - g - s)
                )
                for (b in boxes) {
                    canvas.drawRoundRect(RectF(b[0], b[1], b[0] + s, b[1] + s), r, r, strokePaint)
                }
            }
            CLOCK -> {
                canvas.drawCircle(w / 2, h / 2, w * 0.38f, strokePaint)
                canvas.drawLine(w / 2, h / 2, w / 2, h * 0.30f, strokePaint)
                canvas.drawLine(w / 2, h / 2, w * 0.66f, h * 0.55f, strokePaint)
            }
            GEAR -> {
                val cx = w / 2
                val cy = h / 2
                val r = w * 0.28f
                canvas.drawCircle(cx, cy, r, strokePaint)
                for (i in 0 until 8) {
                    val a = (i * Math.PI / 4).toFloat()
                    val x1 = cx + kotlin.math.cos(a) * r
                    val y1 = cy + kotlin.math.sin(a) * r
                    val x2 = cx + kotlin.math.cos(a) * (r + dp(4f))
                    val y2 = cy + kotlin.math.sin(a) * (r + dp(4f))
                    canvas.drawLine(x1, y1, x2, y2, strokePaint)
                }
                canvas.drawCircle(cx, cy, r * 0.35f, strokePaint)
            }
            WARN -> {
                fillPaint.color = Neo.color(context, R.color.flat_yellow)
                val p = Path()
                p.moveTo(w * 0.5f, h * 0.10f)
                p.lineTo(w * 0.90f, h * 0.88f)
                p.lineTo(w * 0.10f, h * 0.88f)
                p.close()
                canvas.drawPath(p, fillPaint)
                canvas.drawPath(p, strokePaint)
                canvas.drawLine(w * 0.5f, h * 0.38f, w * 0.5f, h * 0.62f, strokePaint)
                canvas.drawPoint(w * 0.5f, h * 0.74f, strokePaint)
            }
        }
    }

    companion object {
        const val SHIELD = 0
        const val APPS = 1
        const val CLOCK = 2
        const val GEAR = 3
        const val WARN = 4
    }
}
