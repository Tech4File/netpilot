package app.netpilot.core.access

import app.netpilot.core.access.AccessGuard.AccessStatus
import app.netpilot.core.access.AccessGuard.Inputs
import org.junit.Assert.assertEquals
import org.junit.Test

/** The TV power-cycle truth table, pinned. */
class AccessGuardTest {

    private fun inputs(
        adb: Boolean = false,
        shizuku: Boolean = false,
        shown: Boolean = false,
        held: Boolean = false,
    ) = Inputs(adbGrant = adb, shizukuUsable = shizuku, setupGuideShown = shown, accessHeldBefore = held)

    @Test
    fun `adb grant present is ready — restart or not`() {
        // The pm grant survives TV power off/on: this is the normal state
        // after any restart on the recommended setup.
        assertEquals(AccessStatus.READY, AccessGuard.evaluate(inputs(adb = true)))
    }

    @Test
    fun `no grant but shizuku up is usable`() {
        assertEquals(AccessStatus.SHIZUKU_READY, AccessGuard.evaluate(inputs(shizuku = true)))
    }

    @Test
    fun `access held before and now gone is regrant`() {
        // TV restarted with a Shizuku-only setup (Shizuku is down), or the
        // grant vanished via clear-data/factory-reset: re-prompt.
        assertEquals(AccessStatus.REGRANT, AccessGuard.evaluate(inputs(held = true)))
    }

    @Test
    fun `guide shown but never granted still re-prompts`() {
        assertEquals(AccessStatus.REGRANT, AccessGuard.evaluate(inputs(shown = true)))
    }

    @Test
    fun `true first launch is onboard`() {
        assertEquals(AccessStatus.ONBOARD, AccessGuard.evaluate(inputs()))
    }

    @Test
    fun `adb grant wins even when shizuku is also up`() {
        assertEquals(AccessStatus.READY, AccessGuard.evaluate(inputs(adb = true, shizuku = true)))
    }
}
