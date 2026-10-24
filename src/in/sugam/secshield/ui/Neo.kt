package `in`.sugam.secshield.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import `in`.sugam.secshield.R
import `in`.sugam.secshield.domain.Severity

/**
 * Stitch-aligned design system, drawn in code. One rule set, kept everywhere:
 * - Cards: 20dp corners, white surface, 2dp soft elevation, no borders,
 *   no offset shadows. Shape lock: cards 20, pills full, inputs 16.
 * - One locked accent: Stitch lime #70E000. Neutrals carry the chrome;
 *   colour appears only on data and status.
 * - Type: Inter (OFL, bundled in assets/fonts), 4 sizes (display 56 /
 *   title 24 / body 16 / caption 13), 2 weights. Figures are tabular,
 *   not monospace. If the font asset is ever missing, every helper falls
 *   back to the system sans instead of crashing.
 * - 48dp minimum tap targets, 8-point grid spacing.
 */
object Neo {

    const val BORDER_DP = 1
    const val SHADOW_DP = 0
    const val RADIUS_DP = 20
    const val TAP_DP = 48

    const val CARD_RADIUS_DP = 20
    const val PILL_RADIUS_DP = 28
    const val INPUT_RADIUS_DP = 16
    const val ELEVATION_DP = 2

    fun dp(ctx: Context, v: Float): Int {
        return (v * ctx.resources.displayMetrics.density + 0.5f).toInt()
    }

    private var interRegular: Typeface? = null
    private var interBold: Typeface? = null
    private var interTried: Boolean = false

    /** Inter from assets/fonts, loaded once; system sans on any failure. */
    fun type(ctx: Context, bold: Boolean): Typeface {
        if (!interTried) {
            interTried = true
            try {
                interRegular = Typeface.createFromAsset(ctx.assets, "fonts/Inter-Regular.ttf")
                interBold = Typeface.createFromAsset(ctx.assets, "fonts/Inter-Bold.ttf")
            } catch (ignored: Throwable) {
                interRegular = null
                interBold = null
            }
        }
        return if (bold) {
            interBold ?: Typeface.DEFAULT_BOLD
        } else {
            interRegular ?: Typeface.DEFAULT
        }
    }

    fun color(ctx: Context, id: Int): Int {
        return ctx.resources.getColor(id, ctx.theme)
    }

