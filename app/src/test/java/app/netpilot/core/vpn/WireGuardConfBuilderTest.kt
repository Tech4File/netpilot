package app.netpilot.core.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Direct field input must produce the SAME artifact the .conf importer consumes. */
class WireGuardConfBuilderTest {

    private fun aValidKey(): String = WireGuardConfBuilder.generateKeyPair().first

    @Test
    fun `generates distinct valid keypairs`() {
        val (priv1, pub1) = WireGuardConfBuilder.generateKeyPair()
        val (priv2, pub2) = WireGuardConfBuilder.generateKeyPair()
        assertTrue(WireGuardConfBuilder.isValidKey(priv1))
        assertTrue(WireGuardConfBuilder.isValidKey(pub1))
        assertFalse(priv1 == priv2)
        assertFalse(pub1 == pub2)
    }

    @Test
    fun `rejects garbage keys`() {
        assertFalse(WireGuardConfBuilder.isValidKey("not-a-key"))
        assertFalse(WireGuardConfBuilder.isValidKey(""))
    }

    @Test
    fun `assembles a config that passes the import gate`() {
        val (priv, pub) = WireGuardConfBuilder.generateKeyPair()
        val conf = WireGuardConfBuilder.assembleOrNull(
            address = "10.2.0.2/32",
            clientKey = priv,
            peerKey = pub,
            endpointHost = "vpn.example.com",
            endpointPort = 51820,
            allowedIps = "0.0.0.0/0, ::/0",
        )
        assertNotNull(conf)
        assertTrue(WgConfigCheck.isPlausible(conf!!))
        assertTrue(conf.contains("Endpoint = vpn.example.com:51820"))
        assertTrue(conf.contains("PersistentKeepalive = 25"))
    }

    @Test
    fun `missing or invalid fields refuse to assemble`() {
        val (priv, pub) = WireGuardConfBuilder.generateKeyPair()
        assertNull(
            WireGuardConfBuilder.assembleOrNull(
                address = "", clientKey = priv, peerKey = pub,
                endpointHost = "vpn.example.com", endpointPort = 51820,
                allowedIps = "0.0.0.0/0",
            ),
        )
        assertNull(
            WireGuardConfBuilder.assembleOrNull(
                address = "10.2.0.2/32", clientKey = "garbage", peerKey = pub,
                endpointHost = "vpn.example.com", endpointPort = 51820,
                allowedIps = "0.0.0.0/0",
            ),
        )
        assertNull(
            WireGuardConfBuilder.assembleOrNull(
                address = "10.2.0.2/32", clientKey = priv, peerKey = pub,
                endpointHost = "vpn.example.com", endpointPort = 70000,
                allowedIps = "0.0.0.0/0",
            ),
        )
    }

    @Test
    fun `assembled config round-trips through the parser`() {
        val (priv, pub) = WireGuardConfBuilder.generateKeyPair()
        val conf = WireGuardConfBuilder.assembleOrNull(
            address = "10.2.0.2/32", clientKey = priv, peerKey = pub,
            endpointHost = "198.51.100.7", endpointPort = 443,
            allowedIps = "0.0.0.0/0, ::/0",
        )!!
        assertEquals("198.51.100.7" to 443, WgConfigCheck.endpointOf(conf))
    }
}
