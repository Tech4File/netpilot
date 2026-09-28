package app.netpilot

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import app.netpilot.core.prefs.AppPreferences

/**
 * Application entry point.
 *
 * NetPilot is intentionally tiny and battery-friendly:
 *  - no analytics, no background services, no polling;
 *  - all state changes are observed event-driven (ContentObserver / NetworkCallback).
 */
class NetPilotApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppPreferences(this).theme.nightMode)
    }
}
