package app.netpilot.core.dns

/**
 * Minimal first-party DNS wire-format codec (RFC 1035).
 * Builds standard queries and inspects responses just enough to validate
 * them before forwarding into the Secure DNS tunnel (id + question match).
 */
object DnsMessage {

    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    const val RCODE_NO_ERROR = 0
    const val RCODE_SERVFAIL = 2

    /** Builds a recursive standard query for [name]/[qtype] (class IN). */
    fun buildQuery(id: Int, name: String, qtype: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun s16(v: Int) {
            out.write((v shr 8) and 0xff)
            out.write(v and 0xff)
        }
        s16(id and 0xffff)
        s16(0x0100) // flags: RD=1
        s16(1); s16(0); s16(0); s16(0) // counts: QD=1
        encodeName(name).forEach { out.write(it) }
        s16(qtype)
        s16(1) // IN
        return out.toByteArray()
    }

    /** Encodes a dotted name as QNAME labels (root = single zero byte). */
    fun encodeName(name: String): List<Int> {
        val out = mutableListOf<Int>()
        for (label in name.trim('.').split('.').filter { it.isNotEmpty() }) {
            require(label.length <= 63) { "label too long" }
            out.add(label.length)
            label.forEach { out.add(it.lowercaseChar().code) }
        }
        out.add(0)
        return out
    }

    fun parseId(packet: ByteArray): Int? =
        if (packet.size >= 12) {
            ((packet[0].toInt() and 0xff) shl 8) or (packet[1].toInt() and 0xff)
        } else {
            null
        }

    fun isResponse(packet: ByteArray): Boolean =
        packet.size >= 3 && (packet[2].toInt() and 0x80) != 0

    fun rcode(packet: ByteArray): Int =
        if (packet.size >= 4) packet[3].toInt() and 0x0f else -1

    /**
     * Parses a (possibly compressed) domain name at [off].
     * @return (offset just past the name within this message, decoded name) or null.
     */
    fun parseName(packet: ByteArray, off: Int, maxJumps: Int = 8): Pair<Int, String>? {
        var pos = off
        var end = -1
        var jumps = 0
        var jumped = false
        val sb = StringBuilder()
        while (true) {
            if (pos >= packet.size) return null
            val len = packet[pos].toInt() and 0xff
            if (len == 0) {
                if (!jumped) end = pos + 1
                break
            }
            if (len and 0xc0 == 0xc0) {
                if (pos + 1 >= packet.size) return null
                if (!jumped) end = pos + 2
                if (++jumps > maxJumps) return null
                pos = ((len and 0x3f) shl 8) or (packet[pos + 1].toInt() and 0xff)
                jumped = true
                continue
            }
            if (pos + 1 + len > packet.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 1..len) sb.append(((packet[pos + i].toInt() and 0xff).toChar()))
            pos += 1 + len
        }
        return if (end >= 0) end to sb.toString() else null
    }

    /** The question name of a query/response, or null when malformed. */
    fun questionName(packet: ByteArray): String? {
        if (packet.size < 12) return null
        val qdCount = u16(packet, 4)
        if (qdCount < 1) return null
        return parseName(packet, 12)?.second
    }

    /**
     * Converts a query into a minimal SERVFAIL response (same transaction id,
     * question preserved) — used when the upstream DoT server is unreachable.
     */
    fun servFailResponse(query: ByteArray): ByteArray? {
        if (query.size < 12) return null
        val out = query.copyOf()
        out[2] = ((out[2].toInt() and 0x78) or 0x80).toByte() // QR=1, keep opcode, RCODE low bits here
        out[3] = ((out[3].toInt() and 0xf0) or RCODE_SERVFAIL).toByte()
        out[6] = 0; out[7] = 0 // ANCOUNT = 0
        out[8] = 0; out[9] = 0 // NSCOUNT = 0
        out[10] = 0; out[11] = 0 // ARCOUNT = 0
        return out
    }

    internal fun u16(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)
}
