package app.netpilot.core.tiles

import app.netpilot.core.model.DnsProfile
import app.netpilot.core.model.DnsMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TileDialogStateTest {

    private fun profile(id: String, name: String) =
        DnsProfile(id = id, name = name, hostname = "$id.example.net")

    @Test
    fun `custom mode with active profile renders on with rows`() {
        val m = TileDialogState.compute(
            DnsMode.CUSTOM,
            listOf(profile("a", "Home"), profile("b", "Work")),
            "b",
        )
        assertTrue(m.switchOn)
        assertTrue(m.canTurnOn)
        assertEquals("Work", m.activeProfileName)
        assertTrue(m.rows[0].name == "Home" && !m.rows[0].active)
        assertTrue(m.rows[1].name == "Work" && m.rows[1].active)
    }

    @Test
    fun `off mode renders switch off but rows visible`() {
        val m = TileDialogState.compute(DnsMode.OFF, listOf(profile("a", "Home")), "a")
        assertFalse(m.switchOn)
        assertTrue(m.canTurnOn)
        assertNull(m.activeProfileName)
        assertFalse(m.rows[0].active)
    }

    @Test
    fun `no active profile cannot switch on`() {
        val m = TileDialogState.compute(DnsMode.AUTOMATIC, listOf(profile("a", "Home")), null)
        assertFalse(m.switchOn)
        assertFalse(m.canTurnOn)
        assertNull(m.activeProfileName)
    }

    @Test
    fun `empty profile list has no rows and cannot turn on`() {
        val m = TileDialogState.compute(DnsMode.OFF, emptyList(), null)
        assertFalse(m.switchOn)
        assertFalse(m.canTurnOn)
        assertTrue(m.rows.isEmpty())
    }
}
