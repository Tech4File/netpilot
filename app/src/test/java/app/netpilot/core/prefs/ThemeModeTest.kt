package app.netpilot.core.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `parses stored names`() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.from("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeMode.from("DARK"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from("SYSTEM"))
    }

    @Test
    fun `unknown values fall back to system`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from("neon"))
    }
}
