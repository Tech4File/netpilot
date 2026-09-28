package app.netpilot.core.profiles

import app.netpilot.core.dns.DnsProfileRepository
import app.netpilot.core.vpn.VpnProfileRepository
import org.json.JSONObject

/**
 * Combined export/import of DNS + VPN profiles (Settings screen).
 * The file stays on the user's storage via the system file picker (SAF);
 * VPN secrets are intentionally NOT exported — metadata only.
 */
object ProfileBackup {

    const val MIME_TYPE = "application/json"
    const val SUGGESTED_NAME = "netpilot-profiles.json"

    fun export(dns: DnsProfileRepository, vpn: VpnProfileRepository): String {
        val root = JSONObject()
        root.put("type", "netpilot.bundle.v1")
        root.put("dns", JSONObject(dns.exportJson()))
        root.put("vpn", JSONObject(vpn.exportJson()))
        return root.toString(2)
    }

    /** @return total number of newly imported profiles. */
    fun import(text: String, dns: DnsProfileRepository, vpn: VpnProfileRepository): Int {
        val root = JSONObject(text)
        var count = 0
        val dnsNode = root.optJSONObject("dns")
        if (dnsNode != null) count += dns.importJson(dnsNode.toString())
        val vpnNode = root.optJSONObject("vpn")
        if (vpnNode != null) count += vpn.importJson(vpnNode.toString())
        // Tolerate single-repository exports too.
        if (dnsNode == null && vpnNode == null) {
            count += dns.importJson(text)
            count += vpn.importJson(text)
        }
        return count
    }
}
