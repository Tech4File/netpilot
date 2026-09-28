package app.netpilot.androidtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.netpilot.core.model.VpnProfile
import app.netpilot.core.model.VpnType
import app.netpilot.core.vpn.OvpnConfigParser
import app.netpilot.core.vpn.PlatformVpnController
import app.netpilot.core.vpn.VpnProfileRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** VPN profile storage + parser behaviour on a real/emulated Android system. */
@RunWith(AndroidJUnit4::class)
class VpnProfileStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun profileCrudRoundTrip() {
        val repo = VpnProfileRepository(context)
        val added = repo.add(
            VpnProfile(
                id = "", name = "CI VPN", type = VpnType.PLATFORM_IKEV2,
                serverHost = "gw.example.org", serverPort = 500, username = "ci", password = "pw",
            ),
        )
        assertEquals("gw.example.org", repo.get(added.id)?.serverHost)
        repo.update(added.copy(name = "CI VPN v2"))
        assertEquals("CI VPN v2", repo.get(added.id)?.name)
        assertTrue(repo.delete(added.id))
    }

    @Test
    fun platformVpnSupportGateMatchesRuntime() {
        // minSdk is 28; the native IKEv2 platform API needs API 30+.
        assertEquals(
            android.os.Build.VERSION.SDK_INT >= 30,
            PlatformVpnController.isSupported(),
        )
    }

    @Test
    fun ovpnParserHandlesRealisticConfig() {
        val config = OvpnConfigParser.parse(
            """
            ##############################################
            # Exported by NetPilot CI
            ##############################################
            client
            dev tun
            proto udp
            remote ci.example.net 1194
            resolv-retry infinite
            nobind
            persist-key
            persist-tun
            remote-cert-tls server
            verify-x509-name server name
            auth SHA256
            auth-nocache
            verb 3

            <ca>
            -----BEGIN CERTIFICATE-----
            AAAA
            -----END CERTIFICATE-----
            </ca>
            """.trimIndent(),
        )
        assertTrue(config.isUsableForProfile())
        assertEquals("ci.example.net:1194", config.primaryEndpoint())
        assertTrue(config.caInline)
        assertFalse(config.tlsCrypt)
    }
}
