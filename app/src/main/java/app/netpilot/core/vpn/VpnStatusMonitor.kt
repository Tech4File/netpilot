package app.netpilot.core.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Event-driven VPN state tracking (a single system NetworkCallback — no polling).
 * Android enforces one active VPN system-wide; a VPN network with TRANSPORT_VPN
 * simply appears or disappears.
 */
object VpnStatusMonitor {

    interface Listener {
        fun onVpnChanged(active: Boolean, details: VpnRuntimeInfo?)
    }

    data class VpnRuntimeInfo(val iface: String?, val dnsServers: List<String>)

    private val listeners = CopyOnWriteArrayList<Listener>()
    private var registeredContext: Context? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun addListener(listener: Listener) = listeners.add(listener)
    fun removeListener(listener: Listener) = listeners.remove(listener)

    @Synchronized
    fun start(context: Context) {
        if (registeredContext != null) return
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = notify(appContext)
            override fun onLost(network: Network) = notify(appContext)
        }
        // A platform IKEv2 session can outlive our process; when we come back,
        // drop the persisted session marker if the device has no VPN at all.
        VpnSessionState.reconcile(appContext, isActive(appContext))
        try {
            cm.registerNetworkCallback(request, callback)
            registeredContext = appContext
            this.callback = callback
        } catch (_: Exception) {
            // Very old/vendor-broken builds: UI still refreshes on resume.
        }
    }

    /**
     * True only when a VPN transport is present AND it belongs to NetPilot.
     * Device farms and other apps can run their own VPN; reporting any VPN as
     * "ours" made the dashboard show ON with nothing to turn off (LambdaTest
     * finding). Our classic services live in this process, so their static
     * flags are authoritative; the platform IKEv2 path has NO in-process
     * service (the OS daemon owns the tunnel), so its recorded session in
     * [VpnSessionState] is the ownership proof (v2.0.3 field bug fix —
     * NetPilot's own IKEv2 tunnel used to be misclassified as foreign).
     */
    fun ourVpnActive(context: Context): Boolean =
        isActive(context) && (
            NetPilotVpnService.isRunning ||
                SecureDnsVpnService.runningHostname != null ||
                WireGuardRuntime.isRunning ||
                VpnSessionState.platformSessionActive(context)
            )

    /**
     * True when NetPilot has claimed a tunnel even if the TUN is not up yet
     * (the connecting window) — used for the "Connecting…" UI state.
     */
    fun anySessionClaimed(context: Context): Boolean = VpnSessionState.anySessionClaimed(context)

    /** A VPN is up, but it is another app's (or the device farm's). */
    fun foreignVpnActive(context: Context): Boolean =
        isActive(context) && !ourVpnActive(context)

    fun isActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return cm.allNetworks.any { n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    /**
     * Unregisters the system callback — called when the app leaves the
     * foreground (battery model: nothing listens while nothing is visible;
     * queries like [isActive] keep working without the callback).
     */
    @Synchronized
    fun stop(context: Context) {
        callback?.let { cb ->
            (context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
                ?.runCatching { unregisterNetworkCallback(cb) }
        }
        callback = null
        registeredContext = null
    }

    fun currentInfo(context: Context): VpnRuntimeInfo? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        return cm.allNetworks.firstNotNullOfOrNull { n ->
            val caps = cm.getNetworkCapabilities(n) ?: return@firstNotNullOfOrNull null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@firstNotNullOfOrNull null
            val lp = cm.getLinkProperties(n)
            VpnRuntimeInfo(
                iface = lp?.interfaceName,
                dnsServers = lp?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty(),
            )
        }
    }

    private fun notify(context: Context) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val active = isActive(context)
            val info = currentInfo(context)
            listeners.forEach { it.onVpnChanged(active, info) }
        }
    }
}