    private fun rect(fill: Int, stroke: Int, strokeDp: Int, radiusDp: Int, ctx: Context): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.setColor(fill)
        d.setCornerRadius(dp(ctx, radiusDp.toFloat()).toFloat())
        if (strokeDp > 0) d.setStroke(dp(ctx, strokeDp.toFloat()), stroke)
        return d
    }

    private fun oval(fill: Int): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(fill)
        return d
    }

    /** Card surface: rounded rect, no border. Elevation is set on the container. */
    fun cardBg(ctx: Context, fill: Int): Drawable {
        return rect(fill, 0, 0, CARD_RADIUS_DP, ctx)
    }

    /** Pill background, used for the active bottom-nav item. */
    fun pillBg(ctx: Context, fill: Int): Drawable {
        return rect(fill, 0, 0, PILL_RADIUS_DP, ctx)
    }

    /** Search field: soft grey fill, rounded, no border. */
    fun inputBg(ctx: Context): Drawable {
        return rect(color(ctx, R.color.surface_sunken), 0, 0, INPUT_RADIUS_DP, ctx)
    }

    /** Filled pill button background with press feedback: pressed state darkens slightly. */
    fun buttonBg(ctx: Context, fill: Int): Drawable {
        val normal = rect(fill, 0, 0, PILL_RADIUS_DP, ctx)
        val pressed = rect(darken(fill, 0.92f), 0, 0, PILL_RADIUS_DP, ctx)
        val states = StateListDrawable()
        states.addState(intArrayOf(android.R.attr.state_pressed), pressed)
        states.addState(intArrayOf(), normal)
        return states
    }

    /** Secondary pill button: white fill, hairline border, soft grey when pressed. */
    fun secondaryBg(ctx: Context): Drawable {
        val hairline = color(ctx, R.color.border)
        val normal = rect(color(ctx, R.color.surface), hairline, BORDER_DP, PILL_RADIUS_DP, ctx)
        val pressed = rect(color(ctx, R.color.surface_sunken), hairline, BORDER_DP, PILL_RADIUS_DP, ctx)
        val states = StateListDrawable()
        states.addState(intArrayOf(android.R.attr.state_pressed), pressed)
        states.addState(intArrayOf(), normal)
        return states
    }

    private fun darken(c: Int, factor: Float): Int {
        val r = (Color.red(c) * factor).toInt()
        val g = (Color.green(c) * factor).toInt()
        val b = (Color.blue(c) * factor).toInt()
        return Color.rgb(r, g, b)
    }

    fun text(ctx: Context, s: String, sp: Float, colorRes: Int, bold: Boolean): TextView {
        val t = TextView(ctx)
        t.text = s
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        t.setTextColor(color(ctx, colorRes))
        t.typeface = type(ctx, bold)
        return t
    }

    /**
     * Tabular figures for scores and counts: sans bold with tabular numerals,
     * so digits line up and the one number that matters reads as a figure.
     * Type scale for the whole app (max 4 sizes, 2 weights):
     * display 56 (the score only), title 24 (band, app label),
     * body 16 (card titles, buttons, body text), caption 13 (meta).
     */
    fun figure(ctx: Context, s: String, sp: Float, colorRes: Int): TextView {
        val t = TextView(ctx)
        t.text = s
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        t.setTextColor(color(ctx, colorRes))
        t.typeface = type(ctx, true)
        t.fontFeatureSettings = "tnum"
        return t
    }

    fun column(ctx: Context): LinearLayout {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.VERTICAL
        return l
    }

    fun row(ctx: Context): LinearLayout {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.HORIZONTAL
        l.gravity = Gravity.CENTER_VERTICAL
        return l
    }

    /** A modern card container: white, rounded, softly elevated. Caller sets padding and children. */
    fun card(ctx: Context, fillRes: Int = R.color.surface): LinearLayout {
        val l = column(ctx)
        l.background = cardBg(ctx, color(ctx, fillRes))
        l.elevation = dp(ctx, ELEVATION_DP.toFloat()).toFloat()
        return l
    }

    /** Filled pill button, 48dp minimum height, bold label. */
    fun button(ctx: Context, label: String, fillRes: Int): Button {
        val b = Button(ctx)
        b.text = label
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        b.typeface = type(ctx, true)
        b.setTextColor(color(ctx, R.color.text_on_accent))
        b.background = buttonBg(ctx, color(ctx, fillRes))
        b.elevation = dp(ctx, ELEVATION_DP.toFloat()).toFloat()
        b.minHeight = dp(ctx, TAP_DP.toFloat())
        b.setPadding(dp(ctx, 24f), 0, dp(ctx, 24f), 0)
        b.isAllCaps = false
        return b
    }

    /** Primary action: refined lime pill, ink text. */
    fun primaryButton(ctx: Context, label: String): Button {
        val b = button(ctx, label, R.color.accent)
        b.setTextColor(color(ctx, R.color.text_on_accent))
        return b
    }

    /** Secondary action: white pill, hairline border, ink text. */
    fun secondaryButton(ctx: Context, label: String): Button {
        val b = Button(ctx)
        b.text = label
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        b.typeface = type(ctx, true)
        b.setTextColor(color(ctx, R.color.text_primary))
        b.background = secondaryBg(ctx)
        b.minHeight = dp(ctx, TAP_DP.toFloat())
        b.setPadding(dp(ctx, 24f), 0, dp(ctx, 24f), 0)
        b.isAllCaps = false
        return b
    }

    /** Small status chip: translucent tint pill, coloured text, no border. */
    fun chip(ctx: Context, label: String, fillRes: Int): TextView {
        val t = TextView(ctx)
        t.text = label
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        t.typeface = type(ctx, true)
        val c = color(ctx, fillRes)
        t.setTextColor(c)
        val tinted = (0x24 shl 24) or (c and 0x00FFFFFF)
        t.background = rect(tinted, 0, 0, 14, ctx)
        t.setPadding(dp(ctx, 12f), dp(ctx, 6f), dp(ctx, 12f), dp(ctx, 6f))
        return t
    }

    /** Section header: 16sp bold ink, no underline bar. Hierarchy comes from weight, not decoration. */
    fun sectionHeader(ctx: Context, label: String): LinearLayout {
        val wrap = column(ctx)
        val t = text(ctx, label, 16f, R.color.text_primary, true)
        wrap.addView(t)
        return wrap
    }

    /** Hairline divider. */
    fun divider(ctx: Context): View {
        val v = View(ctx)
        v.setBackgroundColor(color(ctx, R.color.border))
        v.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1f))
        return v
    }

    fun space(ctx: Context, dpValue: Int): View {
        val v = View(ctx)
        v.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, dpValue.toFloat()))
        return v
    }

    fun lp(w: Int, h: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(w, h)
    }

    fun sevColorRes(sev: Int): Int = when (sev) {
        Severity.CRITICAL -> R.color.status_critical
        Severity.HIGH -> R.color.status_high
        Severity.MEDIUM -> R.color.status_medium
        Severity.LOW -> R.color.status_low
        else -> R.color.status_ok
    }

    /** Severity dot: small solid circle, no border. Caller sizes it (10dp). */
    fun sevMark(ctx: Context, colorRes: Int): View {
        val v = View(ctx)
        v.background = oval(color(ctx, colorRes))
        return v
    }
}
