package app.netpilot.core.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostnameValidatorTest {

    private fun reason(raw: String?): HostnameCheck.Reason =
        (HostnameValidator.check(raw) as HostnameCheck.Invalid).reason

    @Test
    fun `valid provider hostnames are accepted and normalized`() {
        for (input in listOf("dns.google", "one.one.one.one", "DNS.AdGuard.COM.", "  dns.quad9.net  ")) {
            val check = HostnameValidator.check(input)
            assertTrue("$input should be valid", check is HostnameCheck.Valid)
            assertEquals(input.trim().trimEnd('.').lowercase(), (check as HostnameCheck.Valid).normalized)
        }
    }

    @Test
    fun `empty input is rejected`() {
        assertEquals(HostnameCheck.Reason.EMPTY, reason(null))
        assertEquals(HostnameCheck.Reason.EMPTY, reason(""))
        assertEquals(HostnameCheck.Reason.EMPTY, reason("   "))
    }

    @Test
    fun `ip literals are rejected with a dedicated reason`() {
        assertEquals(HostnameCheck.Reason.IP_NOT_ALLOWED, reason("8.8.8.8"))
        assertEquals(HostnameCheck.Reason.IP_NOT_ALLOWED, reason("1.1.1.1"))
    }

    @Test
    fun `single-label names are rejected`() {
        assertEquals(HostnameCheck.Reason.BAD_LABEL, reason("localhost"))
        assertEquals(HostnameCheck.Reason.BAD_LABEL, reason("dns"))
    }

    @Test
    fun `invalid characters are rejected`() {
        assertEquals(HostnameCheck.Reason.BAD_LABEL, reason("https://dns.google"))
        assertEquals(HostnameCheck.Reason.BAD_LABEL, reason("dns_google.com"))
        assertEquals(HostnameCheck.Reason.BAD_LABEL, reason("dns google com"))
        assertEquals(HostnameCheck.Reason.BAD_LABEL, reason("-dns.google"))
        assertEquals(HostnameCheck.Reason.BAD_LABEL, reason("dns-.google"))
    }

    @Test
    fun `overly long hostnames are rejected`() {
        val tooLong = "a".repeat(251) + ".com"
        assertEquals(HostnameCheck.Reason.TOO_LONG, reason(tooLong))
    }

    @Test
    fun `63-char labels are exactly the limit`() {
        val label = "a".repeat(63)
        assertTrue(HostnameValidator.isValid("$label.com"))
        assertEquals(HostnameCheck.Reason.BAD_LABEL, reason("${label}a.com"))
    }

    @Test
    fun `deeply nested but valid hostnames pass`() {
        assertTrue(HostnameValidator.isValid("a.b.c.d.e.f.g.h.i.j.k.net"))
    }

    @Test
    fun `isValid fast path matches check`() {
        assertTrue(HostnameValidator.isValid("dns.google"))
        assertFalse(HostnameValidator.isValid("8.8.8.8"))
        assertFalse(HostnameValidator.isValid(""))
    }
}
