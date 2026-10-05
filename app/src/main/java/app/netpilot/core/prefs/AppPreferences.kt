package app.netpilot.core.prefs

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import app.netpilot.R

enum class ThemeMode(val nightMode: Int, val labelRes: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, R.string.theme_system),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO, R.string.theme_light),
    DARK(AppCompatDelegate.MODE_NIGHT_YES, R.string.theme_dark);

    companion object {
        fun from(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** Lightweight UI preferences (theme, dismissed hints). Lives in app-private storage. */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var theme: ThemeMode
        get() = ThemeMode.from(prefs.getString(KEY_THEME, null))
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    /** True once the first-launch permission guide was auto-shown (never auto again). */
    var setupGuideShown: Boolean
        get() = prefs.getBoolean(KEY_SETUP_GUIDE_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_GUIDE_SHOWN, value).apply()

    var dnsVpnAdvisoryDismissed: Boolean
        get() = prefs.getBoolean(KEY_ADVISORY_DISMISSED, false)
        set(value) = prefs.edit().putBoolean(KEY_ADVISORY_DISMISSED, value).apply()

    /** True once the POST_NOTIFICATIONS runtime request was made (ask once, never nag). */
    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_PERMISSION_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIF_PERMISSION_ASKED, value).apply()

    /**
     * True once ANY Private-DNS access path (adb grant or Shizuku) was usable.
     * Drives AccessGuard: if access later disappears, the user gets the
     * "access lost — re-grant" prompt, not silence (TV power-cycle case).
     */
    var accessHeld: Boolean
        get() = prefs.getBoolean(KEY_ACCESS_HELD, false)
        set(value) = prefs.edit().putBoolean(KEY_ACCESS_HELD, value).apply()

    /** Settings: reconnect the last embedded WireGuard tunnel after a restart (default OFF). */
    var reconnectOnBoot: Boolean
        get() = prefs.getBoolean(KEY_RECONNECT_ON_BOOT, false)
        set(value) = prefs.edit().putBoolean(KEY_RECONNECT_ON_BOOT, value).apply()

    /** True while an embedded WireGuard tunnel is up — the boot receiver reads it once. */
    var vpnWasActiveEmbeddedWg: Boolean
        get() = prefs.getBoolean(KEY_VPN_WAS_ACTIVE_WG, false)
        set(value) = prefs.edit().putBoolean(KEY_VPN_WAS_ACTIVE_WG, value).apply()

    companion object {
        private const val PREFS = "netpilot_settings"
        const val KEY_THEME = "theme"
        const val KEY_ADVISORY_DISMISSED = "dns_vpn_advisory_dismissed"
        const val KEY_SETUP_GUIDE_SHOWN = "setup_guide_shown"
        private const val KEY_NOTIF_PERMISSION_ASKED = "notification_permission_asked"
        private const val KEY_ACCESS_HELD = "dns_access_held"
        private const val KEY_RECONNECT_ON_BOOT = "reconnect_on_boot"
        private const val KEY_VPN_WAS_ACTIVE_WG = "vpn_was_active_embedded_wg"
    }
}
