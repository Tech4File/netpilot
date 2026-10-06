package app.netpilot

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import app.netpilot.core.prefs.AppPreferences
import app.netpilot.core.vpn.OvpnCoreInstall

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
        // Embedded OpenVPN engine: wire the data-channel factory at process
        // start so the UI's connect decision sees it. Installing it only
        // inside the VPN service was a chicken-and-egg deadlock — the service
        // is started THROUGH the factory, so the UI always saw null and fell
        // back to the engine-app install prompt. Absent native library ->
        // still a no-op and the honest bridge guidance stays.
        OvpnCoreInstall.install()
    }
}
