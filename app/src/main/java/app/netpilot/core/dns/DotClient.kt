package app.netpilot.core.dns

import java.io.InputStream
import java.io.OutputStream
import javax.net.ssl.HttpsURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket

/**
 * First-party DNS-over-TLS client (RFC 7858) with RFC 7766 two-byte framing.
 *
 * Certificate semantics match Android's own Private DNS: the TLS certificate is
 * validated against the provider **hostname** (never an IP). A pre-resolved
 * bootstrap address may be supplied so the tunnel does not recurse into itself.
 */
class DotClient(
    private val hostname: String,
    private val bootstrapAddress: InetAddress? = null,
    private val port: Int = 853,
) {

    /** @return the raw response message, or null on any failure/timeout. */
    fun query(query: ByteArray, timeoutMs: Int = 5_000): ByteArray? {
        if (DnsMessage.parseId(query) == null || DnsMessage.questionName(query) == null) return null
        return try {
            val address = bootstrapAddress ?: InetAddress.getByName(hostname)
            val socket = javax.net.ssl.SSLContext.getDefault().socketFactory.createSocket() as SSLSocket
            socket.apply {
                connect(InetSocketAddress(address, port), timeoutMs)
                soTimeout = timeoutMs
                sslParameters = sslParameters.apply {
                    serverNames = listOf(SNIHostName(hostname))
                    // Verified manually right after the handshake against [hostname].
                    endpointIdentificationAlgorithm = null
                }
                startHandshake()
                check(HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)) {
                    "DoT certificate does not match $hostname"
                }
            }
            socket.use { s ->
                writeFrame(s.getOutputStream(), query)
                readFrame(s.getInputStream())
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val MAX_FRAME = 65_535

        /** RFC 7766: two-byte big-endian length prefix + message. */
        fun writeFrame(out: OutputStream, payload: ByteArray) {
            out.write((payload.size shr 8) and 0xff)
            out.write(payload.size and 0xff)
            out.write(payload)
            out.flush()
        }

        /** Reads one framed message, or null on EOF/truncation/implausible length. */
        fun readFrame(input: InputStream, max: Int = MAX_FRAME): ByteArray? {
            val hi = input.read()
            if (hi < 0) return null
            val lo = input.read()
            if (lo < 0) return null
            val len = (hi shl 8) or lo
            if (len < 12 || len > max) return null
            val buf = ByteArray(len)
            var off = 0
            while (off < len) {
                val n = input.read(buf, off, len - off)
                if (n < 0) return null
                off += n
            }
            return buf
        }
    }
}
