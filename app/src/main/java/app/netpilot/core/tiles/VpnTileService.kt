package app.netpilot.core.tiles

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.netpilot.MainActivity
import app.netpilot.R
import app.netpilot.core.model.VpnProfile
import app.netpilot.core.model.VpnType
import app.netpilot.core.platform.Sdk
import app.netpilot.core.vpn.VpnProfileRepository
import app.netpilot.core.vpn.WgConfigCheck
import app.netpilot.core.vpn.WireGuardManager
import app.netpilot.core.vpn.WireGuardRuntime

/**
 * Quick Settings tile: one-tap VPN toggle (v2.2.0) — the second tile on the
 * seam the DNS tile built. Boundaryless exactly like DnsTileService: bound
 * only while the shade shows it, zero cost otherwise.
 *
 * Honest semantics: the tile toggles NetPilot's EMBEDDED WireGuard engine
 * (the only engine that can be toggled headlessly — it dies with the app
 * process, so [WireGuardRuntime] is always the truth). Platform IKEv2
 * tunnels belong to the OS; OpenVPN rides the embedded core when it ships.
 */
class VpnTileService : TileService() {

    override fun onStartListening() {
        // The tile is bound rarely; self-heal any marker drift first (the
        // reconcile converges via TunnelEvents if it changes something).
        WireGuardManager.get(this).reconcile()
        render()
    }

    override fun onClick() {
        val context = applicationContext
        val profile = usableWgProfile(context) ?: run { openApp(); return }
        when (VpnTileState.compute(WireGuardRuntime.isRunning, profile != null, consentGranted(context)).action) {
            VpnTileState.Action.TOGGLE_OFF ->
                WireGuardManager.get(context).disconnect { render() }
            VpnTileState.Action.CONNECT_LAST ->
                WireGuardManager.get(context).connect(profile, onResult = { _, _ -> render() })
            VpnTileState.Action.OPEN_APP -> openApp()
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val context = applicationContext
        val hasProfile = usableWgProfile(context) != null
        val model = VpnTileState.compute(
            running = WireGuardRuntime.isRunning,
            hasWgProfile = hasProfile,
            consentGranted = consentGranted(context),
        )
        tile.state = if (model.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_vpn_label)
        if (Build.VERSION.SDK_INT >= Sdk.Q) {
            tile.subtitle = model.subtitle ?: if (model.action == VpnTileState.Action.OPEN_APP) {
                getString(R.string.tile_vpn_open_app)
            } else {
                getString(R.string.tile_vpn_off)
            }
        }
        tile.updateTile()
    }

    private fun consentGranted(context: android.content.Context): Boolean = try {
        VpnService.prepare(context) == null
    } catch (_: Exception) {
        false
    }

    /** Last-connected WireGuard profile, else the first usable one. */
    private fun usableWgProfile(context: android.content.Context): VpnProfile? {
        val repo = VpnProfileRepository(context)
        fun VpnProfile.usable() = type == VpnType.WIREGUARD && !wgConfig.isNullOrBlank() &&
            WgConfigCheck.isPlausible(wgConfig!!)
        return repo.lastConnectedId()?.let { repo.get(it) }?.takeIf { it.usable() }
            ?: repo.list().firstOrNull { it.usable() }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Sdk.U) {
            @Suppress("InlinedApi") // guarded above; constant inlined at compile time
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this, 0, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            // API 28–33: the Intent overload is the only form; deprecated as of 34.
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
