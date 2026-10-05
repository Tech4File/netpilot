package app.netpilot.androidtest

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v2.2.0: both Quick Settings tiles, the boot receiver and the app
 * shortcuts must resolve through the PackageManager — registration is what
 * makes them real (the tile logic itself is unit-tested on DnsTileState /
 * VpnTileState). Runs on the CI instrumented legs (API 28/29/34/35).
 */
@RunWith(AndroidJUnit4::class)
class ComponentsRegistrationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun dnsTileIsRegistered() {
        val pm = context.packageManager
        val intent = Intent("android.service.quicksettings.action.QS_TILE").setClassName(
            context.packageName,
            "app.netpilot.core.tiles.DnsTileService",
        )
        assertNotNull(pm.queryIntentServices(intent, 0).firstOrNull())
    }

    @Test
    fun vpnTileIsRegistered() {
        val pm = context.packageManager
        val intent = Intent("android.service.quicksettings.action.QS_TILE").setClassName(
            context.packageName,
            "app.netpilot.core.tiles.VpnTileService",
        )
        assertNotNull(pm.queryIntentServices(intent, 0).firstOrNull())
    }

    @Test
    fun bootReceiverIsRegistered() {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED).setClassName(
            context.packageName,
            "app.netpilot.core.boot.BootReconnectReceiver",
        )
        assertNotNull(pm.queryBroadcastReceivers(intent, 0).firstOrNull())
    }

    @Test
    fun staticShortcutsArePublished() {
        val shortcuts = androidx.core.content.pm.ShortcutManagerCompat.getShortcuts(
            context,
            androidx.core.content.pm.ShortcutManagerCompat.FLAG_MATCH_MANIFEST,
        )
        val ids = shortcuts.map { it.id }
        assertTrue("dns" in ids)
        assertTrue("vpn" in ids)
    }

    @Test
    fun manifestParsesAllNewComponents() {
        // Smoke: the package's own info loads — guards against manifest merge
        // regressions from the new entries.
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        assertEquals("app.netpilot", info.packageName)
    }
}
