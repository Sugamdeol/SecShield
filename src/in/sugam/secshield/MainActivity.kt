package `in`.sugam.secshield

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import `in`.sugam.secshield.data.ReportPdf
import `in`.sugam.secshield.data.ScanScheduler
import `in`.sugam.secshield.domain.AppRisk
import `in`.sugam.secshield.domain.ScamAdvisor
import `in`.sugam.secshield.domain.ScanSnapshot
import `in`.sugam.secshield.domain.Severity
import `in`.sugam.secshield.ui.Actions
import `in`.sugam.secshield.ui.IconView
import `in`.sugam.secshield.ui.Neo
import `in`.sugam.secshield.ui.RingView
import `in`.sugam.secshield.ui.TrendView
import `in`.sugam.secshield.ui.ShieldStateHolder
import `in`.sugam.secshield.ui.ShieldUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * v2.4 host activity, Stitch-aligned. The screen's one question is answered
 * first, by the score, and nothing competes with it: one giant figure, one
 * band, one action in the thumb zone. Events flow down to the state holder,
 * state flows up through StateFlow, and every panel is built fresh from
 * state, so no view is ever added to a parent twice.
 *
 * Type scale (4 sizes, 2 weights): display 56 (score only), title 24
 * (band), body 16 (section headers are body bold), caption 13.
 * Spacing on the 8-point grid: 8 / 16 / 24 only.
 */
class MainActivity : Activity() {

