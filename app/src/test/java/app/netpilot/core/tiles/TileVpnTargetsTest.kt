package app.netpilot.core.tiles

import app.netpilot.core.model.VpnProfile
import app.netpilot.core.model.VpnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TileVpnTargetsTest {

    private fun wg(id: String, name: String, conf: String? = "[Interface]\nPrivateKey = ${"a".repeat(44)}\n\n[Peer]\nEndpoint = 10.0.0.1:51820\nPublicKey = ${"b".repeat(44)}\nAllowedIPs = 0.0.0.0/0\n") =
        VpnProfile(id = id, name = name, type = VpnType.WIREGUARD, serverHost = "wg", wgConfig = conf)

    private fun ovpn(id: String, name: String, conf: String? = "client\ndev tun\n") =
        VpnProfile(id = id, name = name, type = VpnType.OPENVPN, serverHost = "ovpn", ovpnConfig = conf)

    private fun ike(id: String) =
        VpnProfile(id = id, name = "ike", type = VpnType.PLATFORM_IKEV2, serverHost = "vpn")

    @Test
    fun `last used usable profile wins`() {
        val profiles = listOf(wg("a", "A"), ovpn("b", "B"))
        assertEquals("b", TileVpnTargets.pick(profiles, "b")?.id)
    }

    @Test
    fun `falls back to first usable when last used is gone`() {
        val profiles = listOf(wg("a", "A"), ovpn("b", "B"))
        assertEquals("a", TileVpnTargets.pick(profiles, "deleted-id")?.id)
    }

    @Test
    fun `wireguard without a plausible config is not usable`() {
        val p = wg("x", "X", conf = "garbage")
        assertFalse(TileVpnTargets.usable(p))
        assertNull(TileVpnTargets.pick(listOf(p), "x"))
    }

    @Test
    fun `openvpn without config is not usable`() {
        val p = ovpn("x", "X", conf = null)
        assertFalse(TileVpnTargets.usable(p))
    }

    @Test
    fun `platform ikev2 is never tile-drivable`() {
        assertFalse(TileVpnTargets.usable(ike("i")))
        assertNull(TileVpnTargets.pick(listOf(ike("i")), "i"))
    }

    @Test
    fun `nothing usable yields null`() {
        assertNull(TileVpnTargets.pick(emptyList(), null))
    }
}
