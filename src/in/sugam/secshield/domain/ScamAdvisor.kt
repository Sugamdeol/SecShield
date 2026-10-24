package `in`.sugam.secshield.domain

/**
 * Offline scam-message advisor. Pure heuristics over pasted text, run on
 * this phone only, nothing sent anywhere.
 *
 * It is deliberately labelled a heuristic check everywhere it appears:
 * it flags common scam markers, it never returns a verdict. A message with
 * no flags is not "safe", it only has no common markers. A message with
 * flags is not "a scam", it only looks like the patterns scammers reuse.
 */
object ScamAdvisor {

    data class Flag(val title: String, val detail: String)

    private val SHORTENERS = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "cutt.ly", "is.gd", "s.id",
        "rebrand.ly", "shorturl.at", "ow.ly", "buff.ly", "tiny.cc", "rb.gy",
        "shrtco.de", "bitly.is", "t.ly", "v.gd", "clck.ru"
    )

    private val SUSPICIOUS_TLDS = setOf(
        "xyz", "top", "click", "buzz", "shop", "vip", "fun", "site", "online"
    )

    private val MONEY_LOGIN_WORDS = setOf(
        "bank", "paytm", "upi", "kyc", "verify", "account", "password", "otp",
        "prize", "lottery", "recharge", "wallet", "card", "loan", "refund"
    )

    /** Brand token to its real domain. A host containing the token but not
     *  ending on the real domain is a lookalike candidate. */
    private val BRAND_DOMAINS = mapOf(
        "paytm" to "paytm.com",
        "paypal" to "paypal.com",
        "google" to "google.com",
        "amazon" to "amazon.com",
        "apple" to "apple.com",
        "whatsapp" to "whatsapp.com",
        "instagram" to "instagram.com",
        "facebook" to "facebook.com",
        "phonepe" to "phonepe.com",
        "sbi" to "sbi.co.in",
        "hdfc" to "hdfcbank.com",
        "icici" to "icicibank.com"
    )

    private val URGENCY_PHRASES = listOf(
        "account blocked", "account will be blocked", "account suspended",
        "verify now", "verify immediately", "kyc expired", "kyc update",
        "you have won", "lottery winner", "claim your prize",
        "within 24 hours", "act now", "urgent action",
        "free recharge", "password expired"
    )

    private val CODE_REQUEST_PHRASES = listOf(
        "share your otp", "send otp", "send your otp", "share your password",
        "enter your pin", "share your pin", "tell me your otp"
    )

    private val URL_REGEX = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)

    /** Returns the flags found in the pasted text, empty when none match. */
    fun check(input: String): List<Flag> {
        val out = ArrayList<Flag>()
        if (input.isBlank()) return out
        val lower = input.lowercase()

        val urls = URL_REGEX.findAll(input).map { it.value }.toList()
        for (raw in urls) {
            var url = raw.trimEnd('.', ',', ')', ']', '!', '?', ';', ':')
            var host = url.substringAfter("://", "")
                .substringBefore("/")
                .substringBefore("?")
                .substringBefore("#")
                .substringBefore(":")
                .lowercase()
            if (host.startsWith("www.")) host = host.substring(4)
            if (host.isEmpty()) continue

            if (SHORTENERS.contains(host)) {
                out.add(Flag("Link shortener",
                    "Shortened links hide the real destination. Expand it before trusting it."))
            }
            if (host.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                out.add(Flag("Link points at a raw IP address",
                    "Legit services use a domain name. Raw IP links are a classic phishing shape."))
            }
            val path = url.lowercase()
            if (path.contains(".apk") || path.endsWith(".apk")) {
                out.add(Flag("Link downloads an APK file",
                    "Apps outside the Play Store skip store review. Do not install from a chat link."))
            }
            val tld = host.substringAfterLast(".", "")
            if (SUSPICIOUS_TLDS.contains(tld) &&
                MONEY_LOGIN_WORDS.any { lower.contains(it) || path.contains(it) }) {
                out.add(Flag("Unusual domain plus a money or login topic",
                    "A .$tld address talking about accounts, KYC or prizes deserves a second look."))
            }
            for ((token, real) in BRAND_DOMAINS) {
                if (host.contains(token) && host != real && !host.endsWith(".$real")) {
                    out.add(Flag("Lookalike of $real",
                        "This address uses the $token name but is not $real. Check the spelling carefully."))
                    break
                }
            }
        }

        for (phrase in URGENCY_PHRASES) {
            if (lower.contains(phrase)) {
                out.add(Flag("Urgency pressure: \"$phrase\"",
                    "Scammers rush you so you act before thinking. Real banks do not threaten closure by SMS."))
                break
            }
        }
        for (phrase in CODE_REQUEST_PHRASES) {
            if (lower.contains(phrase)) {
                out.add(Flag("Asks for a code or password",
                    "No bank, wallet or support team ever asks for your OTP or PIN. Never share one."))
                break
            }
        }
        return out
    }
}
