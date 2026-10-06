package app.netpilot.core.vpn

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.netpilot.R
import app.netpilot.core.model.VpnProfile
import app.netpilot.core.prefs.AppPreferences
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.StringReader

/**
 * NetPilot's embedded WireGuard engine (official wireguard-android tunnel
 * library, Apache-2.0, disclosed in Settings → Open-source licenses).
 *
 * The userspace backend runs INSIDE NetPilot — one app, no second install —
 * and works on Android 9/10 where the platform IKEv2 API does not exist.
 * NetPilot's own state machine owns the tunnel: WireGuardRuntime carries the
 * same authority the classic service flags carry for the other tunnels.
 */
object WireGuardRuntime {

    @Volatile
    var runningProfileId: String? = null
        private set

    @Volatile
    var runningName: String? = null
        private set

    fun markStarted(profileId: String, name: String) {
        runningProfileId = profileId
        runningName = name
    }

    fun markStopped() {
        runningProfileId = null
        runningName = null
    }

    val isRunning: Boolean get() = runningProfileId != null
}

class WireGuardManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    // Lazy: loads the wg-go native library on first WireGuard use only.
    private val backend: GoBackend by lazy { GoBackend(appContext) }

    private var currentTunnel: NpTunnel? = null

    /** Connect/disconnect generation — stale async callbacks are dropped. */
    @Volatile
    private var sessionGeneration: Long = 0

    private inner class NpTunnel(private val tunnelName: String) : Tunnel {
        override fun getName(): String = tunnelName
        override fun onStateChange(newState: Tunnel.State) {
            // The backend can take the tunnel DOWN from OUTSIDE this manager:
            // the system revoked the VPN (Android settings, consent revoke,
            // another VPN app). Without this hook the runtime marker stayed
            // "running" forever and the UI showed connected over a dead
            // tunnel — the field-reported desync.
            if (newState == Tunnel.State.DOWN) {
                // VERIFY before settling: the backend can surface transient
                // DOWNs during reconfiguration (and our own putDown fires
                // one). Killing the markers on a spurious DOWN made
                // reconcile() treat the LIVE tunnel as a stray and shut it
                // down — tunnels died "after some time" in the field.
                Thread {
                    val reallyDown = runCatching {
                        backend.getRunningTunnelNames().none { it == tunnelName }
                    }.getOrDefault(true)
                    if (reallyDown) settleStoppedAsync()
                }.start()
            }
        }
    }

    /** Clears markers + boot pref and notifies the UI. Safe from any thread. */
    private fun settleStoppedAsync() {
        main.post {
            currentTunnel = null
            settleStopped()
        }
    }

    private fun settleStopped() {
        WireGuardRuntime.markStopped()
        AppPreferences(appContext).vpnWasActiveEmbeddedWg = false
        TunnelEvents.notifyChanged()
    }

    fun connect(
        profile: VpnProfile,
        onResult: (success: Boolean, errorRes: Int) -> Unit,
        onHandshake: (confirmed: Boolean) -> Unit = {},
    ) {
        val raw = profile.wgConfig
        if (raw.isNullOrBlank() || !WgConfigCheck.isPlausible(raw)) {
            post(onResult, false, R.string.err_wg_needs_config)
            return
        }
        val generation = ++sessionGeneration
        Thread {
            val result = runCatching {
                val config = Config.parse(BufferedReader(StringReader(raw)))
                val tunnel = NpTunnel(profile.name)
                // Android's VPN consent must already be granted (the UI runs
                // VpnService.prepare first); the backend throws otherwise.
                backend.setState(tunnel, Tunnel.State.UP, config)
                tunnel
            }
            main.post {
                // Drop stale results (user already connected another tunnel).
                if (generation != sessionGeneration) return@post
                result.fold(
                    onSuccess = { tunnel ->
                        currentTunnel = tunnel
                        WireGuardRuntime.markStarted(profile.id, profile.name)
                        AppPreferences(appContext).vpnWasActiveEmbeddedWg = true
                        TunnelEvents.notifyChanged()
                        onResult(true, 0)
                    },
                    onFailure = { e ->
                        onResult(false, errorResFor(e))
                    },
                )
            }
            // Honest "connected": the TUN being up is not enough — a WireGuard
            // tunnel without a server handshake carries no traffic. Watch the
            // real handshake counter before confirming.
            result.getOrNull()?.let { tunnel ->
                val confirmed = awaitHandshake(tunnel, HANDSHAKE_TIMEOUT_MS)
                main.post {
                    if (generation == sessionGeneration) onHandshake(confirmed)
                }
            }
        }.start()
    }

    private fun awaitHandshake(tunnel: NpTunnel, timeoutMs: Long): Boolean {
        var waited = 0L
        while (waited < timeoutMs) {
            try {
                Thread.sleep(HANDSHAKE_POLL_MS)
            } catch (_: InterruptedException) {
                return false
            }
            waited += HANDSHAKE_POLL_MS
            val stats = runCatching { backend.getStatistics(tunnel) }.getOrNull() ?: continue
            val confirmed = runCatching {
                stats.peers().any { key ->
                    stats.peer(key)?.latestHandshakeEpochMillis()?.let { it > 0 } == true
                }
            }.getOrDefault(false)
            if (confirmed) return true
        }
        return false
    }

    fun disconnect(onDone: () -> Unit = {}) {
        sessionGeneration++
        val tunnel = currentTunnel
        if (tunnel == null) {
            settleStopped()
            main.post { onDone() }
            return
        }
        Thread {
            // Put the tunnel down and VERIFY against the backend's own truth;
            // one retry — a transient wg-go failure must not leave a zombie
            // tunnel the app believes is off (field-reported: internet stayed
            // dead until force-stop).
            var down = putDown(tunnel)
            if (!down) down = putDown(tunnel)
            main.post {
                currentTunnel = null
                settleStopped()
                onDone()
            }
        }.start()
    }

    private fun putDown(tunnel: NpTunnel): Boolean {
        return try {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
            backend.getRunningTunnelNames().isEmpty()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Self-healing reconcile against the GoBackend's authoritative state.
     * Fixes EVERY divergence trigger — system revoke, half-completed
     * disconnect, lost bookkeeping — instead of trusting markers only our
     * own paths wrote. Cheap (a map lookup in wg-go); fire-and-forget; the
     * UI converges via [TunnelEvents] when something changed.
     */
    fun reconcile() {
        if (reconcileInFlight) return
        reconcileInFlight = true
        Thread {
            try {
                val running = runCatching { backend.getRunningTunnelNames() }
                    .getOrNull() ?: return@Thread
                val tracked = currentTunnel
                var changed = false
                if (running.isEmpty()) {
                    if (WireGuardRuntime.isRunning) {
                        WireGuardRuntime.markStopped()
                        AppPreferences(appContext).vpnWasActiveEmbeddedWg = false
                        changed = true
                    }
                } else {
                    // A running tunnel we do not track is almost always OURS
                    // after a spurious marker loss: RE-OWN it by name instead
                    // of killing it. Killing on divergence shut down live
                    // tunnels in the field. (NetPilot is the only tunnel
                    // creator on the device; there are no foreign strays.)
                    val name = tracked?.name?.takeIf { running.contains(it) } ?: running.first()
                    if (!WireGuardRuntime.isRunning) {
                        WireGuardRuntime.markStarted("", name)
                        AppPreferences(appContext).vpnWasActiveEmbeddedWg = true
                        changed = true
                    }
                }
                if (changed) TunnelEvents.notifyChangedAsync()
            } finally {
                reconcileInFlight = false
            }
        }.start()
    }

    @Volatile
    private var reconcileInFlight = false

    private fun errorResFor(e: Throwable): Int = when {
        e is BackendException && e.reason == BackendException.Reason.VPN_NOT_AUTHORIZED ->
            R.string.secure_dns_consent_failed
        e is BackendException && e.reason == BackendException.Reason.DNS_RESOLUTION_FAILURE ->
            R.string.wg_err_dns
        else -> R.string.wg_err_start_failed
    }

    private fun post(f: (Boolean, Int) -> Unit, ok: Boolean, err: Int) {
        main.post { f(ok, err) }
    }

    companion object {
        /** How long to watch for the first server handshake before warning. */
        const val HANDSHAKE_TIMEOUT_MS = 10_000L
        private const val HANDSHAKE_POLL_MS = 1_000L

        @Volatile
        private var instance: WireGuardManager? = null

        fun get(context: Context): WireGuardManager =
            instance ?: synchronized(this) {
                instance ?: WireGuardManager(context).also { instance = it }
            }
    }
}

/** Lightweight structural check used before storing/connecting a .conf. */
object WgConfigCheck {

    private val INTERFACE_SECTION = Regex("(?im)^\\s*\\[Interface]\\s*$")
    private val PRIVATE_KEY = Regex("(?im)^\\s*PrivateKey\\s*=\\s*\\S{40,}")
    private val PEER_SECTION = Regex("(?im)^\\s*\\[Peer]\\s*$")
    private val ENDPOINT = Regex("(?im)^\\s*Endpoint\\s*=\\s*(\\[[^]]+]|[^:\\s]+):(\\d+)\\s*$")

    /** True when the text looks like a usable WireGuard config (fast, no crypto). */
    fun isPlausible(text: String): Boolean =
        INTERFACE_SECTION.containsMatchIn(text) &&
            PRIVATE_KEY.containsMatchIn(text) &&
            PEER_SECTION.containsMatchIn(text) &&
            ENDPOINT.containsMatchIn(text)

    /** First "host:port" Endpoint found, for display fields. */
    fun endpointOf(text: String): Pair<String, Int>? {
        val m = ENDPOINT.find(text) ?: return null
        val port = m.groupValues[2].toIntOrNull() ?: return null
        return m.groupValues[1] to port
    }
}
