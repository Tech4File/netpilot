package app.netpilot.core.vpn

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins the ownership model for platform-managed IKEv2 sessions: the OS daemon
 * runs the tunnel with no app service to observe, so VpnSessionState's record
 * is the only proof that a tunnel belongs to NetPilot (v2.0.3 field bug —
 * NetPilot's own IKEv2 tunnel was misclassified as a foreign VPN and the UI
 * locked itself).
 */
@RunWith(RobolectricTestRunner::class)
class VpnSessionStateTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() {
        VpnSessionState.markPlatformStopped(context)
    }

    @Test
    fun `platform session lifecycle records and clears`() {
        assertFalse(VpnSessionState.platformSessionActive(context))
        VpnSessionState.markPlatformStarted(context, "p1", "Office VPN")
        assertTrue(VpnSessionState.platformSessionActive(context))
        assertEquals("p1", VpnSessionState.platformProfileId(context))
        assertEquals("Office VPN", VpnSessionState.platformProfileName(context))
        VpnSessionState.markPlatformStopped(context)
        assertFalse(VpnSessionState.platformSessionActive(context))
    }

    @Test
    fun `reconcile drops the persisted session when no VPN transport is present`() {
        VpnSessionState.markPlatformStarted(context, "p1", "Office VPN")
        VpnSessionState.simulateRestartForTest() // process died; marker persists
        assertTrue("a surviving platform tunnel must be re-recognised from persistence", VpnSessionState.platformSessionActive(context))
        VpnSessionState.reconcile(context, vpnTransportPresent = false)
        assertFalse("stale session must not survive a tun-less device", VpnSessionState.platformSessionActive(context))
    }

    @Test
    fun `a live in-memory session survives reconcile without a transport - connect window guard`() {
        VpnSessionState.markPlatformStarted(context, "p1", "Office VPN")
        // During connect the flag is set before the TUN appears; reconcile must
        // never wipe a live session or the ownership proof would race away.
        VpnSessionState.reconcile(context, vpnTransportPresent = false)
        assertTrue(VpnSessionState.platformSessionActive(context))
    }

    @Test
    fun `reconcile keeps the session while a VPN transport is present`() {
        VpnSessionState.markPlatformStarted(context, "p1", "Office VPN")
        VpnSessionState.reconcile(context, vpnTransportPresent = true)
        assertTrue(VpnSessionState.platformSessionActive(context))
    }

    @Test
    fun `anySessionClaimed mirrors the platform session`() {
        assertFalse(VpnSessionState.anySessionClaimed(context))
        VpnSessionState.markPlatformStarted(context, "p1", "Office VPN")
        assertTrue(VpnSessionState.anySessionClaimed(context))
        VpnSessionState.markPlatformStopped(context)
        assertFalse(VpnSessionState.anySessionClaimed(context))
    }

    @Test
    fun `clearIfExpired removes an abandoned platform claim`() {
        VpnSessionState.markPlatformStarted(context, "p1", "Office VPN")
        VpnSessionState.simulateRestartForTest()
        // Backdate the persisted claim beyond the grace window.
        context.getSharedPreferences("netpilot_vpn_session", Context.MODE_PRIVATE)
            .edit()
            .putLong("platform_claimed_at", System.currentTimeMillis() - 60_000)
            .commit()
        val cleared = VpnSessionState.clearIfExpired(context, vpnTransportPresent = false)
        assertTrue("stale claim must be cleared", cleared)
        assertFalse(VpnSessionState.platformSessionActive(context))
    }

    @Test
    fun `clearIfExpired keeps a fresh claim during the connect window`() {
        VpnSessionState.markPlatformStarted(context, "p1", "Office VPN")
        val cleared = VpnSessionState.clearIfExpired(context, vpnTransportPresent = false)
        assertFalse("a fresh claim is the connect window - keep it", cleared)
        assertTrue(VpnSessionState.platformSessionActive(context))
    }

    @Test
    fun `clearIfExpired short-circuits while a transport is present`() {
        VpnSessionState.markPlatformStarted(context, "p1", "Office VPN")
        assertFalse(VpnSessionState.clearIfExpired(context, vpnTransportPresent = true))
        assertTrue(VpnSessionState.platformSessionActive(context))
    }
}
