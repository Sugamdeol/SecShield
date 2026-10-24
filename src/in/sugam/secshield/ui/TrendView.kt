package `in`.sugam.secshield.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import `in`.sugam.secshield.R
import `in`.sugam.secshield.data.Store

/**
 * Score-over-time bars for the History tab, drawn from real history data.
 * Records arrive newest-first from the Store; they are drawn oldest to
 * newest, left to right. Nothing here is a forecast or a target, just the
 * recorded scores. Needs at least 2 records to draw anything meaningful;
 * with fewer, the History panel shows the list alone.
 */
class TrendView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private var records: List<Store.ScoreRecord> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        barPaint.style = Paint.Style.FILL
        gridPaint.style = Paint.Style.STROKE
        gridPaint.strokeWidth = 1f
        labelPaint.textSize = 12f * ctx.resources.displayMetrics.scaledDensity
        labelPaint.typeface = Neo.type(ctx, true)
    }

    fun setRecords(newestFirst: List<Store.ScoreRecord>) {
        records = newestFirst.reversed()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (records.size < 2) return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val pad = Neo.dp(context, 8f).toFloat()
        val labelH = Neo.dp(context, 20f).toFloat()
        val plotH = h - labelH - pad
        val plotTop = pad

        val gridColor = Neo.color(context, R.color.border)
        val barColor = Neo.color(context, R.color.accent)
        val textColor = Neo.color(context, R.color.text_muted)
        gridPaint.color = gridColor
        barPaint.color = barColor
        labelPaint.color = textColor

        // Gridlines at 0, 50, 100.
        for (level in listOf(0, 50, 100)) {
            val y = plotTop + plotH * (1f - level / 100f)
            canvas.drawLine(pad, y, w - pad, y, gridPaint)
        }

        val n = records.size
        val slot = (w - pad * 2) / n
        val barW = (slot * 0.55f).coerceAtLeast(4f)
        for ((i, rec) in records.withIndex()) {
            val cx = pad + slot * i + slot / 2f
            val barH = plotH * (rec.score.coerceIn(0, 100) / 100f)
            val top = plotTop + plotH - barH
            canvas.drawRoundRect(
                cx - barW / 2f, top, cx + barW / 2f, plotTop + plotH,
                barW / 2f, barW / 2f, barPaint
            )
            // Only the first and last scores are labelled, to avoid clutter.
            if (i == 0 || i == n - 1) {
                val label = rec.score.toString()
                val tw = labelPaint.measureText(label)
                canvas.drawText(label, cx - tw / 2f, h - Neo.dp(context, 4f), labelPaint)
            }
        }
    }
}
