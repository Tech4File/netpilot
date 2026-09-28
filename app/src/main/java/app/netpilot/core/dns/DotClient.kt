package app.netpilot.core.dns

import android.net.http.X509TrustManagerExtensions
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * First-party DNS-over-TLS client (RFC 7858) with RFC 7766 two-byte framing.
 *
 * Certificate semantics match Android's own Private DNS: the TLS certificate is
 * chain-validated against the system CAs **and** hostname-verified against the
 * provider hostname (never an IP) — enforced inside the handshake via the
 * platform trust machinery, so no application data can ever flow over an
 * unverified connection. A pre-resolved bootstrap address may be supplied so
 * the tunnel does not recurse into itself; hostname verification applies on
 * the bootstrap path too.
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
            val context = SSLContext.getInstance("TLS")
            context.init(null, arrayOf<TrustManager>(HostnameVerifyingTrustManager(hostname)), null)
            val socket = context.socketFactory.createSocket() as SSLSocket
            socket.apply {
                connect(InetSocketAddress(address, port), timeoutMs)
                soTimeout = timeoutMs
                sslParameters = sslParameters.apply {
                    serverNames = listOf(SNIHostName(hostname))
                }
                // Chain validation + hostname match happen inside
                // startHandshake() — HostnameVerifyingTrustManager enforces
                // both before the handshake can complete, on every path
                // (direct or bootstrap).
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

    /**
     * Delegates to the platform trust machinery via X509TrustManagerExtensions,
     * which performs full chain validation (system CAs) AND hostname
     * verification against [hostname] in a single call — Android's own
     * semantics for host-pinned TLS. Strictly equivalent to endpoint
     * identification, but bound to the target hostname even when connecting
     * through a bootstrap IP.
     */
    private class HostnameVerifyingTrustManager(
        private val hostname: String,
    ) : X509TrustManager {
        private val delegate: X509TrustManager = TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()

        private val extensions = X509TrustManagerExtensions(delegate)

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            // Throws before the handshake completes unless the certificate
            // chain is trusted AND matches [hostname].
            extensions.checkServerTrusted(chain, authType, hostname)
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
            delegate.checkClientTrusted(chain, authType)

        override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
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
