package app.netpilot.core.vpn

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.netpilot.core.model.VpnAuthType
import app.netpilot.core.model.VpnProfile
import app.netpilot.core.model.VpnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Embedded WireGuard engine: runtime flags share the authority of the classic
 * service flags, and .conf payloads round-trip through the on-device store
 * (they contain keys, so exports must never ship them).
 */
@RunWith(RobolectricTestRunner::class)
class WireGuardStorageTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var repo: VpnProfileRepository

    @Before
    fun setUp() {
        repo = VpnProfileRepository(context)
        WireGuardRuntime.markStopped()
    }

    @Test
    fun `runtime flags follow the lifecycle`() {
        assertFalse(WireGuardRuntime.isRunning)
        WireGuardRuntime.markStarted("wg1", "Home WG")
        assertTrue(WireGuardRuntime.isRunning)
        assertEquals("wg1", WireGuardRuntime.runningProfileId)
        assertEquals("Home WG", WireGuardRuntime.runningName)
        WireGuardRuntime.markStopped()
        assertFalse(WireGuardRuntime.isRunning)
    }

    @Test
    fun `wg config round-trips through the store`() {
        val conf = "[Interface]\nPrivateKey = ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuv1234567890A=\n[Peer]\nEndpoint = h:1\n"
        val saved = repo.add(
            VpnProfile(
                id = "", name = "Home WG", type = VpnType.WIREGUARD,
                serverHost = "h", serverPort = 51820, authType = VpnAuthType.PSK,
                wgConfig = conf,
            ),
        )
        assertEquals(conf, repo.get(saved.id)?.wgConfig)
    }

    @Test
    fun `wireguard is a distinct profile type`() {
        assertEquals(3, VpnType.entries.size)
        assertTrue(VpnType.entries.any { it == VpnType.WIREGUARD })
    }
}
