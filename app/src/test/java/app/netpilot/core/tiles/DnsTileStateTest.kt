package app.netpilot.core.tiles

import app.netpilot.core.model.DnsMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Quick Settings tile is boundaryless; its STATE logic is pinned here. */
class DnsTileStateTest {

    @Test
    fun `strict dns with a profile shows active with the profile name`() {
        val m = DnsTileState.compute(true, DnsMode.CUSTOM, "Cloudflare")
        assertTrue(m.active)
        assertEquals("Cloudflare", m.subtitle)
        assertTrue(m.clickable)
    }

    @Test
    fun `automatic mode is not active but clickable`() {
        val m = DnsTileState.compute(true, DnsMode.AUTOMATIC, null)
        assertFalse(m.active)
        assertNull(m.subtitle)
        assertTrue(m.clickable)
    }

    @Test
    fun `off mode is inactive and clickable`() {
        val m = DnsTileState.compute(true, DnsMode.OFF, null)
        assertFalse(m.active)
        assertTrue(m.clickable)
    }

    @Test
    fun `without the grant the tile is not clickable`() {
        val m = DnsTileState.compute(false, DnsMode.CUSTOM, "Cloudflare")
        assertFalse(m.active)
        assertFalse(m.clickable)
    }
}
