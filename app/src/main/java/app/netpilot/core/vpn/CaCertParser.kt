package app.netpilot.core.vpn

import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64

/**
 * Minimal, dependency-free X.509 CA certificate handling for IKEv2 profiles.
 * Android's platform API requires a CA certificate for username/password (EAP)
 * server authentication — see PlatformVpnController.
 */
object CaCertParser {

    private val PEM_BLOCK = Regex(
        "-----BEGIN CERTIFICATE-----(.*?)-----END CERTIFICATE-----",
        RegexOption.DOT_MATCHES_ALL,
    )

    /**
     * Extracts the first valid PEM certificate block from arbitrary text
     * (e.g. a .pem bundle or a pasted certificate). Returns normalized PEM or null.
     */
    fun extractPem(text: String): String? =
        PEM_BLOCK.findAll(text)
            .map { match ->
                "-----BEGIN CERTIFICATE-----${match.groupValues[1]}-----END CERTIFICATE-----"
            }
            .firstOrNull { decodeCertificateOrNull(it) != null }

    /** Accepts PEM text or raw DER bytes and returns a normalized PEM, or null. */
    fun normalizeToPem(bytes: ByteArray): String? {
        val asText = bytes.toString(Charsets.US_ASCII)
        extractPem(asText)?.let { return it }
        return runCatching {
            val cert = decodeDer(bytes) ?: return@runCatching null
            encodePem(cert)
        }.getOrNull()
    }

    fun decodeCertificate(pem: String): X509Certificate =
        decodeCertificateOrNull(pem) ?: error("Not a valid X.509 certificate")

    fun decodeCertificateOrNull(pem: String): X509Certificate? = runCatching {
        val base64 = pem
            .replace("-----BEGIN CERTIFICATE-----", "")
            .replace("-----END CERTIFICATE-----", "")
            .replace(WHITESPACE, "")
        val der = Base64.getDecoder().decode(base64)
        decodeDer(der)
    }.getOrNull()

    private fun decodeDer(der: ByteArray): X509Certificate? = runCatching {
        CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate
    }.getOrNull()

    private fun encodePem(cert: X509Certificate): String {
        val base64 = Base64.getEncoder().encodeToString(cert.encoded)
        val wrapped = base64.chunked(64).joinToString("\n")
        return "-----BEGIN CERTIFICATE-----\n$wrapped\n-----END CERTIFICATE-----"
    }

    private val WHITESPACE = Regex("\\s")
}
