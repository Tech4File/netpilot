package app.netpilot.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** The default VPN type follows the device: platform IKEv2 from 11, embedded WireGuard below. */
class VpnTypeTest {

    @Test
    fun `android 9 and 10 default to the embedded wireguard engine`() {
        assertEquals(VpnType.WIREGUARD, VpnType.defaultFor(28))
        assertEquals(VpnType.WIREGUARD, VpnType.defaultFor(29))
    }

    @Test
    fun `android 11 and above default to platform ikev2`() {
        assertEquals(VpnType.PLATFORM_IKEV2, VpnType.defaultFor(30))
        assertEquals(VpnType.PLATFORM_IKEV2, VpnType.defaultFor(34))
        assertEquals(VpnType.PLATFORM_IKEV2, VpnType.defaultFor(35))
    }
}
