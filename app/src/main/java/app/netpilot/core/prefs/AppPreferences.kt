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

    var dnsVpnAdvisoryDismissed: Boolean
        get() = prefs.getBoolean(KEY_ADVISORY_DISMISSED, false)
        set(value) = prefs.edit().putBoolean(KEY_ADVISORY_DISMISSED, value).apply()

    companion object {
        private const val PREFS = "netpilot_settings"
        const val KEY_THEME = "theme"
        const val KEY_ADVISORY_DISMISSED = "dns_vpn_advisory_dismissed"
    }
}
