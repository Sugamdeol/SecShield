package `in`.sugam.secshield.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import `in`.sugam.secshield.domain.AppRisk
import `in`.sugam.secshield.domain.Check
import `in`.sugam.secshield.domain.ScanSnapshot
import `in`.sugam.secshield.domain.Severity
import `in`.sugam.secshield.data.Store
import java.io.File
import java.io.FileOutputStream

/**
 * Structured PDF report built with the platform PdfDocument API only.
 * No network, no third party library. Written to the app cache dir.
 */
object ReportPdf {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 48

    private class Doc {
        val pdf = PdfDocument()
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y: Float = 0f
        val title = Paint()
        val head = Paint()
        val body = Paint()
        val small = Paint()

        init {
            title.textSize = 20f
            title.isFakeBoldText = true
            head.textSize = 13f
            head.isFakeBoldText = true
            body.textSize = 11f
            small.textSize = 9f
            newPage()
        }

        fun newPage() {
            if (page != null) pdf.finishPage(page)
            val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
            page = pdf.startPage(info)
            canvas = page!!.canvas
            y = MARGIN + 10f
        }

        fun need(h: Float) {
            if (y + h > PAGE_H - MARGIN) newPage()
        }

        fun line(s: String?, p: Paint) {
            need(16f)
            canvas!!.drawText(s ?: "", MARGIN.toFloat(), y, p)
            y += 16f
        }

        fun wrapped(s: String?, p: Paint) {
            if (s == null) return
            for (l in wrap(s, 92)) line(l, p)
        }

        fun gap(h: Float) {
            y += h
            if (y > PAGE_H - MARGIN) newPage()
        }

        fun finish(out: File) {
            pdf.finishPage(page)
            val fos = FileOutputStream(out)
            pdf.writeTo(fos)
            fos.close()
            pdf.close()
        }
    }

    private fun wrap(s: String, width: Int): List<String> {
        val out = ArrayList<String>()
        val words = s.split("\\s+".toRegex())
        val cur = StringBuilder()
        for (w in words) {
            if (cur.isNotEmpty() && cur.length + 1 + w.length > width) {
                out.add(cur.toString())
                cur.setLength(0)
            }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(w)
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        if (out.isEmpty()) out.add("")
        return out
    }

    private fun date(ts: Long): String {
        val f = java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault())
        return f.format(java.util.Date(ts))
    }

    /** Builds the report and returns the file, or null on failure. */
    fun build(ctx: Context, snap: ScanSnapshot, apps: List<AppRisk>): File? {
        try {
            val store = Store(ctx)
            val d = Doc()
            d.line("SecShield report", d.title)
            d.line("Generated on this phone by SecShield v2.4", d.small)
            d.gap(10f)
            d.line(DeviceProbe.deviceLabel(ctx) + " · Android " + Build.VERSION.RELEASE, d.body)
            d.line("Score: " + snap.score + "/100 (" + snap.band + ")", d.body)
            d.line(snap.passing.toString() + " of " + snap.total + " checks passed", d.body)
            d.line("Scanned " + date(snap.scannedAt), d.body)
            d.gap(14f)

            val history = store.history()
            if (history.isNotEmpty()) {
                d.line("Score history", d.head)
                for (rec in history.take(10)) {
                    d.line(date(rec.ts) + " · " + rec.score + "/100 (" + rec.band + ")", d.body)
                }
                d.gap(14f)
            }

            val failing = snap.failing
            if (failing.isNotEmpty()) {
                d.line("Findings", d.head)
                for (c in failing) {
                    d.line(Severity.label(c.severity) + " · " + c.title, d.body)
                    d.wrapped(c.detail, d.small)
                }
                d.gap(14f)
            } else {
                d.line("No failing checks. Everything passed.", d.body)
                d.gap(14f)
            }

            val flagged = apps.filter { it.severity >= Severity.MEDIUM }
            if (flagged.isNotEmpty()) {
                d.line("Flagged apps", d.head)
                for (r in flagged) {
                    d.line(r.label + " · Risk " + r.score + "/100", d.body)
                    d.wrapped(r.shortReason(), d.small)
                }
                d.gap(14f)
            }

            val combos = ArrayList<String>()
            for (r in apps) {
                for (combo in r.privacyCombos()) {
                    combos.add(r.label + ": " + combo)
                }
            }
            if (combos.isNotEmpty()) {
                d.line("Privacy combinations", d.head)
                for (c in combos) d.wrapped(c, d.body)
                d.gap(14f)
            }

            // v2.4 additions: known-bad matches, score trend, top risks.
            val bad = apps.filter { it.knownBad != null }
            if (bad.isNotEmpty()) {
                d.line("Reported-bad matches (list matches, not confirmed-malware verdicts)", d.head)
                for (r in bad) {
                    d.line(r.label + " (" + r.pkg + ") · " + r.knownBad!!.family +
                        ", " + r.knownBad!!.source, d.body)
                }
                d.gap(14f)
            }
            if (history.size >= 2) {
                val oldest = history.last()
                val latest = history.first()
                val best = history.maxOf { it.score }
                val worst = history.minOf { it.score }
                d.line("Score trend", d.head)
                d.line("From " + oldest.score + "/100 to " + latest.score + "/100 · Best " +
                    best + "/100 · Lowest " + worst + "/100 over " + history.size + " scans", d.body)
                d.gap(14f)
            }
            val topRisks = apps.filter { it.severity >= Severity.MEDIUM }
                .sortedByDescending { it.score }.take(3)
            if (topRisks.isNotEmpty()) {
                d.line("Top risks", d.head)
                for (r in topRisks) {
                    d.line(r.label + " · Risk " + r.score + "/100", d.body)
                    d.wrapped(r.shortReason(), d.small)
                }
            }

            val out = File(ctx.cacheDir, "secshield-report.pdf")
            d.finish(out)
            return out
        } catch (t: Throwable) {
            return null
        }
    }
}
