package app.netpilot.core.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pre-parse structural gate for WireGuard .conf imports. */
class WgConfigCheckTest {

    private val valid = """
        [Interface]
        PrivateKey = ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuv1234567890A=
        Address = 10.2.0.2/32
        DNS = 1.1.1.1

        [Peer]
        PublicKey = ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ=
        AllowedIPs = 0.0.0.0/0, ::/0
        Endpoint = 169.197.142.29:51820
    """.trimIndent()

    @Test
    fun `a complete config is plausible`() {
        assertTrue(WgConfigCheck.isPlausible(valid))
    }

    @Test
    fun `missing peer section is rejected`() {
        val noPeer = valid.substringBefore("[Peer]")
        assertFalse(WgConfigCheck.isPlausible(noPeer))
    }

    @Test
    fun `missing private key is rejected`() {
        val noKey = valid.replace(Regex("(?im)^\\s*PrivateKey\\s*=.*$"), "")
        assertFalse(WgConfigCheck.isPlausible(noKey))
    }

    @Test
    fun `openvpn config text is rejected`() {
        assertFalse(WgConfigCheck.isPlausible("remote vpn.example.com 1194\nclient\n<ca>\n</ca>\n"))
    }

    @Test
    fun `endpoint is extracted for the display fields`() {
        assertEquals("169.197.142.29" to 51820, WgConfigCheck.endpointOf(valid))
        assertNull(WgConfigCheck.endpointOf("[Interface]\nPrivateKey = ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuv1234567890A=\n"))
    }
}
