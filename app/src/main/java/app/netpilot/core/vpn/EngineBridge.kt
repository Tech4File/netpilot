package app.netpilot.core.vpn

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Bridge to the OpenVPN protocol engine.
 *
 * NetPilot is engine-agnostic by design: the OpenVPN *data channel* is a large
 * native C++ core (with crypto inside the privileged VPN path), so it is NOT
 * bundled. Instead NetPilot drives the official, open-source engine app
 * ("OpenVPN for Android", de.blinkt.openvpn) through its documented external
 * control surface ("Controlling from external apps" in the project README):
 *
 *   - profile hand-off: standard ACTION_VIEW on an .ovpn content URI
 *   - connect:  activity  de.blinkt.openvpn.api.ConnectVPN
 *               extra    de.blinkt.openvpn.api.profileName = <profile name>
 *   - disconnect: activity de.blinkt.openvpn.api.DisconnectVPN
 *
 * This gives Android 9/10 devices (no platform IKEv2 API) a working, honest
 * VPN path: the engine connects, NetPilot keeps owning profiles, DNS and the
 * status surface. The engine's tunnel is somebody else's VpnService —
 * VpnStatusMonitor already classifies it as a foreign VPN and the UI says so.
 *
 * OpenVPN Connect (net.openvpn.openvpn) is detected as a secondary engine but
 * only receives the profile file hand-off (its control intents are not a
 * documented public API).
 */
object EngineBridge {

    /** Official open-source engine with a documented external control API. */
    const val ICS_PACKAGE = "de.blinkt.openvpn"
    const val ICS_CONNECT_CLASS = "de.blinkt.openvpn.api.ConnectVPN"
    const val ICS_DISCONNECT_CLASS = "de.blinkt.openvpn.api.DisconnectVPN"
    const val EXTRA_PROFILE_NAME = "de.blinkt.openvpn.api.profileName"

    /** Official OpenVPN Connect app — file hand-off only. */
    const val CONNECT_PACKAGE = "net.openvpn.openvpn"

    const val OVPN_MIME = "application/x-openvpn-profile"

    fun installed(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: Exception) {
        false
    }

    /** The primary engine (with connect/disconnect control) is installed? */
    fun primaryEngineInstalled(context: Context): Boolean =
        installed(context, ICS_PACKAGE)

    /**
     * Writes the profile's config to a FileProvider URI for engine import.
     * The file is named after the profile so the engine's auto-suggested
     * profile name matches what [connectIntent] targets.
     */
    fun handOffUri(context: Context, profileName: String, config: String): Uri {
        val dir = File(context.cacheDir, "engine-handoff").apply { mkdirs() }
        // Keep engine-side profile name == NetPilot profile name: the file
        // stem becomes the suggested name; strip filesystem-hostile chars.
        val stem = profileName.replace(Regex("[^A-Za-z0-9 _.-]"), "_").trim().ifEmpty { "profile" }
        val file = File(dir, "$stem.ovpn")
        file.writeText(config)
        return FileProvider.getUriForFile(context, "${context.packageName}.enginehandoff", file)
    }

    /**
     * One-time import: opens the engine's import flow with the profile file.
     * @return false when no engine can consume it (caller shows install help).
     */
    fun launchImport(context: Context, profileName: String, config: String): Boolean {
        if (!primaryEngineInstalled(context)) return false
        return try {
            val uri = handOffUri(context, profileName, config)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, OVPN_MIME)
                .setPackage(ICS_PACKAGE)
                .addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK,
                )
            grantRead(context, uri, ICS_PACKAGE)
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Connects a profile that is already saved in the engine (by name).
     * @return false when the engine is missing (caller shows install help).
     */
    fun launchConnect(context: Context, profileName: String): Boolean {
        if (!primaryEngineInstalled(context)) return false
        return try {
            val intent = Intent(ICS_CONNECT_ACTION)
                .setClassName(ICS_PACKAGE, ICS_CONNECT_CLASS)
                .putExtra(EXTRA_PROFILE_NAME, profileName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Asks the engine to disconnect its own VPN. Safe no-op when the engine
     * holds no VPN (and it can never stop a DIFFERENT app's tunnel).
     */
    fun launchDisconnect(context: Context) {
        if (!primaryEngineInstalled(context)) return
        try {
            context.startActivity(
                Intent(ICS_DISCONNECT_ACTION)
                    .setClassName(ICS_PACKAGE, ICS_DISCONNECT_CLASS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
            // Engine gone or blocked — nothing we can do from here.
        }
    }

    /** Opens the engine's Play Store page (falls back to the web page). */
    fun launchInstall(context: Context): Boolean {
        val web = Intent(Intent.ACTION_VIEW, Uri.parse(INSTALL_PAGE))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$ICS_PACKAGE"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (_: Exception) {
            try {
                context.startActivity(web)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun grantRead(context: Context, uri: Uri, packageName: String) {
        try {
            context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // The intent flag already carries the grant on modern Android.
        }
    }

    /** Internal: action strings kept here so tests can pin them. */
    const val ICS_CONNECT_ACTION = "de.blinkt.openvpn.api.ConnectVPN"
    const val ICS_DISCONNECT_ACTION = "de.blinkt.openvpn.api.DisconnectVPN"
    const val INSTALL_PAGE = "https://ics.openvpn.net/"
}
