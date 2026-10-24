package `in`.sugam.secshield.domain

/** One posture check result. */
data class Check(
    val id: String,
    val title: String,
    val detail: String,
    val severity: Int,
    /** null when there is nothing the user can do about it, otherwise an intent action string. */
    val fixAction: String?,
    val fixLabel: String?
) {
    val isPass: Boolean get() = severity == Severity.OK
}
