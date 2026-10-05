package app.netpilot.core.vpn

import app.netpilot.core.vpn.WireGuardSplitTunnel.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WireGuardSplitTunnelTest {

    private val base = """
        [Interface]
        PrivateKey = ABCdef123=
        Address = 10.2.0.2/32

        [Peer]
        PublicKey = XYz456=
        AllowedIPs = 0.0.0.0/0
        Endpoint = vpn.example.com:51820
    """.trimIndent() + "\n"

    @Test
    fun `no lines means all apps`() {
        val s = WireGuardSplitTunnel.read(base)
        assertEquals(Mode.ALL, s.mode)
        assertTrue(s.packages.isEmpty())
    }

    @Test
    fun `apply exclude inserts the line into the interface section`() {
        val out = WireGuardSplitTunnel.apply(base, WireGuardSplitTunnel.Selection(Mode.EXCLUDE, listOf("com.game.one", "com.game.two")))!!
        val s = WireGuardSplitTunnel.read(out)
        assertEquals(Mode.EXCLUDE, s.mode)
        assertEquals(listOf("com.game.one", "com.game.two"), s.packages)
        // Must sit inside [Interface] — before [Peer].
        assertTrue(out.indexOf("ExcludedApplications") < out.indexOf("[Peer]"))
    }

    @Test
    fun `apply replaces an existing line instead of duplicating`() {
        val once = WireGuardSplitTunnel.apply(base, WireGuardSplitTunnel.Selection(Mode.EXCLUDE, listOf("com.a")))!!
        val twice = WireGuardSplitTunnel.apply(once, WireGuardSplitTunnel.Selection(Mode.EXCLUDE, listOf("com.b", "com.c")))!!
        assertEquals(1, twice.lines().count { it.startsWith("ExcludedApplications") })
        assertEquals(listOf("com.b", "com.c"), WireGuardSplitTunnel.read(twice).packages)
    }

    @Test
    fun `all mode clears the lines`() {
        val excluded = WireGuardSplitTunnel.apply(base, WireGuardSplitTunnel.Selection(Mode.EXCLUDE, listOf("com.a")))!!
        val all = WireGuardSplitTunnel.apply(excluded, WireGuardSplitTunnel.Selection(Mode.ALL, emptyList()))!!
        assertEquals(Mode.ALL, WireGuardSplitTunnel.read(all).mode)
        assertFalse(all.contains("Applications"))
    }

    @Test
    fun `include and exclude are mutually exclusive by construction`() {
        // apply() writes only ONE kind of line, and read() repairs a
        // contradictory config by falling back to ALL.
        val s = WireGuardSplitTunnel.read(
            base.replace("Address = 10.2.0.2/32", "Address = 10.2.0.2/32\nIncludedApplications = com.x\nExcludedApplications = com.y"),
        )
        assertEquals(Mode.ALL, s.mode)
    }

    @Test
    fun `invalid package names and empty lists are rejected`() {
        assertNull(WireGuardSplitTunnel.apply(base, WireGuardSplitTunnel.Selection(Mode.INCLUDE, emptyList())))
        assertNull(WireGuardSplitTunnel.apply(base, WireGuardSplitTunnel.Selection(Mode.EXCLUDE, listOf("not a package"))))
        assertNull(WireGuardSplitTunnel.apply("no interface here", WireGuardSplitTunnel.Selection(Mode.EXCLUDE, listOf("com.a"))))
    }
}
