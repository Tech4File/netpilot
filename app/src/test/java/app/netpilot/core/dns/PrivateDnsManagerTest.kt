package app.netpilot.core.dns

import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import app.netpilot.core.model.DnsMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class PrivateDnsManagerTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Shadows.shadowOf(context).grantPermissions(PrivateDnsManager.PERMISSION)
    }

    @Test
    fun `permission detection reflects grant state`() {
        assertTrue(PrivateDnsManager.hasWritePermission(context))
        Shadows.shadowOf(context).denyPermissions(PrivateDnsManager.PERMISSION)
        assertFalse(PrivateDnsManager.hasWritePermission(context))
    }

    @Test
    fun `default state is off`() {
        assertEquals(DnsMode.OFF, PrivateDnsManager.read(context).mode)
    }

    @Test
    fun `applyProfile writes strict mode and specifier`() {
        assertTrue(PrivateDnsManager.applyProfile(context, "dns.google"))
        val state = PrivateDnsManager.read(context)
        assertEquals(DnsMode.CUSTOM, state.mode)
        assertEquals("dns.google", state.specifier)
    }

    @Test
    fun `applying a second profile replaces the first - single active invariant`() {
        PrivateDnsManager.applyProfile(context, "dns.google")
        PrivateDnsManager.applyProfile(context, "one.one.one.one")
        val state = PrivateDnsManager.read(context)
        assertEquals(DnsMode.CUSTOM, state.mode)
        assertEquals("one.one.one.one", state.specifier)
    }

    @Test
    fun `setAutomatic writes opportunistic and clears specifier`() {
        PrivateDnsManager.applyProfile(context, "dns.google")
        assertTrue(PrivateDnsManager.setAutomatic(context))
        val state = PrivateDnsManager.read(context)
        assertEquals(DnsMode.AUTOMATIC, state.mode)
        assertEquals(null, state.specifier)
    }

    @Test
    fun `disable writes off and clears specifier`() {
        PrivateDnsManager.applyProfile(context, "dns.google")
        assertTrue(PrivateDnsManager.disable(context))
        val state = PrivateDnsManager.read(context)
        assertEquals(DnsMode.OFF, state.mode)
        assertEquals(null, state.specifier)
    }

    @Test
    fun `writes are refused without permission`() {
        Shadows.shadowOf(context).denyPermissions(PrivateDnsManager.PERMISSION)
        assertFalse(PrivateDnsManager.applyProfile(context, "dns.google"))
        assertEquals(DnsMode.OFF, PrivateDnsManager.read(context).mode)
    }
}
