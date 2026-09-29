package app.netpilot.core.dns

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/**
 * First-party DNS-over-TLS client (RFC 7858) with RFC 7766 two-byte framing.
 *
 * Certificate semantics match Android's own Private DNS: the platform performs
 * full CA chain validation AND hostname verification against the provider
 * hostname — inside the handshake, via the standard endpoint identification
 * algorithm ("HTTPS"). A pre-resolved bootstrap address may be supplied so the
 * tunnel does not recurse into itself; the plain TCP socket is wrapped with
 * [javax.net.ssl.SSLSocketFactory.createSocket] passing the logical hostname,
 * so hostname verification applies on the bootstrap path too.
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
            val factory = SSLContext.getDefault().socketFactory
            // Connect plain, then upgrade with the LOGICAL hostname attached —
            // the platform verifies the certificate against [hostname] (never
            // the bootstrap IP) during the handshake.
            val plain = Socket()
            plain.connect(InetSocketAddress(address, port), timeoutMs)
            val socket = factory.createSocket(plain, hostname, port, true) as SSLSocket
            socket.apply {
                soTimeout = timeoutMs
                sslParameters = sslParameters.apply {
                    serverNames = listOf(SNIHostName(hostname))
                    // Secure default: chain validation + hostname verification
                    // are enforced by the platform before the handshake can
                    // complete. No data ever flows on an unverified connection.
                    endpointIdentificationAlgorithm = "HTTPS"
                }
                startHandshake()
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
