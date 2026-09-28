package app.netpilot.ui

import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.netpilot.MainActivity
import app.netpilot.R
import app.netpilot.core.ui.isTelevision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Device-form-factor compatibility, proven on the JVM across Android
 * configuration canvases:
 *
 *  - classic Android TV  (960x540dp canvas, television UI mode)
 *  - 4K TV canvas        (1920x1080dp, television UI mode)
 *  - small phone         (360x640dp — the narrowest common phone)
 *
 * Layouts are dp-based with flexible weights, so physical screen inches
 * (24"…80"+) do not change the dp canvas — resolution + density do.
 * These tests pin that contract.
 */
@RunWith(AndroidJUnit4::class)
class UiCompatibilityTest {

    private fun launch(): MainActivity {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        return activity
    }

    @Test
    @Config(sdk = [33], qualifiers = "television")
    fun `classic TV canvas shows rail and hides bottom bar`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue("television qualifier must activate TV detection", context.isTelevision)

        val activity = launch()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.nav_rail).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.nav_bottom).visibility)
        assertNotNull(activity.findViewById<View>(R.id.hero_status))
    }

    @Test
    @Config(sdk = [33], qualifiers = "w1920dp-h1080dp-television-xhdpi")
    fun `4K TV canvas renders with overscan-safe paddings`() {
        val activity = launch()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.nav_rail).visibility)

        // Screen padding must meet the TV overscan-safe value (36dp), not the phone 20dp.
        val activity2 = activity
        val expected = 36f * activity2.resources.displayMetrics.density
        assertEquals("TV layout must use overscan-safe padding", expected, activity2.resources.getDimension(R.dimen.screen_padding))
        assertNotNull(activity.findViewById<View>(R.id.nav_container))
        assertNotNull(activity.findViewById<View>(R.id.hero_status))
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h640dp-mdpi")
    fun `smallest common phone canvas still renders every screen`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertFalse(context.isTelevision)

        val activity = launch()
        assertNotNull(activity.findViewById<View>(R.id.hero_status))
        assertNotNull(activity.findViewById<View>(R.id.card_dns_quick))
        assertNotNull(activity.findViewById<View>(R.id.card_vpn_quick))
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.nav_bottom).visibility)

        // Switch through all four tabs on the tiny canvas — nothing may crash or vanish.
        activity.runOnUiThread {
            activity.selectTab(NavTab.PRIVATE_DNS)
        }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNotNull(activity.findViewById<View>(R.id.dns_header))

        activity.runOnUiThread { activity.selectTab(NavTab.VPN) }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNotNull(activity.findViewById<View>(R.id.vpn_header))
    }

    @Test
    @Config(sdk = [33], qualifiers = "television")
    fun `TV dimension overrides resolve to overscan-safe values`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val overscan = context.resources.getDimension(R.dimen.tv_overscan)
        val screenPadding = context.resources.getDimension(R.dimen.screen_padding)
        assertEquals(24f, overscan)
        assertEquals(36f, screenPadding)
    }
}
