package app.netpilot.core.tiles

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.netpilot.MainActivity
import app.netpilot.R
import app.netpilot.core.model.VpnProfile
import app.netpilot.core.model.VpnType
import app.netpilot.core.vpn.NetPilotVpnService
import app.netpilot.core.vpn.VpnProfileRepository
import app.netpilot.core.vpn.VpnSessionState
import app.netpilot.core.vpn.WireGuardManager
import app.netpilot.core.vpn.WireGuardRuntime

/**
 * Quick Settings tile: one-tap VPN toggle (Android 9+).
 *
 * v2.4.0: the tile reflects and controls ANY NetPilot tunnel — embedded
 * WireGuard AND the embedded OpenVPN transport — not WireGuard alone (with
 * an OpenVPN profile up the tile used to stay grey, which read as "off"
 * while the device was very much tunnelled). Tap toggles whatever is up;
 * when nothing is up it reconnects the last-used profile of any embedded
 * type; long-press opens the app's VPN page (QS_TILE_PREFERENCES router).
 *
 * Battery model unchanged: the system binds this service only while the
 * shade shows the tile or the user taps it. The visual on/off styling is
 * the SYSTEM's: Tile.STATE_ACTIVE / STATE_INACTIVE are tinted by the
 * platform with the current theme's accent (per-API, light and dark) —
 * NetPilot paints no colors of its own, by design.
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
        val repo = VpnProfileRepository(context)
        val target = TileVpnTargets.pick(repo.list(), repo.lastConnectedId())
        when (
            VpnTileState.compute(
                running = anyTunnelRunning(),
                hasLastProfile = target != null,
                consentGranted = consentGranted(context),
            ).action
        ) {
            VpnTileState.Action.TOGGLE_OFF -> stopActiveTunnel(context)
            VpnTileState.Action.CONNECT_LAST -> connectLast(context, repo, target!!)
            VpnTileState.Action.OPEN_APP -> openApp()
        }
    }

    /** Any NetPilot tunnel: the embedded WireGuard engine or the OpenVPN transport. */
    private fun anyTunnelRunning(): Boolean =
        WireGuardRuntime.isRunning || NetPilotVpnService.isRunning

    private fun stopActiveTunnel(context: Context) {
        if (WireGuardRuntime.isRunning) {
            WireGuardManager.get(context).disconnect { render() }
        } else {
            context.startService(
                Intent(context, NetPilotVpnService::class.java)
                    .setAction(NetPilotVpnService.ACTION_DISCONNECT),
            )
            render()
        }
    }

    private fun connectLast(context: Context, repo: VpnProfileRepository, profile: VpnProfile) {
        // The tile IS a last-used user action: record it so the switch, the
        // tile and the row highlight agree on what "last used" is.
        repo.setLastConnectedId(profile.id)
        when (profile.type) {
            VpnType.WIREGUARD ->
                WireGuardManager.get(context).connect(profile, onResult = { _, _ -> render() })
            VpnType.OPENVPN -> {
                // Consent is already verified by the caller; the embedded
                // engine connects headlessly — the same hand-off the VPN
                // tab performs (no second UI, no bridge).
                context.startService(
                    Intent(context, NetPilotVpnService::class.java)
                        .setAction(NetPilotVpnService.ACTION_CONNECT)
                        .putExtra(NetPilotVpnService.EXTRA_OVPN, profile.ovpnConfig)
                        .putExtra(NetPilotVpnService.EXTRA_SESSION, profile.name)
                        .putExtra(NetPilotVpnService.EXTRA_PROFILE_ID, profile.id),
                )
                render()
            }
            else -> openApp()
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val context = applicationContext
        val model = VpnTileState.compute(
            running = anyTunnelRunning(),
            hasLastProfile = TileVpnTargets.pick(
                VpnProfileRepository(context).list(),
                VpnProfileRepository(context).lastConnectedId(),
            ) != null,
            consentGranted = consentGranted(context),
        )
        tile.state = if (model.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_vpn_label)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = model.subtitle
                ?: VpnSessionState.activeTunnel(context)?.name
                ?: getString(R.string.tile_vpn_off)
        }
        tile.updateTile()
    }

    private fun consentGranted(context: Context): Boolean = try {
        VpnService.prepare(context) == null
    } catch (_: Exception) {
        false
    }

    private fun openApp() {
        TileLaunch.launchAndCollapse(
            this,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
