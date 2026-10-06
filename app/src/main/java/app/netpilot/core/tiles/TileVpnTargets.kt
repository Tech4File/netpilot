package app.netpilot.core.tiles

import app.netpilot.core.model.VpnProfile
import app.netpilot.core.model.VpnType
import app.netpilot.core.vpn.WgConfigCheck

/**
 * Pure profile targeting for the VPN tile's "connect last used" action.
 * The tile can drive tunnels headlessly ONLY when the profile needs no
 * user interaction beyond the already-granted VPN consent:
 *  - WireGuard: embedded engine, config is complete — usable.
 *  - OpenVPN: embedded engine, config is complete — usable.
 *  - Platform IKEv2: the OS shows its own dialog — NOT tile-drivable.
 */
object TileVpnTargets {

    fun usable(profile: VpnProfile): Boolean = when (profile.type) {
        VpnType.WIREGUARD ->
            !profile.wgConfig.isNullOrBlank() && WgConfigCheck.isPlausible(profile.wgConfig!!)
        VpnType.OPENVPN -> !profile.ovpnConfig.isNullOrBlank()
        VpnType.PLATFORM_IKEV2 -> false
    }

    /** Last-used usable profile, else the first usable one, else null. */
    fun pick(profiles: List<VpnProfile>, lastConnectedId: String?): VpnProfile? =
        profiles.firstOrNull { it.id == lastConnectedId && usable(it) }
            ?: profiles.firstOrNull { usable(it) }
}
