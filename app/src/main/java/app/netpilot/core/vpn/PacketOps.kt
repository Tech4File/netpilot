package app.netpilot.core.vpn

/**
 * First-party IPv4/UDP packet surgery for the Secure DNS tunnel.
 * Parses DNS-bound UDP datagrams arriving on the TUN and builds correctly
 * checksummed reply packets (RFC 1071 internet checksum + UDP pseudo-header).
 */
object PacketOps {

    const val PROTO_UDP = 17
    const val PORT_DNS = 53

    data class DnsQueryInfo(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val dns: ByteArray,
        val headerLen: Int,
    )

    /** @return parsed query details, or null when this is not a plain UDP/53 IPv4 datagram. */
    fun parseDnsUdpV4(packet: ByteArray, length: Int): DnsQueryInfo? {
        if (length < 28) return null
        if ((packet[0].toInt() and 0xf0) != 0x40) return null // not IPv4
        val ihl = (packet[0].toInt() and 0x0f) * 4
        if (ihl < 20 || length < ihl + 8) return null
        val totalLength = PacketRead.u16(packet, 2)
        if (totalLength > length) return null
        val fragField = PacketRead.u16(packet, 6)
        if (fragField and 0x1fff != 0) return null // fragmented — drop
        if (packet[9].toInt() != PROTO_UDP) return null
        val udpOff = ihl
        val srcPort = PacketRead.u16(packet, udpOff)
        val dstPort = PacketRead.u16(packet, udpOff + 2)
        val udpLength = PacketRead.u16(packet, udpOff + 4)
        if (dstPort != PORT_DNS || udpLength < 20) return null
        if (udpOff + udpLength > length) return null
        val dns = packet.copyOfRange(udpOff + 8, udpOff + udpLength)
        return DnsQueryInfo(
            srcIp = packet.copyOfRange(12, 16),
            dstIp = packet.copyOfRange(16, 20),
            srcPort = srcPort,
            dstPort = dstPort,
            dns = dns,
            headerLen = ihl,
        )
    }

    /** Builds the response packet: addresses/ports swapped, fresh checksums. */
    fun buildUdpV4Response(query: DnsQueryInfo, dnsResponse: ByteArray): ByteArray {
        val udpLength = 8 + dnsResponse.size
        val total = query.headerLen + udpLength
        val out = ByteArray(total)

        // IPv4 header
        out[0] = 0x45
        out[1] = 0x00
        out[2] = ((total shr 8) and 0xff).toByte()
        out[3] = (total and 0xff).toByte()
        out[4] = 0x00; out[5] = 0x00 // id
        out[6] = 0x40; out[7] = 0x00 // DF, no offset
        out[8] = 64 // TTL
        out[9] = PROTO_UDP.toByte()
        out[10] = 0x00; out[11] = 0x00 // checksum filled below
        query.dstIp.copyInto(out, 12) // reply source = query destination
        query.srcIp.copyInto(out, 16)

        // UDP header: src 53 -> client's ephemeral port
        out[query.headerLen] = ((PORT_DNS shr 8) and 0xff).toByte()
        out[query.headerLen + 1] = (PORT_DNS and 0xff).toByte()
        out[query.headerLen + 2] = ((query.srcPort shr 8) and 0xff).toByte()
        out[query.headerLen + 3] = (query.srcPort and 0xff).toByte()
        out[query.headerLen + 4] = ((udpLength shr 8) and 0xff).toByte()
        out[query.headerLen + 5] = (udpLength and 0xff).toByte()
        out[query.headerLen + 6] = 0x00; out[query.headerLen + 7] = 0x00

        dnsResponse.copyInto(out, query.headerLen + 8)

        // IPv4 header checksum
        val ipCs = finalizeChecksum(internetChecksum(out, 0, query.headerLen, 0))
        out[10] = ((ipCs shr 8) and 0xff).toByte()
        out[11] = (ipCs and 0xff).toByte()

        // UDP checksum over pseudo-header + header + payload
        var sum = 0L
        sum = internetChecksum(out, 12, 8, sum) // src + dst from the reply header
        val pseudo = ByteArray(4)
        pseudo[2] = 0x00
        pseudo[3] = PROTO_UDP.toByte()
        pseudo[0] = ((udpLength shr 8) and 0xff).toByte()
        pseudo[1] = (udpLength and 0xff).toByte()
        sum = internetChecksum(pseudo, 0, 4, sum)
        out[query.headerLen + 6] = 0x00; out[query.headerLen + 7] = 0x00
        sum = internetChecksum(out, query.headerLen, udpLength, sum)
        val udpCs = finalizeChecksum(sum)
        out[query.headerLen + 6] = ((udpCs shr 8) and 0xff).toByte()
        out[query.headerLen + 7] = (udpCs and 0xff).toByte()

        return out
    }

    /** One's-complement sum (RFC 1071) with carry folding. */
    fun internetChecksum(data: ByteArray, off: Int, length: Int, initial: Long): Long {
        var sum = initial
        var i = off
        val end = off + length
        while (i + 1 < end) {
            sum += PacketRead.u16(data, i)
            if (sum > 0xffff) sum = (sum and 0xffff) + 1
            i += 2
        }
        if (i < end) { // odd trailing byte, padded with zero
            sum += (data[i].toInt() and 0xff) shl 8
            if (sum > 0xffff) sum = (sum and 0xffff) + 1
        }
        return sum
    }

    fun finalizeChecksum(sum: Long): Int = (sum.toInt() and 0xffff).inv() and 0xffff
}

internal object PacketRead {
    fun u16(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)
}
