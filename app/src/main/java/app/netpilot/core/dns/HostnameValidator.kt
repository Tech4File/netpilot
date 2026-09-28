package app.netpilot.core.dns

/**
 * Strict hostname validation for Private DNS (DNS-over-TLS) providers.
 * The Android system requires a *hostname* here (it validates the provider's
 * TLS certificate against this name) — an IP literal is rejected on purpose.
 */
sealed class HostnameCheck {
    data class Valid(val normalized: String) : HostnameCheck()
    data class Invalid(val reason: Reason) : HostnameCheck()

    enum class Reason { EMPTY, TOO_LONG, BAD_LABEL, IP_NOT_ALLOWED }
}

object HostnameValidator {

    private const val MAX_LENGTH = 253
    private val LABEL = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
    private val IPV4 = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$")

    fun check(raw: String?): HostnameCheck {
        val value = raw?.trim()?.trimEnd('.')?.lowercase().orEmpty()
        if (value.isEmpty()) return HostnameCheck.Invalid(HostnameCheck.Reason.EMPTY)
        if (value.length > MAX_LENGTH) return HostnameCheck.Invalid(HostnameCheck.Reason.TOO_LONG)
        if (IPV4.matches(value)) return HostnameCheck.Invalid(HostnameCheck.Reason.IP_NOT_ALLOWED)
        val labels = value.split('.')
        if (labels.size < 2) return HostnameCheck.Invalid(HostnameCheck.Reason.BAD_LABEL)
        for (label in labels) {
            if (!LABEL.matches(label)) return HostnameCheck.Invalid(HostnameCheck.Reason.BAD_LABEL)
        }
        return HostnameCheck.Valid(value)
    }

    /** Convenience for unit tests and UI: fast boolean form. */
    fun isValid(raw: String?): Boolean = check(raw) is HostnameCheck.Valid
}
