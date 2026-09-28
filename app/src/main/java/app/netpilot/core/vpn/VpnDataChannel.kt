package app.netpilot.core.vpn

import android.os.ParcelFileDescriptor

/**
 * Pluggable OpenVPN data-channel interface.
 *
 * DESIGN NOTE (docs/SECURITY.md → Dependency policy): NetPilot's shipped code is
 * 100% first-party. A full OpenVPN protocol engine (TLS control channel, packet
 * HMAC, AEAD data channel) is a large, security-critical piece of software that
 * we deliberately keep behind this seam so it can be:
 *   a) provided by a separately reviewed transport module, or
 *   b) replaced in future by an audited in-house implementation,
 * without ever silently shipping unreviewed crypto in this APK.
 * The native IKEv2/IPsec path (Android 11+) is fully functional out of the box.
 */
interface VpnDataChannel {

    /**
     * Runs the protocol session over the established TUN.
     * @return true once the session is up; false if the engine rejected the config.
     */
    fun open(tun: ParcelFileDescriptor, config: OvpnConfig): Boolean

    /** Tears the session down and releases the TUN. Must be idempotent. */
    fun close()

    companion object {
        /**
         * Set by an optional transport module at process start. When null, the
         * UI explains the situation instead of pretending to connect.
         */
        @Volatile
        var factory: ((android.content.Context) -> VpnDataChannel?)? = null
    }
}
