package `in`.sugam.secshield.domain

/** Severity ladder shared by posture checks and app risk. Text label always accompanies colour. */
object Severity {
    const val OK = 0
    const val LOW = 1
    const val MEDIUM = 2
    const val HIGH = 3
    const val CRITICAL = 4

    fun label(sev: Int): String = when (sev) {
        CRITICAL -> "CRITICAL"
        HIGH -> "HIGH"
        MEDIUM -> "MEDIUM"
        LOW -> "LOW"
        else -> "PASSING"
    }

    /** Weight a failing check subtracts from the 100 point score. */
    fun weight(sev: Int): Int = when (sev) {
        CRITICAL -> 14
        HIGH -> 8
        MEDIUM -> 4
        LOW -> 2
        else -> 0
    }
}
