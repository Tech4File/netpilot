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
        val state = PrivateDnsManager.read(context)
        if (state.mode == DnsMode.CUSTOM) {
            if (PrivateDnsManager.disable(context)) {
                DnsProfileRepository(context).clearActive()
            }
        } else {
            val profile = DnsProfileRepository(context).activeProfile()
            if (profile != null) {
                PrivateDnsManager.applyProfile(context, profile.hostname)
            } else {
                openApp()
                return
            }
        }
        // Keep the status notification host honest after a tile-side flip
        // (the app UI may be nowhere in memory — that is the point).
        StatusNotifications.reconcileStatusService(context)
        refreshTile()
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
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
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