    private lateinit var holder: ShieldStateHolder
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var content: FrameLayout
    private lateinit var scanBar: LinearLayout
    private lateinit var scanBtn: View
    private lateinit var navBar: LinearLayout
    private var navItems = ArrayList<LinearLayout>()
    private var activeTab: Int = 0
    private var showRest: Boolean = false
    private var appFilter: Int = 0
    // Scam advisor input and result survive panel rebuilds, like appFilter.
    private var scamInput: String = ""
    private var scamFlags: List<ScamAdvisor.Flag>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        holder = (application as App).holder
        setContentView(buildRoot())
        uiScope.launch { holder.state.collect { render(it) } }
        holder.start()
    }

    override fun onResume() {
        super.onResume()
        holder.refreshStored()
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
    }

    override fun onBackPressed() {
        if (activeTab != 0) {
            activeTab = 0
            paintNav()
            updateScanBar()
            render(holder.state.value)
        } else {
            super.onBackPressed()
        }
    }

    // ---------- Shell ----------

    private fun buildRoot(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Neo.color(this, R.color.bg))

        // Top app bar: mark, name, gear. No score chip: the score lives
        // in exactly one place, the hero, so nothing competes with it.
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(Neo.color(this, R.color.surface))
        bar.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
        val mark = IconView(this, IconView.SHIELD)
        mark.contentDescription = "SecShield"
        bar.addView(mark, LinearLayout.LayoutParams(Neo.dp(this, 40f), Neo.dp(this, 40f)))
        val nameCol = LinearLayout(this)
        nameCol.orientation = LinearLayout.VERTICAL
        val nameLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        nameLp.leftMargin = Neo.dp(this, 16f)
        nameCol.addView(Neo.text(this, "SecShield", 16f, R.color.text_primary, true))
        nameCol.addView(Neo.text(this, "ON THIS PHONE · NO CLOUD", 13f, R.color.text_muted, false))
        bar.addView(nameCol, nameLp)
        val spacer = View(this)
        bar.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        val gear = IconView(this, IconView.GEAR)
        gear.contentDescription = "Guard settings"
        gear.isClickable = true
        gear.minimumHeight = Neo.dp(this, 48f)
        gear.minimumWidth = Neo.dp(this, 48f)
        gear.setOnClickListener { goToTab(3) }
        bar.addView(gear, LinearLayout.LayoutParams(Neo.dp(this, 48f), Neo.dp(this, 48f)))
        root.addView(bar)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Scan action, pinned in the thumb zone above the nav bar.
        scanBar = LinearLayout(this)
        scanBar.orientation = LinearLayout.VERTICAL
        scanBar.setBackgroundColor(Neo.color(this, R.color.surface))
        scanBar.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
        scanBtn = Neo.primaryButton(this, "SCAN NOW")
        scanBtn.minimumHeight = Neo.dp(this, 56f)
        scanBtn.setOnClickListener { holder.rescan() }
        scanBar.addView(scanBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scanBar)

        navBar = buildNav()
        root.addView(navBar)
        updateScanBar()
        return root
    }

    private fun buildNav(): LinearLayout {
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setBackgroundColor(Neo.color(this, R.color.surface))
        nav.setPadding(Neo.dp(this, 8f), Neo.dp(this, 8f), Neo.dp(this, 8f), Neo.dp(this, 8f))
        val labels = arrayOf("Shield", "Apps", "History", "Guard")
        val icons = arrayOf(IconView.SHIELD, IconView.APPS, IconView.CLOCK, IconView.GEAR)
        navItems.clear()
        for (idx in labels.indices) {
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER
            item.isClickable = true
            item.minimumHeight = Neo.dp(this, 56f)
            val icon = IconView(this, icons[idx])
            icon.contentDescription = labels[idx]
            item.addView(icon, LinearLayout.LayoutParams(Neo.dp(this, 28f), Neo.dp(this, 28f)))
            val label = Neo.text(this, labels[idx], 13f, R.color.text_primary, true)
            label.gravity = Gravity.CENTER
            item.addView(label)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.leftMargin = Neo.dp(this, 8f)
            lp.rightMargin = Neo.dp(this, 8f)
            item.setOnClickListener { goToTab(idx) }
            nav.addView(item, lp)
            navItems.add(item)
        }
        paintNav()
        return nav
    }

    private fun goToTab(idx: Int) {
        if (activeTab != idx) {
            activeTab = idx
            paintNav()
            updateScanBar()
            render(holder.state.value)
        }
    }

    private fun paintNav() {
        for (i in navItems.indices) {
            val item = navItems[i]
            if (i == activeTab) {
                item.background = Neo.pillBg(this, Neo.color(this, R.color.accent_soft))
                (item.getChildAt(1) as TextView).setTextColor(Neo.color(this, R.color.text_primary))
            } else {
                item.background = null
                (item.getChildAt(1) as TextView).setTextColor(Neo.color(this, R.color.text_muted))
            }
        }
    }

    private fun updateScanBar() {
        scanBar.visibility = if (activeTab == 0) View.VISIBLE else View.GONE
    }

    // ---------- Render ----------

    private fun render(state: ShieldUiState) {
        content.removeAllViews()
        when (state) {
            is ShieldUiState.Loading -> content.addView(loadingView())
            is ShieldUiState.Failed -> content.addView(failedView(state.message))
            is ShieldUiState.Ready -> {
                if (scanBtn is android.widget.Button) {
                    val b = scanBtn as android.widget.Button
                    b.text = if (state.scanning) "Scanning…" else "SCAN NOW"
                    b.isEnabled = !state.scanning
                }
                val panel: View = when (activeTab) {
                    1 -> buildAppsPanel(state)
                    2 -> buildHistoryPanel(state)
                    3 -> buildGuardPanel()
                    else -> buildShieldPanel(state)
                }
                content.addView(panel)
            }
        }
    }

    private fun loadingView(): View {
        val col = Neo.column(this)
        col.gravity = Gravity.CENTER
        col.setPadding(Neo.dp(this, 16f), Neo.dp(this, 24f), Neo.dp(this, 16f), Neo.dp(this, 24f))
        col.addView(Neo.text(this, "Scanning…", 24f, R.color.text_primary, true))
        val sub = Neo.text(this, "Checking 22 things on this phone. This takes a few seconds.",
            16f, R.color.text_muted, false)
        sub.gravity = Gravity.CENTER
        sub.setPadding(0, Neo.dp(this, 16f), 0, 0)
        col.addView(sub)
        return col
    }

    private fun failedView(message: String): View {
        val body = panelBody()
        val card = Neo.card(this)
        card.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
        card.addView(Neo.text(this, "Scan failed", 24f, R.color.text_primary, true))
        val msg = Neo.text(this, message, 16f, R.color.text_secondary, false)
        msg.setPadding(0, Neo.dp(this, 8f), 0, 0)
        card.addView(msg)
        body.addView(card)
        body.addView(Neo.space(this, 16))
        val retry = Neo.primaryButton(this, "Try again")
        retry.setOnClickListener { holder.retry() }
        body.addView(retry, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return wrapScroll(body)
    }

    private fun wrapScroll(body: LinearLayout): ScrollView {
        val sv = ScrollView(this)
        sv.addView(body)
        return sv
    }

    private fun panelBody(): LinearLayout {
        val body = Neo.column(this)
        body.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 24f))
        return body
    }

    // ---------- Shield panel ----------

    private fun buildShieldPanel(state: ShieldUiState.Ready): View {
        val body = panelBody()
        val snap = state.snapshot

        // Hero: the score dominates. Ring + one giant tabular figure + band.
        val hero = Neo.card(this)
        hero.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
        val heroRow = Neo.row(this)
        heroRow.gravity = Gravity.CENTER_VERTICAL
        val ringWrap = FrameLayout(this)
        val ring = RingView(this)
        ring.setProgress(snap.score)
        ring.setRingColor(Neo.color(this, R.color.accent))
        ring.setTrackColor(Neo.color(this, R.color.surface_sunken))
        ringWrap.addView(ring, FrameLayout.LayoutParams(Neo.dp(this, 152f), Neo.dp(this, 152f)))
        val num = Neo.figure(this, snap.score.toString(), 56f, R.color.text_primary)
        num.gravity = Gravity.CENTER
        val numLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        numLp.gravity = Gravity.CENTER
        ringWrap.addView(num, numLp)
        heroRow.addView(ringWrap)
        val heroCol = Neo.column(this)
        val heroColLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        heroColLp.leftMargin = Neo.dp(this, 16f)
        // Stitch hero: band in caps, plus the Shield Protected line on a
        // fully protected phone. Bands are the app's real bands, not the
        // mockup's placeholder label.
        heroCol.addView(Neo.text(this, snap.band.uppercase(), 24f, R.color.text_primary, true))
        if (snap.band == "Protected") {
            val prot = Neo.text(this, "Shield Protected", 16f, R.color.text_secondary, false)
            prot.setPadding(0, Neo.dp(this, 8f), 0, 0)
            heroCol.addView(prot)
        }
        // Stitch counts row, real numbers: risks, warnings, safe apps.
        val risks = snap.critical + snap.high
        val warnings = snap.medium + snap.low
        val safeApps = state.apps.count { it.severity < Severity.MEDIUM }
        val counts = Neo.text(this, "$risks risks · $warnings warnings · $safeApps safe apps",
            16f, R.color.text_secondary, false)
        counts.setPadding(0, Neo.dp(this, 8f), 0, 0)
        heroCol.addView(counts)
        val meta = Neo.text(this,
            if (snap.scannedAt > 0) "Scanned " + relativeTime(snap.scannedAt) else "Not scanned yet",
            13f, R.color.text_secondary, false)
        meta.setPadding(0, Neo.dp(this, 8f), 0, 0)
        heroCol.addView(meta)
        heroRow.addView(heroCol, heroColLp)
        hero.addView(heroRow)
        body.addView(hero)

        // Peak-end: the scan finishes with a result, not a dead end.
        if (state.banner != null) {
            body.addView(Neo.space(this, 16))
            val banner = Neo.card(this)
            banner.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
            val title = if (snap.band == "Protected") "Your phone is in great shape."
                else "Scan complete — " + snap.band + "."
            banner.addView(Neo.text(this, title, 16f, R.color.text_primary, true))
            val deltaLine = when {
                !state.banner.hasPrevious -> "First scan recorded — this is your baseline."
                state.banner.delta == null -> "Score recorded."
                state.banner.delta > 0 -> "Up " + state.banner.delta + " since your last scan."
                state.banner.delta < 0 -> "Down " + (-state.banner.delta) +
                    " since your last scan — check what changed below."
                else -> "Same as your last scan."
            }
            val deltaTv = Neo.text(this, deltaLine, 16f, R.color.text_secondary, false)
            deltaTv.setPadding(0, Neo.dp(this, 8f), 0, 0)
            banner.addView(deltaTv)
            body.addView(banner)
        }

        // Share + Export PDF.
        val shareRow = Neo.row(this)
        shareRow.setPadding(0, Neo.dp(this, 16f), 0, 0)
        val shareBtn = Neo.secondaryButton(this, "Share report")
        shareBtn.setOnClickListener { shareReport(state) }
        shareRow.addView(shareBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val pdfBtn = Neo.secondaryButton(this, "Export PDF")
        val pdfLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        pdfLp.leftMargin = Neo.dp(this, 16f)
        shareRow.addView(pdfBtn, pdfLp)
        pdfBtn.setOnClickListener { exportPdf(state) }
        body.addView(shareRow)

        // v2.4 scam advisor: paste a link or message, checked on this
        // phone only. Heuristic flags, never a verdict.
        body.addView(Neo.space(this, 24))
        body.addView(Neo.sectionHeader(this, "Scam link check"))
        body.addView(Neo.space(this, 16))
        val scamCard = Neo.card(this)
        scamCard.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
        scamCard.addView(Neo.text(this,
            "Paste a link or message. Checked on this phone only, nothing is sent anywhere.",
            16f, R.color.text_muted, false))
        val scamInputView = EditText(this)
        scamInputView.hint = "Paste a link or message…"
        scamInputView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        scamInputView.setTextColor(Neo.color(this, R.color.text_primary))
        scamInputView.setHintTextColor(Neo.color(this, R.color.text_muted))
        scamInputView.background = Neo.inputBg(this)
        scamInputView.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
        scamInputView.minHeight = Neo.dp(this, 48f)
        scamInputView.setText(scamInput)
        scamInputView.setSelection(scamInput.length)
        scamInputView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                scamInput = s?.toString() ?: ""
            }
        })
        scamCard.addView(Neo.space(this, 16))
        scamCard.addView(scamInputView)
        val scamBtn = Neo.primaryButton(this, "Check")
        scamBtn.setOnClickListener {
            scamFlags = ScamAdvisor.check(scamInput)
            render(holder.state.value)
        }
        val scamBtnLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        scamBtnLp.topMargin = Neo.dp(this, 16f)
        scamCard.addView(scamBtn, scamBtnLp)
        val flags = scamFlags
        if (flags != null) {
            scamCard.addView(Neo.space(this, 16))
            if (flags.isEmpty()) {
                scamCard.addView(Neo.text(this,
                    "No common scam markers found. This is a heuristic check, not a safety verdict.",
                    16f, R.color.text_primary, true))
            } else {
                for (f in flags) {
                    scamCard.addView(Neo.text(this, "•  " + f.title, 16f, R.color.text_primary, true))
                    val det = Neo.text(this, f.detail, 16f, R.color.text_secondary, false)
                    det.setPadding(0, Neo.dp(this, 8f), 0, 0)
                    scamCard.addView(det)
                    scamCard.addView(Neo.space(this, 8))
                }
                scamCard.addView(Neo.text(this,
                    "Heuristic check only, not a verdict. When in doubt, do not tap, and never share a code.",
                    16f, R.color.text_muted, false))
            }
        }
        body.addView(scamCard)

        // Score history (last 5), with a guided empty state.
        body.addView(Neo.space(this, 24))
        body.addView(Neo.sectionHeader(this, "Score history"))
        body.addView(Neo.space(this, 16))
        val histCard = Neo.card(this)
        histCard.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
        if (state.history.isEmpty()) {
            histCard.addView(Neo.text(this, "No past scans yet.", 16f, R.color.text_primary, true))
            val guide = Neo.text(this, "Tap Scan now below and this becomes your first entry.",
                16f, R.color.text_muted, false)
            guide.setPadding(0, Neo.dp(this, 8f), 0, 0)
            histCard.addView(guide)
        } else {
            val shown = minOf(5, state.history.size)
            for (i in 0 until shown) {
                val rec = state.history[i]
                val row = Neo.row(this)
                row.addView(Neo.figure(this, "${rec.score}/100", 16f, R.color.text_primary))
                row.addView(Neo.text(this, " · " + rec.band, 16f, R.color.text_secondary, false))
                val sp = View(this)
                row.addView(sp, LinearLayout.LayoutParams(0, 1, 1f))
                row.addView(Neo.text(this, relativeTime(rec.ts), 13f, R.color.text_muted, false))
                histCard.addView(row)
                if (i < shown - 1) {
                    histCard.addView(Neo.space(this, 8))
                    histCard.addView(Neo.divider(this))
                    histCard.addView(Neo.space(this, 8))
                }
            }
        }
        body.addView(histCard)

        // Severity groups.
        addGroup(body, snap, Severity.CRITICAL, "Critical")
        addGroup(body, snap, Severity.HIGH, "High")
        addGroup(body, snap, Severity.MEDIUM, "Medium")
        addGroup(body, snap, Severity.LOW, "Low")

        // Privacy note.
        val note = Neo.card(this, R.color.surface_sunken)
        note.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
        note.addView(Neo.text(this, "Everything stays on this phone", 16f, R.color.text_primary, true))
        val noteBody = Neo.text(this,
            "SecShield declares no internet permission. Nothing it reads can be uploaded, because it has no way to send it.",
            16f, R.color.text_muted, false)
        noteBody.setPadding(0, Neo.dp(this, 8f), 0, 0)
        note.addView(noteBody)
        body.addView(Neo.space(this, 24))
        body.addView(note)

        // v2.4 Play Protect card: the built-in baseline, explained honestly.
        // The button only appears when the security settings screen actually
        // resolves on this phone; otherwise the card is guidance only, never
        // a dead button.
        body.addView(Neo.space(this, 24))
        body.addView(Neo.sectionHeader(this, "Google Play Protect"))
        body.addView(Neo.space(this, 16))
        val ppCard = Neo.card(this)
        ppCard.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
        ppCard.addView(Neo.text(this, "The built-in baseline", 16f, R.color.text_primary, true))
        val ppBody = Neo.text(this,
            "Play Protect already scans every app you install, free, on every Android phone. " +
            "SecShield does not replace it. It adds the audit Play Protect does not show: " +
            "settings checks, permission combinations, and Wi-Fi and sideloading checks.",
            16f, R.color.text_secondary, false)
        ppBody.setPadding(0, Neo.dp(this, 8f), 0, 0)
        ppCard.addView(ppBody)
        val ppIntent = Intent(Settings.ACTION_SECURITY_SETTINGS)
        val ppResolves = try {
            packageManager.resolveActivity(ppIntent, PackageManager.MATCH_DEFAULT_ONLY) != null
        } catch (t: Throwable) {
            false
        }
        if (ppResolves) {
            val ppBtn = Neo.secondaryButton(this, "Open security settings")
            ppBtn.setOnClickListener {
                try {
                    startActivity(ppIntent)
                } catch (t: Throwable) {
                    Toast.makeText(this, "Could not open security settings", Toast.LENGTH_SHORT).show()
                }
            }
            val ppLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            ppLp.topMargin = Neo.dp(this, 16f)
            ppCard.addView(ppBtn, ppLp)
        } else {
            val ppGuide = Neo.text(this,
                "On this phone the security settings screen could not be opened from here. " +
                "Open Settings, then Security, to see Play Protect.",
                16f, R.color.text_muted, false)
            ppGuide.setPadding(0, Neo.dp(this, 8f), 0, 0)
            ppCard.addView(ppGuide)
        }
        body.addView(ppCard)

        return wrapScroll(body)
    }

    private fun addGroup(body: LinearLayout, snap: ScanSnapshot, sev: Int, label: String) {
        val list = snap.checks.filter { it.severity == sev }
        if (list.isEmpty()) return
        body.addView(Neo.space(this, 24))
        val header = Neo.row(this)
        header.addView(Neo.sevMark(this, Neo.sevColorRes(sev)),
            LinearLayout.LayoutParams(Neo.dp(this, 10f), Neo.dp(this, 10f)))
        header.addView(Neo.text(this, "  $label · ${list.size}", 16f, R.color.text_primary, true))
        body.addView(header)
        body.addView(Neo.space(this, 16))
        for (c in list) {
            val card = Neo.card(this)
            card.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
            card.addView(Neo.text(this, c.title, 16f, R.color.text_primary, true))
            val detail = Neo.text(this, c.detail, 16f, R.color.text_secondary, false)
            detail.setPadding(0, Neo.dp(this, 8f), 0, 0)
            card.addView(detail)
            if (c.fixAction != null && c.fixLabel != null) {
                val fixBtn = Neo.secondaryButton(this, c.fixLabel)
                fixBtn.setOnClickListener { Actions.openFix(this, c.fixAction) }
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = Neo.dp(this, 16f)
                card.addView(fixBtn, lp)
            }
            body.addView(card)
            body.addView(Neo.space(this, 16))
        }
    }

    // ---------- Apps panel ----------

    private fun buildAppsPanel(state: ShieldUiState.Ready): View {
        val body = panelBody()
        body.addView(Neo.sectionHeader(this, "Apps · ${state.apps.size}"))

        val search = EditText(this)
        search.hint = "Search installed apps…"
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        search.setTextColor(Neo.color(this, R.color.text_primary))
        search.setHintTextColor(Neo.color(this, R.color.text_muted))
        search.background = Neo.inputBg(this)
        search.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
        search.minHeight = Neo.dp(this, 48f)
        search.setText(state.query)
        search.setSelection(state.query.length)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                holder.setQuery(s?.toString() ?: "")
            }
        })
        body.addView(Neo.space(this, 16))
        body.addView(search)

        // Stitch filter chips, real counts. System apps are left out of scans
        // by design (v2.0 decision), so the System count is honestly 0 and
        // tapping it explains that instead of showing invented rows.
        val chipDefs = listOf(
            "All" to state.apps.size,
            "High Risk" to state.apps.count { it.severity >= Severity.HIGH },
            "Medium" to state.apps.count { it.severity == Severity.MEDIUM },
            "System" to state.apps.count { it.systemApp }
        )
        val chipRow = Neo.row(this)
        chipRow.setPadding(0, Neo.dp(this, 16f), 0, 0)
        for ((ci, def) in chipDefs.withIndex()) {
            val chip = TextView(this)
            chip.text = def.first + " " + def.second
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            chip.typeface = Neo.type(this, true)
            chip.setTextColor(Neo.color(this, R.color.text_primary))
            val active = ci == appFilter
            chip.background = if (active) {
                Neo.pillBg(this, Neo.color(this, R.color.accent_soft))
            } else {
                Neo.pillBg(this, Neo.color(this, R.color.surface_sunken))
            }
            chip.setPadding(Neo.dp(this, 16f), Neo.dp(this, 8f), Neo.dp(this, 16f), Neo.dp(this, 8f))
            chip.minHeight = Neo.dp(this, 40f)
            chip.gravity = Gravity.CENTER
            chip.isClickable = true
            chip.setOnClickListener {
                if (appFilter != ci) {
                    appFilter = ci
                    render(holder.state.value)
                }
            }
            val clp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (ci > 0) clp.leftMargin = Neo.dp(this, 8f)
            chipRow.addView(chip, clp)
        }
        body.addView(chipRow)

        // v2.4 reported-bad matches: only on the unfiltered list, so the
        // section never claims a match the filter is hiding.
        val badMatches = state.apps.filter { it.knownBad != null }
        if (appFilter == 0 && state.query.trim().isEmpty() && badMatches.isNotEmpty()) {
            body.addView(Neo.space(this, 24))
            body.addView(Neo.sectionHeader(this, "Reported-bad matches · ${badMatches.size}"))
            body.addView(Neo.space(this, 16))
            val badCard = Neo.card(this)
            badCard.setPadding(0, 0, 0, 0)
            for ((i, r) in badMatches.withIndex()) {
                val row = Neo.row(this)
                row.isClickable = true
                row.minimumHeight = Neo.dp(this, 48f)
                row.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
                row.setOnClickListener { openDetail(r.pkg) }
                val col = Neo.column(this)
                col.addView(Neo.text(this, r.label, 16f, R.color.text_primary, true))
                val match = Neo.text(this,
                    "Matches a public offline list: " + r.knownBad!!.family + " (" + r.knownBad!!.source + "). " +
                    "A list match, not a confirmed-malware verdict.",
                    16f, R.color.text_muted, false)
                match.setPadding(0, Neo.dp(this, 8f), 0, 0)
                col.addView(match)
                row.addView(col)
                badCard.addView(row)
                if (i < badMatches.size - 1) badCard.addView(Neo.divider(this))
            }
            body.addView(badCard)
        }

        val base = when (appFilter) {
            1 -> state.apps.filter { it.severity >= Severity.HIGH }
            2 -> state.apps.filter { it.severity == Severity.MEDIUM }
            3 -> state.apps.filter { it.systemApp }
            else -> state.apps
        }
        if (appFilter == 3) {
            body.addView(Neo.space(this, 16))
            val sysCard = Neo.card(this)
            sysCard.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
            sysCard.addView(Neo.text(this, "No system apps scanned",
                16f, R.color.text_primary, true))
            val sysBody = Neo.text(this,
                "System apps are left out of scans by design: they ship with the phone and hold broad permissions by default, so scoring them would drown the real risks.",
                16f, R.color.text_muted, false)
            sysBody.setPadding(0, Neo.dp(this, 8f), 0, 0)
            sysCard.addView(sysBody)
            body.addView(sysCard)
            return wrapScroll(body)
        }

        val q = state.query.trim().lowercase()
        val searching = q.isNotEmpty()
        val all = if (searching) {
            base.filter {
                it.label.lowercase().contains(q) || it.pkg.lowercase().contains(q)
            }
        } else {
            base
        }

        if (searching) {
            body.addView(Neo.space(this, 16))
            if (all.isEmpty()) {
                val none = Neo.card(this)
                none.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
                none.addView(Neo.text(this, "No app matches “${state.query}”",
                    16f, R.color.text_primary, true))
                val guide = Neo.text(this, "Try the app name or its package name, e.g. com.example.app.",
                    16f, R.color.text_muted, false)
                guide.setPadding(0, Neo.dp(this, 8f), 0, 0)
                none.addView(guide)
                val clear = Neo.secondaryButton(this, "Clear search")
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = Neo.dp(this, 16f)
                none.addView(clear, lp)
                clear.setOnClickListener { holder.setQuery("") }
                body.addView(none)
            } else {
                for (r in all) {
                    body.addView(appCard(r))
                    body.addView(Neo.space(this, 16))
                }
            }
            return wrapScroll(body)
        }

        val privacy = all.filter { it.privacyCombos().isNotEmpty() }
        if (privacy.isNotEmpty()) {
            body.addView(Neo.space(this, 24))
            body.addView(Neo.sectionHeader(this, "Privacy Combinations"))
            body.addView(Neo.space(this, 16))
            val pCard = Neo.card(this)
            pCard.setPadding(0, 0, 0, 0)
            for ((i, r) in privacy.withIndex()) {
                val row = Neo.row(this)
                row.isClickable = true
                row.minimumHeight = Neo.dp(this, 48f)
                row.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
                row.setOnClickListener { openDetail(r.pkg) }
                val col = Neo.column(this)
                col.addView(Neo.text(this, r.label, 16f, R.color.text_primary, true))
                val combo = Neo.text(this, r.privacyCombos().joinToString("; "),
                    16f, R.color.text_muted, false)
                combo.setPadding(0, Neo.dp(this, 8f), 0, 0)
                col.addView(combo)
                row.addView(col)
                pCard.addView(row)
                if (i < privacy.size - 1) pCard.addView(Neo.divider(this))
            }
            body.addView(pCard)
        }

        val flagged = all.filter { it.severity >= Severity.MEDIUM }
        if (flagged.isNotEmpty()) {
            body.addView(Neo.space(this, 24))
            body.addView(Neo.sectionHeader(this, "Flagged · ${flagged.size}"))
            body.addView(Neo.space(this, 16))
            for (r in flagged) {
                body.addView(appCard(r))
                body.addView(Neo.space(this, 16))
            }
        } else {
            body.addView(Neo.space(this, 24))
            val okCard = Neo.card(this, R.color.status_ok_wash)
            okCard.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
            okCard.addView(Neo.text(this, "No apps flagged", 16f, R.color.text_primary, true))
            val okBody = Neo.text(this, "Every app on this phone looks reasonable by capability.",
                16f, R.color.text_muted, false)
            okBody.setPadding(0, Neo.dp(this, 8f), 0, 0)
            okCard.addView(okBody)
            body.addView(okCard)
        }

        val rest = all.filter { it.severity < Severity.MEDIUM }
        if (rest.isNotEmpty()) {
            body.addView(Neo.space(this, 24))
            val restHeader = Neo.row(this)
            restHeader.isClickable = true
            restHeader.minimumHeight = Neo.dp(this, 48f)
            restHeader.addView(Neo.text(this, "Rest of the phone · ${rest.size}",
                16f, R.color.text_primary, true))
            val sp = View(this)
            restHeader.addView(sp, LinearLayout.LayoutParams(0, 1, 1f))
            restHeader.addView(Neo.text(this, if (showRest) "Hide" else "Show",
                16f, R.color.text_muted, true))
            restHeader.setOnClickListener {
                showRest = !showRest
                render(holder.state.value)
            }
            body.addView(restHeader)
            if (showRest) {
                body.addView(Neo.space(this, 16))
                val card = Neo.card(this)
                card.setPadding(0, 0, 0, 0)
                for ((i, r) in rest.withIndex()) {
                    val row = Neo.row(this)
                    row.isClickable = true
                    row.minimumHeight = Neo.dp(this, 48f)
                    row.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
                    row.setOnClickListener { openDetail(r.pkg) }
                    try {
                        val icon = packageManager.getApplicationIcon(r.pkg)
                        val iv = ImageView(this)
                        iv.setImageDrawable(icon)
                        iv.contentDescription = r.label
                        val ivLp = LinearLayout.LayoutParams(Neo.dp(this, 40f), Neo.dp(this, 40f))
                        ivLp.rightMargin = Neo.dp(this, 16f)
                        row.addView(iv, ivLp)
                    } catch (ignored: Throwable) {
                    }
                    row.addView(Neo.text(this, r.label, 16f, R.color.text_primary, true))
                    card.addView(row)
                    if (i < rest.size - 1) card.addView(Neo.divider(this))
                }
                body.addView(card)
            }
        }
        return wrapScroll(body)
    }

    private fun appCard(r: AppRisk): View {
        val card = Neo.card(this)
        card.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
        card.isClickable = true
        card.minimumHeight = Neo.dp(this, 48f)
        card.setOnClickListener { openDetail(r.pkg) }
        val top = Neo.row(this)
        top.gravity = Gravity.TOP
        try {
            val icon = packageManager.getApplicationIcon(r.pkg)
            val iv = ImageView(this)
            iv.setImageDrawable(icon)
            iv.contentDescription = r.label
            val ivLp = LinearLayout.LayoutParams(Neo.dp(this, 40f), Neo.dp(this, 40f))
            ivLp.rightMargin = Neo.dp(this, 16f)
            top.addView(iv, ivLp)
        } catch (ignored: Throwable) {
        }
        val col = Neo.column(this)
        col.addView(Neo.text(this, r.label, 16f, R.color.text_primary, true))
        val pkg = Neo.text(this, r.pkgLabel() + " · " + r.installSourceLabel(),
            13f, R.color.text_muted, false)
        pkg.setPadding(0, Neo.dp(this, 8f), 0, 0)
        col.addView(pkg)
        val sevRow = Neo.row(this)
        sevRow.setPadding(0, Neo.dp(this, 8f), 0, 0)
        sevRow.addView(Neo.sevMark(this, Neo.sevColorRes(r.severity)),
            LinearLayout.LayoutParams(Neo.dp(this, 10f), Neo.dp(this, 10f)))
        val sevText = Neo.text(this, "  " + Severity.label(r.severity) + " · " + r.score + "/100",
            13f, Neo.sevColorRes(r.severity), true)
        sevRow.addView(sevText)
        col.addView(sevRow)
        if (r.lastUsedTime > 0) {
            val lu = Neo.text(this, "Last used " + AppRisk.prettyTime(r.lastUsedTime),
                13f, R.color.text_muted, false)
            lu.setPadding(0, Neo.dp(this, 8f), 0, 0)
            col.addView(lu)
        }
        top.addView(col)
        card.addView(top)
        return card
    }

    private fun openDetail(pkg: String) {
        val i = Intent(this, AppDetailActivity::class.java)
        i.putExtra(AppDetailActivity.EXTRA_PKG, pkg)
        startActivity(i)
    }

    // ---------- History panel ----------

    private fun buildHistoryPanel(state: ShieldUiState.Ready): View {
        val body = panelBody()
        body.addView(Neo.sectionHeader(this, "Score history"))
        body.addView(Neo.space(this, 16))
        val histCard = Neo.card(this)
        histCard.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
        if (state.history.isEmpty()) {
            histCard.addView(Neo.text(this, "No past scans yet.", 16f, R.color.text_primary, true))
            val guide = Neo.text(this, "Go to Shield and tap Scan now to start the history.",
                16f, R.color.text_muted, false)
            guide.setPadding(0, Neo.dp(this, 8f), 0, 0)
            histCard.addView(guide)
        } else {
            // Stitch progression: each entry shows its move from the older
            // entry below it; the oldest entry is the baseline.
            for ((i, rec) in state.history.withIndex()) {
                val row = Neo.row(this)
                row.addView(Neo.figure(this, "${rec.score}/100", 16f, R.color.text_primary))
                row.addView(Neo.text(this, " · " + rec.band, 16f, R.color.text_secondary, false))
                val sp = View(this)
                row.addView(sp, LinearLayout.LayoutParams(0, 1, 1f))
                val older = state.history.getOrNull(i + 1)
                val deltaText = when {
                    older == null -> "Baseline"
                    rec.score > older.score -> "+" + (rec.score - older.score) + " pts"
                    rec.score < older.score -> "-" + (older.score - rec.score) + " pts"
                    else -> "No change"
                }
                val deltaRes = when {
                    older == null -> R.color.text_muted
                    rec.score > older.score -> R.color.status_ok
                    rec.score < older.score -> R.color.status_critical
                    else -> R.color.text_muted
                }
                row.addView(Neo.text(this, deltaText + " · " + dayLabel(rec.ts),
                    13f, deltaRes, true))
                histCard.addView(row)
                if (i < state.history.size - 1) {
                    histCard.addView(Neo.space(this, 8))
                    histCard.addView(Neo.divider(this))
                    histCard.addView(Neo.space(this, 8))
                }
            }
        }
        body.addView(histCard)

        // v2.4 score trend: bars from real history, oldest to newest.
        // Needs at least 2 recorded scans; with fewer, the list above is all there is.
        if (state.history.size >= 2) {
            body.addView(Neo.space(this, 24))
            body.addView(Neo.sectionHeader(this, "Score trend"))
            body.addView(Neo.space(this, 16))
            val trendCard = Neo.card(this)
            trendCard.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
            val trend = TrendView(this)
            trend.setRecords(state.history)
            trendCard.addView(trend, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Neo.dp(this, 160f)))
            val oldest = state.history.last()
            val latest = state.history.first()
            val best = state.history.maxOf { it.score }
            val trendNote = Neo.text(this,
                "From " + oldest.score + "/100 to " + latest.score + "/100 · Best " + best + "/100",
                16f, R.color.text_secondary, false)
            trendNote.setPadding(0, Neo.dp(this, 8f), 0, 0)
            trendCard.addView(trendNote)
            body.addView(trendCard)
        }

        body.addView(Neo.space(this, 24))
        body.addView(Neo.sectionHeader(this, "Event log"))
        body.addView(Neo.space(this, 16))
        val logCard = Neo.card(this)
        logCard.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
        if (state.events.isEmpty()) {
            logCard.addView(Neo.text(this, "Nothing logged yet.", 16f, R.color.text_muted, false))
        } else {
            val recent = state.events.takeLast(50).reversed()
            for ((i, e) in recent.withIndex()) {
                val col = Neo.column(this)
                val titleRow = Neo.row(this)
                titleRow.addView(Neo.sevMark(this, Neo.sevColorRes(e.severity)),
                    LinearLayout.LayoutParams(Neo.dp(this, 10f), Neo.dp(this, 10f)))
                titleRow.addView(Neo.text(this, "  " + e.title, 16f, R.color.text_primary, true))
                col.addView(titleRow)
                val det = Neo.text(this, e.detail, 16f, R.color.text_secondary, false)
                det.setPadding(0, Neo.dp(this, 8f), 0, 0)
                col.addView(det)
                val when_ = Neo.text(this, relativeTime(e.ts), 13f, R.color.text_muted, false)
                when_.setPadding(0, Neo.dp(this, 8f), 0, 0)
                col.addView(when_)
                logCard.addView(col)
                if (i < recent.size - 1) {
                    logCard.addView(Neo.space(this, 8))
                    logCard.addView(Neo.divider(this))
                    logCard.addView(Neo.space(this, 8))
                }
            }
        }
        body.addView(logCard)

        val clearBtn = Neo.secondaryButton(this, "Clear log")
        clearBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear the event log?")
                .setMessage("This deletes all logged events. Scores in the history above stay.")
                .setPositiveButton("Clear") { _, _ -> holder.clearEvents() }
                .setNegativeButton("Keep", null)
                .show()
        }
        body.addView(Neo.space(this, 16))
        body.addView(clearBtn)

        return wrapScroll(body)
    }

    // ---------- Guard panel (Stitch Settings / Guard screen) ----------

    private fun buildGuardPanel(): View {
        val body = panelBody()
        body.addView(Neo.sectionHeader(this, "Guard"))
        body.addView(Neo.space(this, 16))
        // Only the toggles the app actually has are switches. The install
        // monitor and Wi-Fi audit have no off switch in the product, so
        // they are honest always-on rows, never decorative toggles.
        val card = Neo.card(this)
        card.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
        val dailyOn = holder.toggle("daily_scan", false)
        card.addView(switchRow("Daily Automatic Scan",
            "Runs every day at 09:00, notifies only when something needs attention", dailyOn) { on ->
            holder.setToggle("daily_scan", on)
            if (on) ScanScheduler.schedule(this) else ScanScheduler.cancel(this)
        })
        card.addView(Neo.space(this, 8))
        card.addView(Neo.divider(this))
        card.addView(Neo.space(this, 8))
        val alertsOn = holder.toggle("alerts", true)
        card.addView(switchRow("Immediate Threat Alerts",
            "Notifications for new apps and daily scan findings", alertsOn) { on ->
            holder.setToggle("alerts", on)
        })
        card.addView(Neo.space(this, 8))
        card.addView(Neo.divider(this))
        card.addView(Neo.space(this, 8))
        card.addView(statusRow("Real-time Install Shield", "Always on"))
        card.addView(Neo.space(this, 8))
        card.addView(Neo.divider(this))
        card.addView(Neo.space(this, 8))
        card.addView(statusRow("Wi-Fi Network Warnings", "In every scan"))
        body.addView(card)

        body.addView(Neo.space(this, 24))
        val note = Neo.card(this, R.color.surface_sunken)
        note.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
        note.addView(Neo.text(this, "Install monitor runs in the background and re-arms on reboot.",
            16f, R.color.text_muted, false))
        body.addView(note)
        body.addView(Neo.space(this, 16))
        body.addView(Neo.text(this, "SecShield 2.4 · Kotlin",
            13f, R.color.text_muted, false))
        return wrapScroll(body)
    }

    private fun statusRow(title: String, state: String): View {
        val row = Neo.row(this)
        row.addView(Neo.text(this, title, 16f, R.color.text_primary, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Neo.text(this, state, 13f, R.color.status_ok, true))
        return row
    }

    private fun switchRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit): View {
        val row = Neo.row(this)
        val col = Neo.column(this)
        col.addView(Neo.text(this, title, 16f, R.color.text_primary, true))
        val subTv = Neo.text(this, sub, 16f, R.color.text_secondary, false)
        subTv.setPadding(0, Neo.dp(this, 8f), 0, 0)
        col.addView(subTv)
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this)
        sw.isChecked = on
        sw.minHeight = Neo.dp(this, 48f)
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        row.addView(sw)
        return row
    }

    // ---------- Share / PDF ----------

    private fun buildReportText(state: ShieldUiState.Ready): String {
        val sb = StringBuilder()
        val snap = state.snapshot
        sb.append("SecShield report\n")
        sb.append("Score: ").append(snap.score).append("/100 (").append(snap.band).append(")\n")
        sb.append(snap.passing).append(" of ").append(snap.total).append(" checks passed\n")
        sb.append("Scanned ").append(relativeTime(snap.scannedAt)).append("\n\n")
        if (state.history.isNotEmpty()) {
            sb.append("Score history\n")
            for (rec in state.history.take(10)) {
                sb.append(relativeTime(rec.ts)).append(" · ").append(rec.score).append("/100 (")
                    .append(rec.band).append(")\n")
            }
            sb.append("\n")
        }
        if (snap.failing.isNotEmpty()) {
            sb.append("Findings\n")
            for (c in snap.failing) {
                sb.append(Severity.label(c.severity)).append(" · ").append(c.title).append("\n")
                sb.append(c.detail).append("\n")
            }
            sb.append("\n")
        }
        val flagged = state.apps.filter { it.severity >= Severity.MEDIUM }
        if (flagged.isNotEmpty()) {
            sb.append("Flagged apps\n")
            for (r in flagged) {
                sb.append(r.label).append(" · Risk ").append(r.score).append("/100 · ")
                    .append(r.shortReason()).append("\n")
            }
            sb.append("\n")
        }
        val combos = ArrayList<String>()
        for (r in state.apps) {
            for (combo in r.privacyCombos()) combos.add(r.label + ": " + combo)
        }
        if (combos.isNotEmpty()) {
            sb.append("Privacy combinations\n")
            for (c in combos) sb.append(c).append("\n")
            sb.append("\n")
        }
        // v2.4 report additions: known-bad matches, score trend, top risks.
        val bad = state.apps.filter { it.knownBad != null }
        if (bad.isNotEmpty()) {
            sb.append("Reported-bad matches (list matches, not confirmed-malware verdicts)\n")
            for (r in bad) {
                sb.append(r.label).append(" (").append(r.pkg).append(") · ")
                    .append(r.knownBad!!.family).append(", ").append(r.knownBad!!.source).append("\n")
            }
            sb.append("\n")
        }
        if (state.history.size >= 2) {
            val oldest = state.history.last()
            val latest = state.history.first()
            val best = state.history.maxOf { it.score }
            val worst = state.history.minOf { it.score }
            sb.append("Score trend\n")
            sb.append("From ").append(oldest.score).append("/100 to ").append(latest.score)
                .append("/100 · Best ").append(best).append("/100 · Lowest ").append(worst)
                .append("/100 over ").append(state.history.size).append(" scans\n\n")
        }
        val topRisks = state.apps.filter { it.severity >= Severity.MEDIUM }
            .sortedByDescending { it.score }.take(3)
        if (topRisks.isNotEmpty()) {
            sb.append("Top risks\n")
            for (r in topRisks) {
                sb.append(r.label).append(" · Risk ").append(r.score).append("/100 · ")
                    .append(r.shortReason()).append("\n")
            }
        }
        return sb.toString()
    }

    private fun shareReport(state: ShieldUiState.Ready) {
        try {
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_TEXT, buildReportText(state))
            startActivity(Intent.createChooser(i, "Share SecShield report"))
        } catch (t: Throwable) {
            Toast.makeText(this, "No app can take the share", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportPdf(state: ShieldUiState.Ready) {
        try {
            val file = ReportPdf.build(this, state.snapshot, state.apps)
            if (file == null) {
                Toast.makeText(this, "Could not build the PDF", Toast.LENGTH_SHORT).show()
                return
            }
            val uri = Uri.parse("content://in.sugam.secshield.files/" + file.name)
            val i = Intent(Intent.ACTION_SEND)
            i.type = "application/pdf"
            i.putExtra(Intent.EXTRA_STREAM, uri)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(i, "Share SecShield PDF report"))
        } catch (t: Throwable) {
            Toast.makeText(this, "Could not share the PDF", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Helpers ----------

    /** Calendar-day label for history rows: Today / Yesterday / d MMM yyyy. */
    fun dayLabel(ts: Long): String {
        if (ts <= 0) return "never"
        val cal = java.util.Calendar.getInstance()
        val today = cal.get(java.util.Calendar.DAY_OF_YEAR)
        val todayYear = cal.get(java.util.Calendar.YEAR)
        cal.timeInMillis = ts
        val day = cal.get(java.util.Calendar.DAY_OF_YEAR)
        val year = cal.get(java.util.Calendar.YEAR)
        if (day == today && year == todayYear) return "Today"
        val yCal = java.util.Calendar.getInstance()
        yCal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        if (cal.get(java.util.Calendar.DAY_OF_YEAR) == yCal.get(java.util.Calendar.DAY_OF_YEAR)
            && cal.get(java.util.Calendar.YEAR) == yCal.get(java.util.Calendar.YEAR)) {
            return "Yesterday"
        }
        val f = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
        return f.format(java.util.Date(ts))
    }

    fun relativeTime(ts: Long): String {
        if (ts <= 0) return "never"
        val diff = System.currentTimeMillis() - ts
        return when {
            diff < 60_000 -> "just now"
            diff < 3_600_000 -> "${diff / 60_000} min ago"
            diff < 86_400_000 -> "${diff / 3_600_000} h ago"
            diff < 172_800_000 -> "yesterday"
            else -> {
                val f = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
                f.format(java.util.Date(ts))
            }
        }
    }
}
