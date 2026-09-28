package app.netpilot.androidtest

import android.view.KeyEvent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isFocused
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.Matchers.notNullValue
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.netpilot.MainActivity
import app.netpilot.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launch, navigation and D-pad focus behaviour (TV-readiness on phone hardware).
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @get:Rule
    val rule = ActivityScenarioRule(MainActivity::class.java)

    private val isTv: Boolean
        get() = InstrumentationRegistry.getInstrumentation()
            .targetContext.packageManager.hasSystemFeature("android.software.leanback")

    @Test
    fun dashboardIsShownOnLaunch() {
        // The hero card is the launch anchor and must be on screen…
        onView(withId(R.id.hero_status)).check(matches(isDisplayed()))
        // …while sections further down may sit below the fold on some devices
        // (and the one-time setup card appears on ungranted legs), pushing
        // them out of the viewport — presence is the right assertion there.
        onView(withId(R.id.card_dns_quick)).check(matches(notNullValue()))
        onView(withId(R.id.network_card)).check(matches(notNullValue()))
    }

    @Test
    fun bottomNavigationSwitchesFragments() {
        onView(withId(R.id.nav_dns)).perform(click())
        onView(withId(R.id.dns_header)).check(matches(isDisplayed()))

        onView(withId(R.id.nav_vpn)).perform(click())
        onView(withId(R.id.vpn_header)).check(matches(isDisplayed()))

        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.row_theme)).check(matches(isDisplayed()))

        onView(withId(R.id.nav_dashboard)).perform(click())
        onView(withId(R.id.hero_status)).check(matches(isDisplayed()))
    }

    @Test
    fun heroCardJumpsToPrivateDns() {
        onView(withId(R.id.hero_status)).perform(click())
        onView(withId(R.id.dns_header)).check(matches(isDisplayed()))
    }

    @Test
    fun allDashboardQuickCardsAreDpadFocusable() {
        val ids = intArrayOf(R.id.hero_status, R.id.card_dns_quick, R.id.card_vpn_quick)
        val results = mutableListOf<Boolean>()
        rule.scenario.onActivity { activity ->
            ids.forEach { id -> results += activity.findViewById<android.view.View>(id).isFocusable }
        }
        assertTrue("all quick cards must be D-pad focusable: $results", results.all { it })
    }

    @Test
    fun dpadRightMovesFocusToTheAdjacentVpnCard() {
        // Make sure the Dashboard tab is showing.
        onView(withId(R.id.nav_dashboard)).perform(click())

        // Espresso emulators boot in TOUCH MODE, where requestFocus() on a
        // normal view is silently ignored. One injected D-pad press exits
        // touch mode (standard Android behaviour) and focuses the first view.
        onView(isRoot()).perform(pressKey(KeyEvent.KEYCODE_DPAD_DOWN))

        rule.scenario.onActivity { activity ->
            activity.findViewById<android.view.View>(R.id.card_dns_quick).requestFocus()
        }
        rule.scenario.onActivity { activity ->
            assertTrue(
                "DNS card must hold focus before the move",
                activity.findViewById<android.view.View>(R.id.card_dns_quick).isFocused,
            )
        }

        // The DNS and VPN quick cards sit side by side (VPN on the right):
        // walking right with the D-pad must land on the VPN card.
        onView(withId(R.id.card_dns_quick)).perform(pressKey(KeyEvent.KEYCODE_DPAD_RIGHT))
        rule.scenario.onActivity { activity ->
            assertTrue(
                "VPN card must gain focus on D-pad right",
                activity.findViewById<android.view.View>(R.id.card_vpn_quick).isFocused,
            )
            assertFalse(activity.findViewById<android.view.View>(R.id.card_dns_quick).isFocused)
        }
    }

    @Test
    fun phoneLayoutHidesRailAndShowsBottomBar() {
        rule.scenario.onActivity { activity ->
            if (isTv) {
                assertTrue(activity.findViewById<android.view.View>(R.id.nav_rail).visibility == android.view.View.VISIBLE)
            } else {
                assertTrue(activity.findViewById<android.view.View>(R.id.nav_bottom).visibility == android.view.View.VISIBLE)
                assertTrue(activity.findViewById<android.view.View>(R.id.nav_rail).visibility == android.view.View.GONE)
            }
        }
        onView(withId(R.id.nav_bottom)).check(matches(isDisplayed()))
    }
}
