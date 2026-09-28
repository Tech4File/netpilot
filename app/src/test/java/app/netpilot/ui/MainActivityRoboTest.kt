package app.netpilot.ui

import android.app.Application
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.netpilot.MainActivity
import app.netpilot.R
import app.netpilot.core.dns.DnsProfileRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
class MainActivityRoboTest {

    private fun launch(): MainActivity {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        return activity
    }

    @Test
    fun `launches with dashboard and phone navigation`() {
        val activity = launch()
        assertNotNull(activity.findViewById<View>(R.id.hero_status))
        assertNotNull(activity.findViewById<View>(R.id.nav_bottom))
        assertEquals(View.GONE, activity.findViewById<View>(R.id.nav_rail).visibility)
    }

    @Test
    fun `switching tabs swaps fragments`() {
        val activity = launch()
        activity.runOnUiThread { activity.selectTab(NavTab.VPN) }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNotNull(activity.findViewById<View>(R.id.vpn_header))

        activity.runOnUiThread { activity.selectTab(NavTab.PRIVATE_DNS) }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNotNull(activity.findViewById<View>(R.id.dns_header))

        activity.runOnUiThread { activity.selectTab(NavTab.SETTINGS) }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNotNull(activity.findViewById<View>(R.id.row_theme))
    }

    private fun launchOnDnsTab(): MainActivity {
        val activity = launch()
        activity.runOnUiThread { activity.selectTab(NavTab.PRIVATE_DNS) }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        return activity
    }

    @Test
    fun `dns profile dialog rejects an IP address`() {
        val activity = launchOnDnsTab()
        val repo = DnsProfileRepository(ApplicationProvider.getApplicationContext<Application>())
        activity.runOnUiThread {
            val fragment = activity.supportFragmentManager
                .findFragmentById(R.id.nav_container) as? app.netpilot.ui.dns.PrivateDnsFragment
            assertNotNull(fragment)
            fragment!!.showProfileDialog(null)
        }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        val name = dialog!!.findViewById<android.widget.EditText>(R.id.et_name)!!
        val host = dialog.findViewById<android.widget.EditText>(R.id.et_host)!!
        activity.runOnUiThread {
            name.setText("Bad IP")
            host.setText("8.8.8.8")
            dialog.findViewById<android.widget.Button>(android.R.id.button1)?.performClick()
                ?: error("positive button missing")
        }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue("invalid host must be rejected", host.error != null || repo.list().isEmpty())
        assertEquals(0, repo.list().size)
    }

    @Test
    fun `dns profile dialog accepts a valid provider`() {
        val activity = launchOnDnsTab()
        val repo = DnsProfileRepository(ApplicationProvider.getApplicationContext<Application>())
        activity.runOnUiThread {
            (activity.supportFragmentManager.findFragmentById(R.id.nav_container) as app.netpilot.ui.dns.PrivateDnsFragment)
                .showProfileDialog(null)
        }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog()!!
        activity.runOnUiThread {
            dialog.findViewById<android.widget.EditText>(R.id.et_name)!!.setText("Google")
            dialog.findViewById<android.widget.EditText>(R.id.et_host)!!.setText("DNS.Google")
            dialog.findViewById<android.widget.Button>(android.R.id.button1)!!.performClick()
        }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, repo.list().size)
        assertEquals("dns.google", repo.list().first().hostname) // normalized to lower-case
        assertNull(dialog.findViewById<android.widget.EditText>(R.id.et_host)?.error)
    }
}
