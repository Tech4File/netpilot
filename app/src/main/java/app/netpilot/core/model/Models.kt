package app.netpilot.core.model

import androidx.annotation.StringRes
import app.netpilot.R

/** System Private DNS modes — values match Settings.Global "private_dns_mode". */
enum class DnsMode(val settingValue: String) {
    OFF("off"),
    /** Android calls this "opportunistic": use DoT when the network supports it, fall back otherwise. */
    AUTOMATIC("opportunistic"),
    /** Strict mode: always DNS-over-TLS to the configured hostname. */
    CUSTOM("hostname");

    companion object {
        fun fromSetting(value: String?): DnsMode = entries.firstOrNull { it.settingValue == value } ?: OFF
    }
}

/** Snapshot of the system-wide Private DNS state. */
data class DnsSystemState(val mode: DnsMode, val specifier: String?) {
    val isEncryptingDns: Boolean
        get() = mode == DnsMode.CUSTOM || mode == DnsMode.AUTOMATIC
}

/** A user-defined DNS-over-TLS provider. Exactly one can be active at a time. */
data class DnsProfile(
    val id: String,
    val name: String,
    val hostname: String,
)

enum class VpnType(val labelRes: Int) {
    /** Native, platform-managed IKEv2/IPsec (Android 11+, no third-party code). */
    PLATFORM_IKEV2(R.string.vpn_type_ikev2),

    /** OpenVPN-compatible profile (.ovpn import). Data channel is pluggable — see README. */
    OPENVPN(R.string.vpn_type_openvpn),
}

enum class VpnAuthType { USER_PASS, PSK }

/** A user-defined VPN profile. Android allows one active VPN system-wide. */
data class VpnProfile(
    val id: String,
    val name: String,
    val type: VpnType,
    val serverHost: String,
    val serverPort: Int = 1194,
    val authType: VpnAuthType = VpnAuthType.USER_PASS,
    val username: String = "",
    val password: String = "",
    val preSharedKey: String = "",
    /** PEM-encoded CA certificate — required by the platform for IKEv2 EAP auth. */
    val caCertPem: String? = null,
    val ovpnConfig: String? = null,
    val ovpnSummary: String? = null,
)
