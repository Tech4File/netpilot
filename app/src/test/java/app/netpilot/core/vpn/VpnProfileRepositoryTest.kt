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

@RunWith(RobolectricTestRunner::class)
class VpnProfileRepositoryTest {

    private lateinit var repo: VpnProfileRepository

    private fun profile(
        name: String = "My Server",
        type: VpnType = VpnType.PLATFORM_IKEV2,
    ) = VpnProfile(
        id = "",
        name = name,
        type = type,
        serverHost = "vpn.example.com",
        serverPort = if (type == VpnType.OPENVPN) 1194 else 500,
        authType = VpnAuthType.USER_PASS,
        username = "me",
        password = "secret",
    )

    @Before
    fun setUp() {
        repo = VpnProfileRepository(ApplicationProvider.getApplicationContext<Application>())
    }

    @Test
    fun `add assigns a fresh id`() {
        val added = repo.add(profile())
        assertTrue(added.id.isNotBlank())
        assertEquals(listOf(added), repo.list())
    }

    @Test
    fun `update and delete work`() {
        val added = repo.add(profile())
        repo.update(added.copy(name = "Renamed"))
        assertEquals("Renamed", repo.get(added.id)!!.name)
        assertTrue(repo.delete(added.id))
        assertFalse(repo.delete(added.id)) // second delete reports false
        assertTrue(repo.list().isEmpty())
    }

    @Test
    fun `openvpn config payloads round-trip`() {
        val config = "client\nremote a.com 1194 udp\n<ca>\nX\n</ca>"
        val added = repo.add(profile("Tunnel").copy(type = VpnType.OPENVPN, ovpnConfig = config, ovpnSummary = "a.com:1194/udp"))
        val fresh = VpnProfileRepository(ApplicationProvider.getApplicationContext<Application>())
        val stored = fresh.get(added.id)!!
        assertEquals(config, stored.ovpnConfig)
        assertEquals("a.com:1194/udp", stored.ovpnSummary)
    }

    @Test
    fun `last connected tracking`() {
        val added = repo.add(profile())
        repo.setLastConnectedId(added.id)
        assertEquals(added.id, repo.lastConnectedId())
        repo.setLastConnectedId(null)
        assertEquals(null, repo.lastConnectedId())
    }

    @Test
    fun `export excludes secrets and import merges metadata`() {
        val added = repo.add(profile())
        val exported = repo.exportJson()
        assertFalse("secrets must not be exported", exported.contains("secret"))

        // Simulate a fresh device: empty profile storage.
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("netpilot_profiles", 0).edit().clear().commit()
        val fresh = VpnProfileRepository(context)
        assertEquals(1, fresh.importJson(exported))
        assertEquals(0, fresh.importJson(exported))
        assertEquals("vpn.example.com", fresh.list().first().serverHost)
        assertEquals(added.name, fresh.list().first().name)
    }
}
