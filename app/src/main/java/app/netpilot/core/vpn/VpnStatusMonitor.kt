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
        try {
            cm.registerNetworkCallback(request, callback)
            registeredContext = appContext
        } catch (_: Exception) {
            // Very old/vendor-broken builds: UI still refreshes on resume.
        }
    }

    /**
     * True only when a VPN transport is present AND it belongs to NetPilot.
     * Device farms and other apps can run their own VPN; reporting any VPN as
     * "ours" made the dashboard show ON with nothing to turn off (LambdaTest
     * finding). Our services live in this process, so their static flags are
     * authoritative for our own tunnels.
     */
    fun ourVpnActive(context: Context): Boolean =
        isActive(context) && (NetPilotVpnService.isRunning || SecureDnsVpnService.runningHostname != null)

    /** A VPN is up, but it is another app's (or the device farm's). */
    fun foreignVpnActive(context: Context): Boolean =
        isActive(context) && !ourVpnActive(context)

    fun isActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return cm.allNetworks.any { n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
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
