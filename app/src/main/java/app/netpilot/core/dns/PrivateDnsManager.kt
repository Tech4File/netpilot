package app.netpilot.core.dns

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import app.netpilot.core.model.DnsMode
import app.netpilot.core.model.DnsSystemState

/**
 * Reads and writes the system-wide Private DNS setting (Settings.Global).
 *
 * Android protects these keys: writing them requires WRITE_SECURE_SETTINGS,
 * which no installable app gets by default. NetPilot asks the device owner to
 * grant it once over ADB (root-free, reversible, survives updates):
 *
 *     adb shell pm grant app.netpilot android.permission.WRITE_SECURE_SETTINGS
 *
 * This is exactly how Android TV units without the Settings UI get Private DNS.
 */
object PrivateDnsManager {

    const val KEY_MODE = "private_dns_mode"
    const val KEY_SPECIFIER = "private_dns_specifier"
    const val PERMISSION = Manifest.permission.WRITE_SECURE_SETTINGS

    fun hasWritePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun read(context: Context): DnsSystemState {
        val mode = DnsMode.fromSetting(Settings.Global.getString(context.contentResolver, KEY_MODE))
        val specifier = Settings.Global.getString(context.contentResolver, KEY_SPECIFIER)
        return DnsSystemState(mode, specifier)
    }

    /**
     * Activates a profile: strict mode + provider hostname.
     * Only one specifier can exist system-wide, so activating a profile always
     * replaces any previous provider — single-active by construction.
     */
    fun applyProfile(context: Context, hostname: String): Boolean = write(context) {
        Settings.Global.putString(it, KEY_MODE, DnsMode.CUSTOM.settingValue)
        Settings.Global.putString(it, KEY_SPECIFIER, hostname)
    }

    fun setAutomatic(context: Context): Boolean = write(context) {
        Settings.Global.putString(it, KEY_MODE, DnsMode.AUTOMATIC.settingValue)
        Settings.Global.putString(it, KEY_SPECIFIER, null)
    }

    fun disable(context: Context): Boolean = write(context) {
        Settings.Global.putString(it, KEY_MODE, DnsMode.OFF.settingValue)
        Settings.Global.putString(it, KEY_SPECIFIER, null)
    }

    private inline fun write(context: Context, block: (ContentResolver) -> Unit): Boolean {
        if (!hasWritePermission(context)) return false
        return try {
            block(context.contentResolver)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Event-driven updates (no polling): fires when the system DNS setting changes,
     * whether by NetPilot, another app, or the OS.
     */
    fun observe(context: Context, listener: () -> Unit): ContentObserver {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                listener()
            }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(Settings.Global.getUriFor(KEY_MODE), false, observer)
        resolver.registerContentObserver(Settings.Global.getUriFor(KEY_SPECIFIER), false, observer)
        return observer
    }

    fun stopObserving(context: Context, observer: ContentObserver?) {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
    }
}
