package app.netpilot.androidtest

import android.view.KeyEvent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isFocused
import androidx.test.espresso.matcher.ViewMatchers.withId
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
        onView(withId(R.id.hero_status)).check(matches(isDisplayed()))
        onView(withId(R.id.card_dns_quick)).check(matches(isDisplayed()))
        onView(withId(R.id.network_card)).check(matches(isDisplayed()))
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
        val focusable = intArrayOf(R.id.hero_status, R.id.card_dns_quick, R.id.card_vpn_quick)
        assertTrue(focusable.all { id ->
            rule.scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(id).isFocusable
            }
        })
    }

    @Test
    fun dpadDownMovesFocusBetweenQuickCards() {
        // Focus the DNS quick card, then walk down with the D-pad — the VPN card
        // must receive focus (10-foot navigation contract).
        onView(withId(R.id.card_dns_quick)).perform(click())
        // click navigated to the DNS tab; come back and use pure focus instead
        onView(withId(R.id.nav_dashboard)).perform(click())

        rule.scenario.onActivity { activity ->
            activity.findViewById<android.view.View>(R.id.card_dns_quick).requestFocus()
        }
        onView(withId(R.id.card_dns_quick)).perform(pressKey(KeyEvent.KEYCODE_DPAD_DOWN))
        rule.scenario.onActivity { activity ->
            assertTrue(activity.findViewById<android.view.View>(R.id.card_vpn_quick).isFocused)
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
