package app.netpilot.core.tiles

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/**
 * Launches an activity from a tile, collapsing the shade.
 *
 * API contract across NetPilot's range (Android 9 to latest):
 *  - API 24-33: `startActivityAndCollapse(Intent)` is the only form.
 *  - API 34+: the Intent overload is deprecated AND throws for apps
 *    targeting 34+ — the PendingIntent overload is mandatory.
 * Centralised here so every tile call site gets the right branch.
 */
object TileLaunch {

    // The Intent overload is the ONLY form on API 24-33; the deprecation
    // warning is the price of supporting Android 9, suppressed at function
    // level because lint does not honour statement-level suppression here.
    @Suppress("DEPRECATION", "StartActivityAndCollapse", "StartActivityAndCollapseDeprecated")
    fun launchAndCollapse(service: TileService, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 34) {
            @Suppress("InlinedApi") // guarded above; constant inlined at compile time
            service.startActivityAndCollapse(
                PendingIntent.getActivity(
                    service,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            // API 24-33: the Intent overload is the only form.
            service.startActivityAndCollapse(intent)
        }
    }
}
