package app.netpilot.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.StringRes
import app.netpilot.R

/** Reads one-shot facts about the active network for the Dashboard card. */
object NetworkInfoProvider {

    data class Info(
        @StringRes val typeNameRes: Int,
        val iface: String?,
        val dnsServers: List<String>,
        val connected: Boolean,
    )

    fun read(context: Context): Info? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val network = cm.activeNetwork
            ?: return Info(R.string.network_unavailable, null, emptyList(), connected = false)
        val caps = cm.getNetworkCapabilities(network)
            ?: return Info(R.string.network_unavailable, null, emptyList(), connected = false)
        val lp = cm.getLinkProperties(network)

        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> R.string.net_vpn
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> R.string.net_wifi
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> R.string.net_ethernet
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> R.string.net_cellular
            else -> R.string.net_other
        }
        return Info(
            typeNameRes = type,
            iface = lp?.interfaceName,
            dnsServers = lp?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty(),
            connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        )
    }
}
