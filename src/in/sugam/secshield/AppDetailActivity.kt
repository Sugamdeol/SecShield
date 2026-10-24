package `in`.sugam.secshield

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import `in`.sugam.secshield.ui.Actions
import `in`.sugam.secshield.domain.AppRisk
import `in`.sugam.secshield.domain.Severity
import `in`.sugam.secshield.data.Store
import `in`.sugam.secshield.data.CapabilityProbe
import `in`.sugam.secshield.data.DeviceProbe
import `in`.sugam.secshield.ui.Neo

/**
 * Detail screen for one app: its risk, facts, permissions, and fix actions.
 * Every settings intent is guarded; a missing settings screen shows a Toast, never a crash.
 */
class AppDetailActivity : Activity() {

    companion object {
        const val EXTRA_PKG = "pkg"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: run { finish(); return }
        val risk = analyzeOne(pkg)
        if (risk == null) {
            Toast.makeText(this, "Could not load this app", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContentView(buildRoot(risk))
    }

    private fun analyzeOne(pkg: String): AppRisk? {
        val acc = CapabilityProbe.accessibilityServicePackages(this).toSet()
        val notif = CapabilityProbe.notificationListenerPackages(this).toSet()
        val overlay = CapabilityProbe.overlayPackages(this).toSet()
        val admins = DeviceProbe.thirdPartyDeviceAdmins(this).toSet()
        val installers = CapabilityProbe.installerPackages(this).toSet()
        val usage = CapabilityProbe.lastUsedMap(this)
        return AppRisk.analyze(this, pkg, acc, notif, overlay, admins, installers, usage)
    }

    private fun buildRoot(r: AppRisk): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Neo.color(this, R.color.bg))

        // Top bar with a Back button.
        val bar = Neo.row(this)
        bar.setBackgroundColor(Neo.color(this, R.color.surface))
        bar.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
        val back = Neo.secondaryButton(this, "Back")
        back.setOnClickListener { finish() }
        bar.addView(back)
        bar.addView(Neo.text(this, "  App details", 16f, R.color.text_primary, true))
        root.addView(bar)

        val body = Neo.column(this)
        body.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 24f))

        // Header card: icon + label + pkg + risk.
        val head = Neo.card(this)
        head.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
        val headRow = Neo.row(this)
        headRow.gravity = Gravity.TOP
        try {
            val icon = packageManager.getApplicationIcon(r.pkg)
            val iv = ImageView(this)
            iv.setImageDrawable(icon)
            val ivLp = LinearLayout.LayoutParams(Neo.dp(this, 48f), Neo.dp(this, 48f))
            ivLp.rightMargin = Neo.dp(this, 16f)
            headRow.addView(iv, ivLp)
        } catch (ignored: Throwable) {
        }
        val col = Neo.column(this)
        col.addView(Neo.text(this, r.label, 24f, R.color.text_primary, true))
        val pkgTv = Neo.text(this, r.pkgLabel(), 13f, R.color.text_secondary, false)
        pkgTv.setPadding(0, Neo.dp(this, 8f), 0, 0)
        col.addView(pkgTv)
        headRow.addView(col)
        head.addView(headRow)
        // Stitch detail hero: trust score, the mirror of the risk score the
        // list shows (trust = 100 - risk), plus the severity badge.
        val riskRow = Neo.row(this)
        riskRow.setPadding(0, Neo.dp(this, 16f), 0, 0)
        riskRow.addView(Neo.figure(this, "Trust ${100 - r.score}/100", 24f, R.color.text_primary))
        val chip = Neo.chip(this, Severity.label(r.severity), Neo.sevColorRes(r.severity))
        val chipLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        chipLp.leftMargin = Neo.dp(this, 16f)
        riskRow.addView(chip, chipLp)
        head.addView(riskRow)
        body.addView(head)

        // v2.4 known-bad match card. The match is factual and stays visible
        // even after "Mark as trusted" below: trust is the user's dismiss
        // decision, it does not rewrite what the public list says.
        if (r.knownBad != null) {
            body.addView(Neo.space(this, 24))
            body.addView(Neo.sectionHeader(this, "Reported-bad match"))
            body.addView(Neo.space(this, 16))
            val badCard = Neo.card(this)
            badCard.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
            badCard.addView(Neo.text(this,
                "This package is on a public offline list of reported-bad apps: " +
                r.knownBad!!.family + " (" + r.knownBad!!.source + ").",
                16f, R.color.text_primary, true))
            val badBody = Neo.text(this,
                "This is a list match, not a confirmed-malware verdict. The package name matches " +
                "what researchers reported; SecShield did not scan the app's code. Uninstall below " +
                "removes it, or Mark as trusted records that you accept it anyway.",
                16f, R.color.text_secondary, false)
            badBody.setPadding(0, Neo.dp(this, 8f), 0, 0)
            badCard.addView(badBody)
            if (Store(this).isTrusted(r.pkg)) {
                val trustedNote = Neo.text(this, "You marked this app as trusted.",
                    16f, R.color.text_muted, false)
                trustedNote.setPadding(0, Neo.dp(this, 8f), 0, 0)
                badCard.addView(trustedNote)
            }
            body.addView(badCard)
        }

        // Reasons.
        if (r.reasons.isNotEmpty()) {
            body.addView(Neo.space(this, 24))
            body.addView(Neo.sectionHeader(this, "Why this score"))
            body.addView(Neo.space(this, 16))
            val reasonsCard = Neo.card(this)
            reasonsCard.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
            for ((i, reason) in r.reasons.withIndex()) {
                reasonsCard.addView(Neo.text(this, "•  $reason", 16f, R.color.text_primary, false))
                if (i < r.reasons.size - 1) reasonsCard.addView(Neo.space(this, 8))
            }
            body.addView(reasonsCard)
        }

        // Fact boxes: 2 per row.
        body.addView(Neo.space(this, 24))
        body.addView(Neo.sectionHeader(this, "Facts"))
        body.addView(Neo.space(this, 16))
        val facts = listOf(
            "Installed" to AppRisk.prettyTime(r.firstInstallTime),
            "Source" to r.installSourceLabel(),
            "Updated" to AppRisk.prettyTime(r.lastUpdateTime),
            "Last used" to if (r.lastUsedTime > 0) AppRisk.prettyTime(r.lastUsedTime) else "never"
        )
        for (rowIdx in facts.indices step 2) {
            val row = Neo.row(this)
            for (j in rowIdx until minOf(rowIdx + 2, facts.size)) {
                val (label, value) = facts[j]
                val box = Neo.card(this, R.color.surface_sunken)
                box.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
                box.addView(Neo.text(this, label, 13f, R.color.text_muted, true))
                val v = Neo.text(this, value, 16f, R.color.text_primary, true)
                v.setPadding(0, Neo.dp(this, 8f), 0, 0)
                box.addView(v)
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (j > rowIdx) lp.leftMargin = Neo.dp(this, 16f)
                row.addView(box, lp)
            }
            body.addView(row)
            body.addView(Neo.space(this, 16))
        }

        // Privacy combinations.
        val combos = r.privacyCombos()
        if (combos.isNotEmpty()) {
            body.addView(Neo.space(this, 8))
            body.addView(Neo.sectionHeader(this, "Privacy Combinations"))
            body.addView(Neo.space(this, 16))
            val comboCard = Neo.card(this)
            comboCard.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
            for ((i, combo) in combos.withIndex()) {
                comboCard.addView(Neo.text(this, "•  $combo", 16f, R.color.text_primary, false))
                if (i < combos.size - 1) comboCard.addView(Neo.space(this, 8))
            }
            body.addView(comboCard)
        }

        // Declared permissions.
        body.addView(Neo.space(this, 24))
        body.addView(Neo.sectionHeader(this, "Declared permissions"))
        body.addView(Neo.space(this, 16))
        val permCard = Neo.card(this)
        permCard.setPadding(Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f), Neo.dp(this, 16f))
        val dangerous = r.declaredPermissions.filter { AppRisk.DANGEROUS.contains(it) }
        if (dangerous.isEmpty()) {
            permCard.addView(Neo.text(this, "No dangerous permissions are declared in the manifest.",
                13f, R.color.text_muted, false))
        } else {
            for ((i, p) in dangerous.withIndex()) {
                val short = p.substringAfterLast(".")
                val granted = r.grantedDangerous.contains(p)
                permCard.addView(Neo.text(this,
                    (if (granted) "● " else "○ ") + short + (if (granted) " — granted" else " — not granted"),
                    13f, R.color.text_primary, false))
                if (i < dangerous.size - 1) permCard.addView(Neo.space(this, 8))
            }
            val other = r.declaredPermissions.size - dangerous.size
            if (other > 0) {
                permCard.addView(Neo.space(this, 8))
                permCard.addView(Neo.text(this, "+ $other install-time permissions",
                    13f, R.color.text_muted, false))
            }
        }
        body.addView(permCard)

        // Actions. Stitch primary fix first. Android lets no app revoke
        // another app's permissions directly, so the fix button honestly
        // opens the app info page, where Permissions is one tap away.
        body.addView(Neo.space(this, 24))
        body.addView(Neo.sectionHeader(this, "Actions"))
        body.addView(Neo.space(this, 16))
        val revoke = Neo.primaryButton(this, "Revoke Risky Permissions")
        revoke.setOnClickListener { Actions.openAppPermissions(this, r.pkg) }
        body.addView(revoke, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        body.addView(Neo.space(this, 16))
        val hasLocation = r.grantedDangerous.contains(android.Manifest.permission.ACCESS_FINE_LOCATION)
            || r.grantedDangerous.contains(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        if (hasLocation) {
            val inUse = Neo.secondaryButton(this, "In-Use Only")
            inUse.setOnClickListener {
                Toast.makeText(this,
                    "In Permissions, set location to 'Allow only while using the app'",
                    Toast.LENGTH_LONG).show()
                Actions.openAppPermissions(this, r.pkg)
            }
            body.addView(inUse, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            body.addView(Neo.space(this, 16))
        }
        val openSettings = Neo.secondaryButton(this, "Open app settings")
        openSettings.setOnClickListener { Actions.openAppSettings(this, r.pkg) }
        body.addView(openSettings, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        body.addView(Neo.space(this, 16))
        val uninstall = Neo.button(this, "Uninstall app", R.color.flat_red)
        uninstall.setOnClickListener {
            try {
                val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:" + r.pkg))
                startActivity(i)
            } catch (t: Throwable) {
                Toast.makeText(this, "Could not open uninstall", Toast.LENGTH_SHORT).show()
            }
        }
        body.addView(uninstall, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        body.addView(Neo.space(this, 16))
        val trust = Neo.button(this, "Mark as trusted", R.color.flat_yellow)
        trust.setOnClickListener {
            Store(this).trust(r.pkg)
            Store(this).log(Severity.OK, "Trusted app: " + r.label, r.pkg)
            Toast.makeText(this, "Marked as trusted", Toast.LENGTH_SHORT).show()
        }
        body.addView(trust, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val sv = ScrollView(this)
        sv.addView(body)
        root.addView(sv, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }
}
