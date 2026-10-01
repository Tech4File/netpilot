package app.netpilot.core.vpn

import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyFormatException
import com.wireguard.crypto.KeyPair

/**
 * Assembles a real WireGuard .conf from direct field input (own-server flow):
 * the user runs the server, NetPilot can generate the client keypair on
 * device, and the produced config is the same format the .conf importer
 * consumes — one validation gate for both paths.
 */
object WireGuardConfBuilder {

    data class Fields(
        val address: String,
        val clientKey: String,
        val peerKey: String,
        val allowedIps: String,
    )

    /** New client keypair for the manual flow (public key goes on the server). */
    fun generateKeyPair(): Pair<String, String> {
        val pair = KeyPair()
        return pair.privateKey.toBase64() to pair.publicKey.toBase64()
    }

    fun isValidKey(base64: String): Boolean = try {
        Key.fromBase64(base64.trim())
        true
    } catch (_: KeyFormatException) {
        false
    }

    /**
     * Builds a complete .conf, or null when a field is missing/invalid.
     * Output passes [WgConfigCheck.isPlausible] by construction.
     */
    fun assembleOrNull(
        address: String,
        clientKey: String,
        peerKey: String,
        endpointHost: String,
        endpointPort: Int,
        allowedIps: String,
    ): String? {
        val addr = address.trim()
        val priv = clientKey.trim()
        val pub = peerKey.trim()
        val ips = allowedIps.trim()
        val host = endpointHost.trim()
        if (addr.isEmpty() || priv.isEmpty() || pub.isEmpty() || ips.isEmpty() || host.isEmpty()) return null
        if (endpointPort !in 1..65535) return null
        if (!isValidKey(priv) || !isValidKey(pub)) return null
        return buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = $priv")
            appendLine("Address = $addr")
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = $pub")
            appendLine("AllowedIPs = $ips")
            appendLine("Endpoint = $host:$endpointPort")
            appendLine("PersistentKeepalive = 25")
        }.trimEnd() + "\n"
    }
}
