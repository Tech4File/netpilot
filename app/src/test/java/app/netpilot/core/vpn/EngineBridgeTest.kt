package app.netpilot.core.vpn

import android.app.Application
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

/**
 * Pins the OpenVPN engine bridge contract: engine detection and the exact
 * documented control intents (de.blinkt.openvpn external API) NetPilot sends.
 */
@RunWith(RobolectricTestRunner::class)
class EngineBridgeTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun resetEngine() {
        // Robolectric starts with no third-party packages installed.
        assertFalse(EngineBridge.primaryEngineInstalled(context))
    }

    private fun installEngine() {
        val pm = context.packageManager
        val info = PackageInfo().apply { packageName = EngineBridge.ICS_PACKAGE }
        Shadows.shadowOf(pm).installPackage(info)
        assertTrue(EngineBridge.primaryEngineInstalled(context))
    }

    @Test
    fun `connect intent targets the documented external control activity`() {
        installEngine()
        assertTrue(EngineBridge.launchConnect(context, "Home VPN"))
        val sent = Shadows.shadowOf(context).nextStartedActivity
        assertEquals(EngineBridge.ICS_CONNECT_CLASS, sent.component?.className)
        assertEquals(EngineBridge.ICS_PACKAGE, sent.component?.packageName)
        assertEquals(EngineBridge.ICS_CONNECT_ACTION, sent.action)
        assertEquals("Home VPN", sent.getStringExtra(EngineBridge.EXTRA_PROFILE_NAME))
    }

    @Test
    fun `connect refuses to fire without the engine`() {
        assertFalse(EngineBridge.launchConnect(context, "Home VPN"))
        assertNull(Shadows.shadowOf(context).nextStartedActivity)
    }

    @Test
    fun `import hand-off writes a named ovpn file and opens the engine`() {
        installEngine()
        val config = "remote vpn.example.com 1194\nclient\n"
        assertTrue(EngineBridge.launchImport(context, "Home/Office #1", config))
        val sent = Shadows.shadowOf(context).nextStartedActivity
        assertEquals(EngineBridge.OVPN_MIME, sent.type)
        assertEquals(EngineBridge.ICS_PACKAGE, sent.`package`)
        val data = sent.data
        assertTrue(data != null && data.toString().endsWith(".ovpn"))
        // Name is sanitized into the file stem (spaces kept) so the engine's
        // suggested profile name matches what ConnectVPN targets. FileProvider
        // URIs are virtual — resolve the real file under the cache dir.
        assertEquals("Home_Office _1.ovpn", data!!.lastPathSegment)
        val file = java.io.File(context.cacheDir, "engine-handoff/" + data.lastPathSegment)
        assertEquals(config, file.readText())
    }

    @Test
    fun `disconnect targets the engine disconnect activity and is a no-op without it`() {
        EngineBridge.launchDisconnect(context) // no engine: must not throw or start anything
        assertNull(Shadows.shadowOf(context).nextStartedActivity)

        installEngine()
        EngineBridge.launchDisconnect(context)
        val sent = Shadows.shadowOf(context).nextStartedActivity
        assertEquals(EngineBridge.ICS_DISCONNECT_CLASS, sent.component?.className)
        assertEquals(EngineBridge.ICS_PACKAGE, sent.component?.packageName)
    }
}
