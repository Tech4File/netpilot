package app.netpilot.core.tiles

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.netpilot.MainActivity
import app.netpilot.R
import app.netpilot.core.dns.DnsProfileRepository
import app.netpilot.core.model.DnsMode
import app.netpilot.core.dns.PrivateDnsManager
import app.netpilot.core.status.StatusNotifications

/**
 * Quick Settings tile: one-tap Private DNS toggle (phones; Android 9+).
 *
 * Battery model: a TileService has NO lifecycle of its own — the system binds
 * it only while the shade shows the tile or the user taps it, and unbinds
 * immediately after. It registers nothing, polls nothing, and keeps nothing
 * alive. Toggling writes the system Private DNS setting (the grant is the
 * same one-time ADB/Shizuku permission), so protection itself stays a pure
 * system setting with zero NetPilot processes behind it.
 */
class DnsTileService : TileService() {

    override fun onStartListening() {
        // Shade opened / tile became visible — refresh and unbind again.
        refreshTile()
    }

    override fun onClick() {
        val context = applicationContext
        if (!PrivateDnsManager.hasWritePermission(context)) {
            openApp()
            return
        }
        // v2.4.0: the tap opens the pop-up — the on/off switch with the
        // profile list underneath (the richer interaction modern tiles use;
        // works on Android 9+ via startActivityAndCollapse, PendingIntent
        // form on API 34+). The quick-toggle behaviour lives inside the
        // pop-up's own switch.
        TileLaunch.launchAndCollapse(
            this,
            Intent(this, TileDialogActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val context = applicationContext
        val model = DnsTileState.compute(
            hasPermission = PrivateDnsManager.hasWritePermission(context),
            mode = PrivateDnsManager.read(context).mode,
            profileName = DnsProfileRepository(context).activeProfile()?.name,
        )
        tile.state = if (model.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_dns_label)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = model.subtitle ?: getString(R.string.tile_dns_off)
        }
        tile.updateTile()
    }

    private fun openApp() {
        TileLaunch.launchAndCollapse(
            this,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
