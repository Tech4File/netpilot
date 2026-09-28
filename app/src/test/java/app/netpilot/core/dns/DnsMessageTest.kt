package app.netpilot.core.dns

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsMessageTest {

    @Test
    fun `query wire format is RFC 1035 correct`() {
        val q = DnsMessage.buildQuery(0x1234, "DNS.Google", DnsMessage.TYPE_A)
        assertEquals(0x12, q[0].toInt())
        assertEquals(0x34, q[1].toInt())
        assertEquals(0x01, q[2].toInt()) // QR=0, RD=1
        assertEquals(1, DnsMessage.u16(q, 4)) // QDCOUNT
        // QNAME: 3"dns"4"google"0 + QTYPE(1) + QCLASS(1)
        val expectedTail = byteArrayOf(
            3, 'd'.code.toByte(), 'n'.code.toByte(), 's'.code.toByte(),
            6, 'g'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'g'.code.toByte(),
            'l'.code.toByte(), 'e'.code.toByte(),
            0,
            0, 1, 0, 1,
        )
        assertArrayEquals(expectedTail, q.copyOfRange(12, q.size))
        assertEquals(12 + expectedTail.size, q.size)
    }

    @Test
    fun `aaaa queries carry type 28`() {
        val q = DnsMessage.buildQuery(1, "a.b", DnsMessage.TYPE_AAAA)
        assertEquals(28, DnsMessage.u16(q, q.size - 4))
    }

    @Test
    fun `parseId reads transaction ids`() {
        assertEquals(0xabcd, DnsMessage.parseId(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)))
        assertNull(DnsMessage.parseId(ByteArray(11)))
    }

    @Test
    fun `question names are lowercased and round-trip`() {
        val name = DnsMessage.questionName(DnsMessage.buildQuery(9, "One.One.One.ONE", DnsMessage.TYPE_A))
        assertEquals("one.one.one.one", name)
    }

    @Test
    fun `compression pointers are followed`() {
        // Header + compressed question pointing at offset 12 ("example.com").
        val msg = byteArrayOf(
            0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, // header
            7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(),
            'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0,
            0xc0.toByte(), 12, // pointer back to offset 12
            0, 1, 0, 1,
        )
        assertEquals("example.com", DnsMessage.parseName(msg, 25)?.second)
        // A parser must not loop forever on a crafted pointer cycle.
        val cyclic = byteArrayOf(
            0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0,
            0xc0.toByte(), 12,
        )
        assertNull(DnsMessage.parseName(cyclic, 12, maxJumps = 4))
    }

    @Test
    fun `servfail response marks QR and RCODE`() {
        val q = DnsMessage.buildQuery(0x00ff, "dns.google", DnsMessage.TYPE_A)
        val r = DnsMessage.servFailResponse(q)!!
        assertTrue(DnsMessage.isResponse(r))
        assertEquals(DnsMessage.RCODE_SERVFAIL, DnsMessage.rcode(r))
        assertEquals(0x00ff, DnsMessage.parseId(r))
        assertEquals("dns.google", DnsMessage.questionName(r))
    }

    @Test
    fun `malformed inputs never throw`() {
        assertNull(DnsMessage.questionName(ByteArray(0)))
        assertNull(DnsMessage.servFailResponse(ByteArray(4)))
        assertNull(DnsMessage.parseName(byteArrayOf(5, 'a'.code.toByte()), 0))
        assertNotNull(DnsMessage.encodeName("a.b"))
    }
}
