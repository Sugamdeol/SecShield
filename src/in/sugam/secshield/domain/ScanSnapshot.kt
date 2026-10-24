package `in`.sugam.secshield.domain

/**
 * Immutable picture of one finished scan. The UI renders only from snapshots,
 * never from the engine's mutable fields, so a number on screen can never drift
 * from the scan that produced it.
 */
data class ScanSnapshot(
    val score: Int,
    val band: String,
    val critical: Int,
    val high: Int,
    val medium: Int,
    val low: Int,
    val passing: Int,
    val total: Int,
    val checks: List<Check>,
    val scannedAt: Long
) {
    val failing: List<Check> get() = checks.filter { !it.isPass }
    val countsLine: String get() {
        val parts = ArrayList<String>()
        if (critical > 0) parts.add("$critical critical")
        if (high > 0) parts.add("$high high")
        if (medium > 0) parts.add("$medium medium")
        if (low > 0) parts.add("$low low")
        parts.add("$passing passing")
        return parts.joinToString(" · ")
    }
}
