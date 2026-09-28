package app.netpilot.androidtest

import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.netpilot.core.dns.DnsProfileRepository
import app.netpilot.core.dns.PrivateDnsManager
import app.netpilot.core.model.DnsMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end: repository → PrivateDnsManager → system Settings.Global.
 * Runs only when WRITE_SECURE_SETTINGS has been granted (the CI "granted" leg
 * grants it via adb before the test run); skipped gracefully elsewhere.
 */
@RunWith(AndroidJUnit4::class)
class DnsSystemApplyTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun activatingAProfileWritesTheSystemSetting() {
        assumeTrue(PrivateDnsManager.hasWritePermission(context))

        val repo = DnsProfileRepository(context)
        val profile = repo.add("CI Google", "dns.google")
        repo.setActive(profile.id)

        assertTrue(PrivateDnsManager.applyProfile(context, profile.hostname))

        assertEquals(
            DnsMode.CUSTOM.settingValue,
            Settings.Global.getString(context.contentResolver, PrivateDnsManager.KEY_MODE),
        )
        assertEquals(
            profile.hostname,
            Settings.Global.getString(context.contentResolver, PrivateDnsManager.KEY_SPECIFIER),
        )

        // Single-active: switching providers replaces the specifier.
        val second = repo.add("CI Cloudflare", "one.one.one.one")
        repo.setActive(second.id)
        assertTrue(PrivateDnsManager.applyProfile(context, second.hostname))
        assertEquals(
            second.hostname,
            Settings.Global.getString(context.contentResolver, PrivateDnsManager.KEY_SPECIFIER),
        )

        // Disable restores off.
        assertTrue(PrivateDnsManager.disable(context))
        assertEquals(
            DnsMode.OFF.settingValue,
            Settings.Global.getString(context.contentResolver, PrivateDnsManager.KEY_MODE),
        )

        repo.delete(profile.id)
        repo.delete(second.id)
    }

    @Test
    fun observerFiresWhenSettingChanges() {
        assumeTrue(PrivateDnsManager.hasWritePermission(context))
        var fired = false
        val observer = PrivateDnsManager.observe(context) { fired = true }
        try {
            PrivateDnsManager.setAutomatic(context)
            val deadline = System.currentTimeMillis() + 5_000
            while (!fired && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }
            assertTrue("ContentObserver should notify on private_dns_mode change", fired)
        } finally {
            PrivateDnsManager.stopObserving(context, observer)
            PrivateDnsManager.disable(context)
        }
    }
}
