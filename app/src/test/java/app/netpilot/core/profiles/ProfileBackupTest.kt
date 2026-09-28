package app.netpilot.core.profiles

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.netpilot.core.dns.DnsProfileRepository
import app.netpilot.core.model.VpnProfile
import app.netpilot.core.vpn.VpnProfileRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileBackupTest {

    private lateinit var dns: DnsProfileRepository
    private lateinit var vpn: VpnProfileRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        dns = DnsProfileRepository(context)
        vpn = VpnProfileRepository(context)
    }

    @Test
    fun `bundle export contains both repositories`() {
        dns.add("Google", "dns.google")
        vpn.add(VpnProfile(id = "", name = "Srv", type = app.netpilot.core.model.VpnType.PLATFORM_IKEV2, serverHost = "vpn.example.com"))
        val bundle = ProfileBackup.export(dns, vpn)
        assertTrue(bundle.contains("netpilot.bundle.v1"))
        assertTrue(bundle.contains("dns.google"))
        assertTrue(bundle.contains("vpn.example.com"))
    }

    @Test
    fun `bundle import restores into fresh repositories`() {
        dns.add("Google", "dns.google")
        vpn.add(VpnProfile(id = "", name = "Srv", type = app.netpilot.core.model.VpnType.PLATFORM_IKEV2, serverHost = "vpn.example.com"))
        val bundle = ProfileBackup.export(dns, vpn)

        // Simulate a fresh device: empty profile storage.
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("netpilot_profiles", 0).edit().clear().commit()
        val dns2 = DnsProfileRepository(context)
        val vpn2 = VpnProfileRepository(context)
        assertEquals(2, ProfileBackup.import(bundle, dns2, vpn2))
        assertEquals(1, dns2.list().size)
        assertEquals(1, vpn2.list().size)
        assertEquals(0, ProfileBackup.import(bundle, dns2, vpn2))
    }

    @Test
    fun `plain dns export can be imported through the bundle path`() {
        dns.add("Cloudflare", "one.one.one.one")
        val single = dns.exportJson()
        // The repo already holds this profile, so the import must dedupe to 0…
        assertEquals(0, ProfileBackup.import(single, dns, vpn))
        // …and a fresh store must accept exactly one.
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("netpilot_profiles", 0).edit().clear().commit()
        val dns2 = DnsProfileRepository(context)
        val vpn2 = VpnProfileRepository(context)
        assertEquals(1, ProfileBackup.import(single, dns2, vpn2))
    }

    @Test
    fun `secrets never leave the device in a backup`() {
        vpn.add(
            VpnProfile(
                id = "", name = "Srv", type = app.netpilot.core.model.VpnType.PLATFORM_IKEV2,
                serverHost = "vpn.example.com", username = "me", password = "super-secret",
            ),
        )
        val bundle = ProfileBackup.export(dns, vpn)
        assertFalse("passwords must not be exported", bundle.contains("super-secret"))
    }
}
