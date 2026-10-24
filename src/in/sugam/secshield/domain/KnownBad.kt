package `in`.sugam.secshield.domain

/**
 * Small bundled offline list of package names publicly reported as malicious.
 *
 * Sources (both cross-checked in two independent reports each):
 * - Joker family: Pradeo's Sep 2020 disclosure, package names as printed by
 *   Tom's Guide and CybersecurityNews. Joker apps were removed from Google
 *   Play; the list exists so a sideloaded or leftover copy is still caught.
 * - SpyLoan family: McAfee's Dec 2023 disclosure of predatory loan apps
 *   (8M+ installs), package names as printed by McAfee's report and its
 *   coverage. McAfee noted some of these apps later changed to comply with
 *   Play policies, which is one more reason a match is never called a
 *   confirmed-malware verdict: it is a list match, and the user decides.
 *
 * The list is deliberately short and frozen in the app. It is not a malware
 * database and it never phones home for updates, because SecShield declares
 * no internet permission. A match raises the app's risk and shows this
 * wording everywhere it appears; it never labels a heuristic or list match
 * as confirmed malware.
 */
object KnownBad {

    data class Entry(
        val pkg: String,
        val displayName: String,
        val family: String,
        val source: String
    )

    private val ENTRIES = listOf(
        // Joker family, Pradeo Sep 2020.
        Entry("com.convenient.scanner.tb", "Convenient Scanner 2", "Joker", "Pradeo, Sep 2020"),
        Entry("tw.hdwallpaperthemes.emoji.wallpaper", "Emoji Wallpaper", "Joker", "Pradeo, Sep 2020"),
        Entry("com.theone.finger.games", "Fingertip GameBox", "Joker", "Pradeo, Sep 2020"),
        Entry("sms.pushmessage.messaging", "Push Message-Texting&SMS", "Joker", "Pradeo, Sep 2020"),
        Entry("applock.safety.protect.apps", "Safety AppLock", "Joker", "Pradeo, Sep 2020"),
        Entry("sk.pdf.separatedoc.scanner", "Separate Doc Scanner", "Joker", "Pradeo, Sep 2020"),
        // SpyLoan family, McAfee Dec 2023. Display names are given only
        // where the report printed them; the rest are identified by family.
        Entry("com.prestamoseguro.ss", "Préstamo Seguro-Rápido", "SpyLoan", "McAfee, Dec 2023"),
        Entry("com.voscp.rapido", "Préstamo Rápido-Credit Easy", "SpyLoan", "McAfee, Dec 2023"),
        Entry("com.uang.belanja", "SpyLoan loan app", "SpyLoan", "McAfee, Dec 2023"),
        Entry("com.rupiahkilat.best", "RupiahKilat", "SpyLoan", "McAfee, Dec 2023"),
        Entry("com.gotoloan.cash", "SpyLoan loan app", "SpyLoan", "McAfee, Dec 2023"),
        Entry("com.hm.happy.money", "SpyLoan loan app", "SpyLoan", "McAfee, Dec 2023")
    )

    private val BY_PKG = ENTRIES.associateBy { it.pkg }

    /** The list entry for this package, or null when it is not on the list. */
    fun lookup(pkg: String): Entry? = BY_PKG[pkg]

    /** Every entry, for the report and for tests. */
    fun all(): List<Entry> = ENTRIES
}
