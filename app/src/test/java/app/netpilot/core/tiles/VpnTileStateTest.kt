package app.netpilot.core.tiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnTileStateTest {

    @Test
    fun `running tunnel is active and toggles off`() {
        val m = VpnTileState.compute(running = true, hasLastProfile = true, consentGranted = true)
        assertTrue(m.active)
        assertEquals(VpnTileState.Action.TOGGLE_OFF, m.action)
    }

    @Test
    fun `last profile plus consent reconnects`() {
        val m = VpnTileState.compute(running = false, hasLastProfile = true, consentGranted = true)
        assertFalse(m.active)
        assertEquals(VpnTileState.Action.CONNECT_LAST, m.action)
    }

    @Test
    fun `no profile opens the app`() {
        val m = VpnTileState.compute(running = false, hasLastProfile = false, consentGranted = true)
        assertEquals(VpnTileState.Action.OPEN_APP, m.action)
    }

    @Test
    fun `missing consent opens the app — tile cannot show the consent dialog`() {
        val m = VpnTileState.compute(running = false, hasLastProfile = true, consentGranted = false)
        assertEquals(VpnTileState.Action.OPEN_APP, m.action)
    }

    @Test
    fun `running tunnel stays toggle-off even without a stored profile`() {
        val m = VpnTileState.compute(running = true, hasLastProfile = false, consentGranted = false)
        assertTrue(m.active)
        assertEquals(VpnTileState.Action.TOGGLE_OFF, m.action)
        assertNull(m.subtitle)
    }
}
