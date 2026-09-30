package app.netpilot.core.status

import android.app.Notification
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.netpilot.R
import app.netpilot.core.dns.DnsProfileRepository
import app.netpilot.core.dns.PrivateDnsManager
import app.netpilot.core.vpn.VpnSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

/**
 * The unified status notification contract: one notification that names every
 * active protection (VPN profile, Secure-DNS tunnel, strict Private DNS) and
 * offers exactly one "Turn off" action that stops them all.
 */
@RunWith(RobolectricTestRunner::class)
class StatusNotificationsTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() {
        VpnSessionState.markPlatformStopped(context)
        Shadows.shadowOf(context).grantPermissions(PrivateDnsManager.PERMISSION)
        PrivateDnsManager.disable(context)
    }

    @Test
    fun `empty snapshot produces no notification`() {
        val snap = StatusNotifications.snapshot(context)
        assertNull(snap.vpnName)
        assertNull(snap.secureDnsHost)
        assertNull(snap.privateDnsName)
        assertTrue(snap.isEmpty)
        assertNull(StatusNotifications.build(context, snap))
    }

    @Test
    fun `vpn-only snapshot names the profile and offers one turn-off action`() {
        VpnSessionState.markPlatformStarted(context, "p1", "Office VPN")
        val snap = StatusNotifications.snapshot(context)
        assertEquals("Office VPN", snap.vpnName)
        val notif: Notification = StatusNotifications.build(context, snap)!!
        assertEquals(
            context.getString(R.string.notif_vpn_active, "Office VPN"),
            notif.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
        assertEquals(1, notif.actions.size)
        assertEquals(
            context.getString(R.string.notif_action_turn_off),
            notif.actions[0].title.toString(),
        )
    }

    @Test
    fun `vpn and private dns together combine into one notification with one action`() {
        VpnSessionState.markPlatformStarted(context, "p1", "Office VPN")
        val profile = DnsProfileRepository(context).add("Cloudflare", "one.one.one.one")
        DnsProfileRepository(context).setActive(profile.id)
        PrivateDnsManager.applyProfile(context, "one.one.one.one")

        val snap = StatusNotifications.snapshot(context)
        assertEquals("Office VPN", snap.vpnName)
        assertEquals("Cloudflare", snap.privateDnsName)

        val notif = StatusNotifications.build(context, snap)!!
        assertEquals(
            context.getString(R.string.notif_both_title),
            notif.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
        assertEquals(
            context.getString(R.string.notif_both_text, "Office VPN", "Cloudflare"),
            notif.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertEquals("exactly ONE turn-off button even with both protections on", 1, notif.actions.size)
    }

    @Test
    fun `strict private dns names the saved profile`() {
        val profile = DnsProfileRepository(context).add("Quad9", "dns.quad9.net")
        DnsProfileRepository(context).setActive(profile.id)
        PrivateDnsManager.applyProfile(context, "dns.quad9.net")
        val snap = StatusNotifications.snapshot(context)
        assertEquals("Quad9", snap.privateDnsName)
        val notif = StatusNotifications.build(context, snap)!!
        assertEquals(
            context.getString(R.string.notif_dns_active, "Quad9"),
            notif.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
    }
}
