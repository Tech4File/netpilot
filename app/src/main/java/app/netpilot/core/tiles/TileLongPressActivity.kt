package app.netpilot.core.tiles

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.core.content.IntentCompat
import app.netpilot.MainActivity
import app.netpilot.ui.NavTab

/**
 * Long-press router for NetPilot's Quick Settings tiles.
 *
 * Android opens this activity (declared with the ACTION_QS_TILE_PREFERENCES
 * intent-filter) when the user long-presses one of our tiles, passing the
 * tile's component. It routes straight to the matching app page — VPN tile
 * long-press opens the VPN tab, DNS tile long-press opens the DNS tab —
 * then finishes invisibly. Works on every Android that has tiles (9+).
 */
class TileLongPressActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val component: ComponentName? = IntentCompat.getParcelableExtra(
            intent,
            EXTRA_TILE_COMPONENT,
            ComponentName::class.java,
        )
        val tab = when (component?.className) {
            DnsTileService::class.java.name -> NavTab.PRIVATE_DNS.ordinal
            VpnTileService::class.java.name -> NavTab.VPN.ordinal
            else -> -1
        }
        if (tab >= 0) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SHORTCUT_TAB, tab)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
        finish()
    }

    companion object {
        /** TileService.EXTRA_COMPONENT_NAME — the system-provided tile identity. */
        private const val EXTRA_TILE_COMPONENT =
            "android.service.quicksettings.extra.COMPONENT_NAME"
    }
}
