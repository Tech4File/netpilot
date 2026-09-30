package app.netpilot.core.vpn

import android.content.Context
import android.content.SharedPreferences
import app.netpilot.R

/**
 * Single source of truth for "which tunnel belongs to NetPilot?".
 *
 * Two of the three tunnel paths run in-process, so their static service flags
 * are authoritative (OpenVPN transport: [NetPilotVpnService], Secure-DNS
 * tunnel: [SecureDnsVpnService]). The platform IKEv2 path is different:
 * VpnManager hands the tunnel to an OS daemon — **no app service exists to
 * observe** — so a naive "is a VPN network up and is one of our services
 * running?" check misclassifies NetPilot's own IKEv2 tunnel as a foreign VPN
 * and locks the whole UI (v2.0.3 field bug).
 *
 * Fix: NetPilot records the platform session here the moment it starts one.
 * The marker is persisted because a platform-managed tunnel can outlive the
 * app process (a classic VpnService tunnel can never do that), letting a
 * restarted process recognise its own surviving tunnel. [reconcile] drops the
 * persisted marker as soon as the device has no VPN transport at all
 * (reboot / auth failure / teardown while we were dead).
 */
object VpnSessionState {

    data class ActiveTunnel(
        val profileId: String?,
        val name: String,
        val isPlatform: Boolean,
    )

    @Volatile
    private var platformId: String? = null

    @Volatile
    private var platformName: String? = null

    @Volatile
    private var claimedAtMs: Long? = null

    /** Records that NetPilot started a platform-managed (IKEv2/IPsec) session. */
    fun markPlatformStarted(context: Context, profileId: String, name: String) {
        val now = System.currentTimeMillis()
        claimedAtMs = now
        platformId = profileId
        platformName = name
        prefs(context).edit()
            .putString(KEY_ID, profileId)
            .putString(KEY_NAME, name)
            .putLong(KEY_AT, now)
            .apply()
    }

    /** Clears both the in-memory marker and the persisted one. */
    fun markPlatformStopped(context: Context) {
        platformId = null
        platformName = null
        claimedAtMs = null
        clearPersisted(context)
    }

    /** True when NetPilot has started (or restarted into) a platform IKEv2 session. */
    fun platformSessionActive(context: Context): Boolean =
        platformId != null || prefs(context).getString(KEY_ID, null) != null

    fun platformProfileId(context: Context): String? {
        platformId?.let { return it }
        return prefs(context).getString(KEY_ID, null)?.takeIf { it.isNotBlank() }
    }

    fun platformProfileName(context: Context): String? {
        platformName?.let { return it }
        return prefs(context).getString(KEY_NAME, null)?.takeIf { it.isNotBlank() }
    }

    /** True when any NetPilot-started tunnel has been claimed, even if the TUN is not up yet. */
    fun anySessionClaimed(context: Context): Boolean =
        NetPilotVpnService.isRunning ||
            SecureDnsVpnService.runningHostname != null ||
            platformSessionActive(context)

    /** The tunnel NetPilot currently owns, if any. */
    fun activeTunnel(context: Context): ActiveTunnel? {
        val appName = context.getString(R.string.app_name)
        return when {
            NetPilotVpnService.isRunning -> ActiveTunnel(
                profileId = NetPilotVpnService.runningProfileId,
                name = NetPilotVpnService.runningSession ?: appName,
                isPlatform = false,
            )
            SecureDnsVpnService.runningHostname != null -> {
                val host = SecureDnsVpnService.runningHostname ?: return null
                ActiveTunnel(profileId = null, name = host, isPlatform = false)
            }
            platformSessionActive(context) -> ActiveTunnel(
                profileId = platformProfileId(context),
                name = platformProfileName(context) ?: appName,
                isPlatform = true,
            )
            else -> null
        }
    }

    /**
     * Drops the persisted platform-session marker when the device no longer has
     * ANY VPN transport up. Never clears a live in-memory session (that would
     * race the tunnel-appears window during connect).
     */
    fun reconcile(context: Context, vpnTransportPresent: Boolean) {
        if (!vpnTransportPresent && platformId == null) {
            clearPersisted(context)
        }
    }

    fun clearPersisted(context: Context) {
        prefs(context).edit().remove(KEY_ID).remove(KEY_NAME).apply()
    }

    /** Test-only: simulates the process having restarted (volatile state lost, persisted marker kept). */
    internal fun simulateRestartForTest() {
        platformId = null
        platformName = null
        claimedAtMs = null
    }

    /**
     * Global self-heal for an abandoned connect attempt: if a platform claim
     * is older than [CLAIM_GRACE_MS], the device still has no VPN transport
     * and no classic service is up, the tunnel will never appear (OS-layer
     * auth failure has no callback). Clears the claim so NO screen —
     * dashboard included — can stay stuck on "Connecting…". The VPN tab's
     * watchdog uses the same grace for its inline toast.
     *
     * @return true when a stale claim was cleared.
     */
    fun clearIfExpired(context: Context, vpnTransportPresent: Boolean): Boolean {
        if (vpnTransportPresent) return false
        if (NetPilotVpnService.isRunning || SecureDnsVpnService.runningHostname != null) return false
        if (!platformSessionActive(context)) return false
        val at = claimedAtMs
            ?: prefs(context).getLong(KEY_AT, 0L).takeIf { it > 0L }
            ?: return false
        if (System.currentTimeMillis() - at <= CLAIM_GRACE_MS) return false
        markPlatformStopped(context)
        return true
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** How long a platform claim may wait for its TUN before it is abandoned. */
    const val CLAIM_GRACE_MS = 10_000L

    private const val PREFS = "netpilot_vpn_session"
    private const val KEY_ID = "platform_profile_id"
    private const val KEY_NAME = "platform_profile_name"
    private const val KEY_AT = "platform_claimed_at"
}
