package app.netpilot.core.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PacketOpsTest {

    @Test
    fun `internet checksum matches RFC 1071 worked example`() {
        val data = byteArrayOf(0x00, 0x01, 0xf2.toByte(), 0x03, 0xf4.toByte(), 0xf5.toByte(), 0xf6.toByte(), 0xf7.toByte())
        val sum = PacketOps.finalizeChecksum(PacketOps.internetChecksum(data, 0, data.size, 0))
        assertEquals(0x220d, sum)
    }

    @Test
    fun `checksum validates to zero over its own cover set`() {
        val header = byteArrayOf(
            0x45, 0x00, 0x00, 0x54, 0x12, 0x34, 0x40, 0x00, 0x40, 0x11, 0, 0,
            10, 0, 0, 1, 10, 0, 0, 2,
        )
        val cs = PacketOps.finalizeChecksum(PacketOps.internetChecksum(header, 0, header.size, 0))
        header[10] = ((cs shr 8) and 0xff).toByte()
        header[11] = (cs and 0xff).toByte()
        val verify = PacketOps.finalizeChecksum(PacketOps.internetChecksum(header, 0, header.size, 0))
        assertEquals(0, verify)
    }

    private fun buildQueryPacket(): ByteArray {
        val dns = app.netpilot.core.dns.DnsMessage.buildQuery(0x4242, "dns.google", app.netpilot.core.dns.DnsMessage.TYPE_A)
        val ihl = 20
        val udpLen = 8 + dns.size
        val total = ihl + udpLen
        val p = ByteArray(total)
        p[0] = 0x45; p[1] = 0x00
        p[2] = ((total shr 8) and 0xff).toByte(); p[3] = (total and 0xff).toByte()
        p[8] = 64; p[9] = 17
        listOf(192, 168, 1, 10).forEachIndexed { i, b -> p[12 + i] = b.toByte() } // src
        listOf(192, 168, 1, 1).forEachIndexed { i, b -> p[16 + i] = b.toByte() } // dst (DNS)
        p[20] = 0x30; p[21] = 0x39 // src port 12345
        p[22] = 0x00; p[23] = 0x35 // dst port 53
        p[24] = ((udpLen shr 8) and 0xff).toByte(); p[25] = (udpLen and 0xff).toByte()
        dns.copyInto(p, 28)
        return p
    }

    @Test
    fun `dns query packets parse and round-trip through response builder`() {
        val packet = buildQueryPacket()
        val parsed = PacketOps.parseDnsUdpV4(packet, packet.size)
        assertNotNull(parsed)
        parsed!!

        assertEquals(12345, parsed.srcPort)
        assertEquals(53, parsed.dstPort)
        assertArrayEquals(byteArrayOf(192.toByte(), 168.toByte(), 1, 10), parsed.srcIp)
        assertArrayEquals(byteArrayOf(192.toByte(), 168.toByte(), 1, 1), parsed.dstIp)
        assertEquals(0x4242, app.netpilot.core.dns.DnsMessage.parseId(parsed.dns))

        val dnsResponse = app.netpilot.core.dns.DnsMessage.servFailResponse(parsed.dns)!!
        val reply = PacketOps.buildUdpV4Response(parsed, dnsResponse)

        // The reply flows server→client, so the query parser must NOT swallow it
        // (its dst port is the client's ephemeral port, not 53).
        assertNull(PacketOps.parseDnsUdpV4(reply, reply.size))

        // Verify reply fields directly.
        assertEquals(53, PacketRead.u16(reply, 20)) // source port
        assertEquals(12345, PacketRead.u16(reply, 22)) // destination port
        val udpLen = PacketRead.u16(reply, 24)
        assertEquals(reply.size, parsed.headerLen + udpLen)
        assertArrayEquals(parsed.dstIp, reply.copyOfRange(12, 16)) // source = query's destination
        assertArrayEquals(parsed.srcIp, reply.copyOfRange(16, 20)) // destination = query's source
        assertArrayEquals(dnsResponse, reply.copyOfRange(28, reply.size))
        // The IPv4 header checksum must verify to zero over the built packet.
        val ipVerify = PacketOps.finalizeChecksum(PacketOps.internetChecksum(reply, 0, parsed.headerLen, 0))
        assertEquals(0, ipVerify)
    }

    @Test
    fun `non-dns and malformed packets are rejected`() {
        val packet = buildQueryPacket()
        // Wrong destination port (123 -> NTP-ish)
        val notDns = packet.copyOf(); notDns[23] = 123.toByte()
        assertNull(PacketOps.parseDnsUdpV4(notDns, notDns.size))
        // Non-UDP protocol
        val icmp = packet.copyOf(); icmp[9] = 1
        assertNull(PacketOps.parseDnsUdpV4(icmp, icmp.size))
        // IPv6 version nibble
        val v6 = packet.copyOf(); v6[0] = 0x65.toByte()
        assertNull(PacketOps.parseDnsUdpV4(v6, v6.size))
        // Fragmented (offset 8)
        val frag = packet.copyOf(); frag[7] = 8
        assertNull(PacketOps.parseDnsUdpV4(frag, frag.size))
        // Truncated buffer
        assertNull(PacketOps.parseDnsUdpV4(packet, 16))
        assertNull(PacketOps.parseDnsUdpV4(packet, 0))
    }
}
