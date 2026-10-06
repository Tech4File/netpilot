package app.netpilot.core.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The tunnel event hub is the mechanism that keeps every visible surface
 * (dashboard hero, VPN switch, profile rows, tile, notification) converging
 * on the real tunnel state. These tests pin its contract: registration,
 * single-fire fan-out, removal, and resilience to a throwing listener.
 */
class TunnelEventsTest {

    @Before
    fun reset() {
        TunnelEvents.clearForTest()
    }

    @Test
    fun `listener receives one notification per notify`() {
        var count = 0
        TunnelEvents.addListener { count++ }
        TunnelEvents.notifyChanged()
        assertEquals(1, count)
        TunnelEvents.notifyChanged()
        assertEquals(2, count)
    }

    @Test
    fun `all registered listeners fan out`() {
        var a = 0
        var b = 0
        TunnelEvents.addListener { a++ }
        TunnelEvents.addListener { b++ }
        TunnelEvents.notifyChanged()
        assertEquals(1, a)
        assertEquals(1, b)
    }

    @Test
    fun `removed listener stops receiving`() {
        var count = 0
        val l: () -> Unit = { count++ }
        TunnelEvents.addListener(l)
        TunnelEvents.notifyChanged()
        TunnelEvents.removeListener(l)
        TunnelEvents.notifyChanged()
        assertEquals(1, count)
    }

    @Test
    fun `a throwing listener never blocks the others`() {
        var after = 0
        TunnelEvents.addListener { error("boom") }
        TunnelEvents.addListener { after++ }
        TunnelEvents.notifyChanged()
        assertTrue(after == 1)
    }

    @Test
    fun `notify with no listeners is a no-op`() {
        TunnelEvents.notifyChanged()
        assertTrue(true)
    }
}
