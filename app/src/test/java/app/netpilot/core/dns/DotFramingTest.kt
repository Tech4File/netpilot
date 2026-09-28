package app.netpilot.core.dns

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** RFC 7766 two-byte framing used by DNS-over-TLS (DotClient). */
class DotFramingTest {

    @Test
    fun `frame write and read round-trip`() {
        val payload = ByteArray(300) { (it % 251).toByte() }
        val out = ByteArrayOutputStream()
        DotClient.writeFrame(out, payload)
        val bytes = out.toByteArray()
        assertEquals(302, bytes.size)
        assertEquals(0x01, bytes[0].toInt()) // 300 = 0x012C
        assertEquals(0x2c, bytes[1].toInt())
        val read = DotClient.readFrame(ByteArrayInputStream(bytes))
        assertArrayEquals(payload, read)
    }

    @Test
    fun `eof yields null instead of throwing`() {
        assertNull(DotClient.readFrame(ByteArrayInputStream(ByteArray(0))))
        assertNull(DotClient.readFrame(ByteArrayInputStream(byteArrayOf(0x01))))
    }

    @Test
    fun `implausible lengths are rejected`() {
        // Length 5 (< 12) — a DNS message cannot be this small.
        assertNull(DotClient.readFrame(ByteArrayInputStream(byteArrayOf(0, 5, 0, 0, 0, 0, 0))))
        // Length larger than max.
        val tooBig = byteArrayOf(0xff.toByte(), 0xff.toByte())
        assertNull(DotClient.readFrame(ByteArrayInputStream(tooBig), max = 1024))
    }

    @Test
    fun `truncated payload yields null`() {
        val payload = ByteArray(100) { it.toByte() }
        val out = ByteArrayOutputStream()
        DotClient.writeFrame(out, payload)
        val all = out.toByteArray()
        val truncated = all.copyOf(all.size - 10) // missing tail bytes
        assertNull(DotClient.readFrame(ByteArrayInputStream(truncated)))
    }
}
