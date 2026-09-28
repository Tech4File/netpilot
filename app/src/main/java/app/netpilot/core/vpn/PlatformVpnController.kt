package app.netpilot.core.vpn

import android.content.Context
import android.content.Intent
import android.net.Ikev2VpnProfile
import android.net.VpnManager
import android.os.Build
import androidx.annotation.RequiresApi
import app.netpilot.core.model.VpnAuthType
import app.netpilot.core.model.VpnProfile

/**
 * Fully native platform VPN (IKEv2/IPsec) via Android's VpnManager —
 * the OS implements the protocol; NetPilot only provisions and starts/stops it.
 * Available on Android 11+ (API 30).
 */
class PlatformVpnController(private val context: Context) {

    private val vpnManager: VpnManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            @Suppress("InlinedApi") // constant is inlined; guarded above
            context.getSystemService(Context.VPN_MANAGEMENT_SERVICE) as? VpnManager
        } else {
            null
        }

    /**
     * Returns the consent Intent to launch (user must approve once per profile),
     * or null when consent is already granted — then [start] can be called directly.
     */
    fun provisionIntent(profile: VpnProfile): Intent? {
        if (!isSupported()) return null
        val vm = vpnManager ?: return null
        return try {
            vm.provisionVpnProfile(build(profile))
        } catch (_: Exception) {
            null
        }
    }

    fun start(profile: VpnProfile): Boolean {
        if (!isSupported()) return false
        val vm = vpnManager ?: return false
        return try {
            vm.provisionVpnProfile(build(profile)) // keep the stored profile in sync after edits
            if (Build.VERSION.SDK_INT >= 33) {
                vm.startProvisionedVpnProfileSession()
            } else {
                @Suppress("DEPRECATION")
                vm.startProvisionedVpnProfile()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stop(): Boolean {
        if (!isSupported()) return false
        val vm = vpnManager ?: return false
        return try {
            vm.stopProvisionedVpnProfile()
            true
        } catch (_: Exception) {
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    internal fun build(profile: VpnProfile): Ikev2VpnProfile {
        val identity = profile.username.ifBlank { DEFAULT_IDENTITY }
        val builder = Ikev2VpnProfile.Builder(profile.serverHost, identity)
        when (profile.authType) {
            // Platform API: username/password (EAP) server auth is certificate-pinned
            // — a CA certificate is mandatory. PSK needs no certificate.
            VpnAuthType.USER_PASS -> builder.setAuthUsernamePassword(
                identity,
                profile.password,
                CaCertParser.decodeCertificate(
                    profile.caCertPem ?: error("IKEv2 user/pass auth requires a CA certificate"),
                ),
            )
            VpnAuthType.PSK -> builder.setAuthPsk(profile.preSharedKey.toByteArray())
        }
        builder.setBypassable(false)
        builder.setMetered(false)
        return builder.build()
    }

    companion object {
        private const val DEFAULT_IDENTITY = "netpilot"

        /** Platform IKEv2 API exists since Android 11 (R). */
        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }
}
