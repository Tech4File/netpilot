package app.netpilot.core.vpn

/**
 * Pure-Kotlin OpenVPN configuration parser (no third-party code).
 * Understands the subset of directives that matter for profile management,
 * validation and summary display. Block sections (<ca>, <cert> …) are detected
 * and measured, not fully interpreted.
 */
data class OvpnConfig(
    val isClient: Boolean = false,
    val remotes: List<Remote> = emptyList(),
    val proto: String? = null,
    val cipher: String? = null,
    val dataCiphers: List<String> = emptyList(),
    val auth: String? = null,
    val authUserPass: Boolean = false,
    val caInline: Boolean = false,
    val certInline: Boolean = false,
    val keyInline: Boolean = false,
    val tlsAuth: Boolean = false,
    val tlsCrypt: Boolean = false,
    val redirectGateway: Boolean = false,
    val compLzo: Boolean = false,
    val mtu: Int? = null,
    val verb: Int? = null,
    val remoteRandom: Boolean = false,
    val dhcpDns: List<String> = emptyList(),
    val inlineBlockSizes: Map<String, Int> = emptyMap(),
) {
    data class Remote(val host: String, val port: Int, val proto: String?)

    /** Config carries everything needed to identify and describe the tunnel. */
    fun isUsableForProfile(): Boolean = isClient && remotes.isNotEmpty()

    /** Human-readable one-liner, e.g. "vpn.example.com:1194/udp". */
    fun primaryEndpoint(): String? = remotes.firstOrNull()?.let { r ->
        "${r.host}:${r.port}${r.proto?.let { "/$it" } ?: ""}"
    }
}

object OvpnConfigParser {

    private val REMOTE = Regex("^remote\\s+(\\S+)(?:\\s+(\\d+))?(?:\\s+(\\S+))?\\s*$")
    private val BLOCK_OPEN = Regex("^<(\\S+)>\\s*$")
    private val BLOCK_CLOSE = Regex("^</(\\S+)>\\s*$")

    fun parse(text: String): OvpnConfig {
        var client = false
        var proto: String? = null
        var cipher: String? = null
        var dataCiphers = mutableListOf<String>()
        var auth: String? = null
        var authUserPass = false
        var ca = false
        var cert = false
        var key = false
        var tlsAuth = false
        var tlsCrypt = false
        var redirectGateway = false
        var compLzo = false
        var mtu: Int? = null
        var verb: Int? = null
        var remoteRandom = false
        val remotes = mutableListOf<OvpnConfig.Remote>()
        val dhcpDns = mutableListOf<String>()
        val blocks = mutableMapOf<String, Int>()
        var currentBlock: String? = null

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()

            val openTag = BLOCK_OPEN.find(line)
            val closeTag = BLOCK_CLOSE.find(line)
            if (openTag != null) {
                currentBlock = openTag.groupValues[1].lowercase()
                blocks[currentBlock!!] = 0
                return@forEach
            }
            if (closeTag != null) {
                currentBlock = null
                return@forEach
            }
            if (currentBlock != null) {
                blocks[currentBlock!!] = (blocks[currentBlock!!] ?: 0) + line.length
                return@forEach
            }

            // Strip inline comments ("remote a.com 1194 # my server") then re-check.
            val cleaned = line.substringBefore('#').trim()
            if (cleaned.isEmpty() || cleaned.startsWith(";")) return@forEach

            val tokens = cleaned.split(Regex("\\s+"))
            when (tokens[0].lowercase()) {
                "client", "tls-client" -> client = true
                "remote" -> REMOTE.find(cleaned)?.let { m ->
                    remotes += OvpnConfig.Remote(
                        host = m.groupValues[1],
                        port = m.groupValues[2].toIntOrNull() ?: 1194,
                        proto = m.groupValues[3].takeIf { it.isNotBlank() },
                    )
                }
                "proto" -> proto = tokens.getOrNull(1)
                "cipher" -> cipher = tokens.getOrNull(1)
                "data-ciphers" -> dataCiphers = tokens.drop(1).toMutableList()
                "auth" -> auth = tokens.getOrNull(1)
                "auth-user-pass" -> authUserPass = true
                "ca" -> ca = true
                "cert" -> cert = true
                "key" -> key = true
                "tls-auth" -> tlsAuth = true
                "tls-crypt" -> tlsCrypt = true
                "redirect-gateway" -> redirectGateway = true
                "comp-lzo" -> compLzo = true
                "remote-random" -> remoteRandom = true
                "tun-mtu" -> mtu = tokens.getOrNull(1)?.toIntOrNull()
                "verb" -> verb = tokens.getOrNull(1)?.toIntOrNull()
                "dhcp-option" -> if (tokens.getOrNull(1)?.lowercase() == "DNS".lowercase()) {
                    tokens.getOrNull(2)?.let { dhcpDns += it }
                }
            }
        }

        // A cert/key file reference (not inline) still counts as present.
        return OvpnConfig(
            isClient = client,
            remotes = remotes,
            proto = proto,
            cipher = cipher,
            dataCiphers = dataCiphers,
            auth = auth,
            authUserPass = authUserPass,
            caInline = ca || blocks.containsKey("ca"),
            certInline = cert || blocks.containsKey("cert"),
            keyInline = key || blocks.containsKey("key"),
            tlsAuth = tlsAuth,
            tlsCrypt = tlsCrypt,
            redirectGateway = redirectGateway,
            compLzo = compLzo,
            mtu = mtu,
            verb = verb,
            remoteRandom = remoteRandom,
            dhcpDns = dhcpDns,
            inlineBlockSizes = blocks,
        )
    }
}
