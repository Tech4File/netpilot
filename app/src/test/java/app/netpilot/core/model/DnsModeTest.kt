package app.netpilot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsModeTest {

    @Test
    fun `setting values map back to modes`() {
        assertEquals(DnsMode.OFF, DnsMode.fromSetting("off"))
        assertEquals(DnsMode.AUTOMATIC, DnsMode.fromSetting("opportunistic"))
        assertEquals(DnsMode.CUSTOM, DnsMode.fromSetting("hostname"))
    }

    @Test
    fun `unknown or null values fall back to OFF`() {
        assertEquals(DnsMode.OFF, DnsMode.fromSetting(null))
        assertEquals(DnsMode.OFF, DnsMode.fromSetting("bogus"))
        assertEquals(DnsMode.OFF, DnsMode.fromSetting("OFF")) // case-sensitive on purpose, like the OS
    }

    @Test
    fun `isEncryptingDns reflects user protection`() {
        assertTrue(DnsSystemState(DnsMode.CUSTOM, "dns.google").isEncryptingDns)
        assertTrue(DnsSystemState(DnsMode.AUTOMATIC, null).isEncryptingDns)
        assertFalse(DnsSystemState(DnsMode.OFF, null).isEncryptingDns)
    }
}
