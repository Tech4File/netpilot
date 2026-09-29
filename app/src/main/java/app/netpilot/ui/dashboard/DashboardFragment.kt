package app.netpilot.ui.dashboard

import android.database.ContentObserver
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.netpilot.MainActivity
import app.netpilot.R
import app.netpilot.core.dns.PrivateDnsManager
import app.netpilot.core.dns.DnsProfileRepository
import app.netpilot.core.model.DnsMode
import app.netpilot.core.network.NetworkInfoProvider
import app.netpilot.core.prefs.AppPreferences
import app.netpilot.core.vpn.SecureDnsVpnService
import app.netpilot.core.vpn.VpnStatusMonitor
import app.netpilot.databinding.FragmentDashboardBinding
import app.netpilot.ui.NavTab
import app.netpilot.ui.dialogs.SetupDialogs

/**
 * Glanceable command center: protection state, quick jump cards, one-time setup
 * and live network facts (interface, effective DNS servers).
 */
class DashboardFragment : Fragment(), VpnStatusMonitor.Listener {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: AppPreferences
    private lateinit var dnsRepo: DnsProfileRepository
    private var dnsObserver: ContentObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPreferences(requireContext())
        dnsRepo = DnsProfileRepository(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.cardDnsQuick.setOnClickListener { go(NavTab.PRIVATE_DNS) }
        binding.cardVpnQuick.setOnClickListener { go(NavTab.VPN) }
        binding.heroStatus.setOnClickListener { go(NavTab.PRIVATE_DNS) }
        binding.setupGuideBtn.setOnClickListener {
            SetupDialogs.showPermissionGuide(requireActivity() as MainActivity) { refresh() }
        }
        // Persistent (i): opens the same guide any time, even when granted.
        binding.btnInfo.setOnClickListener {
            SetupDialogs.showPermissionGuide(requireActivity() as MainActivity) { refresh() }
        }
        // First-launch only: auto-show the guide when permission is missing.
        // Closed == acknowledged; afterwards the (i) button is the way back.
        if (!PrivateDnsManager.hasWritePermission(requireContext()) && !prefs.setupGuideShown) {
            prefs.setupGuideShown = true
            SetupDialogs.showPermissionGuide(requireActivity() as MainActivity) { refresh() }
        }
        binding.advisoryCard.setOnClickListener {
            prefs.dnsVpnAdvisoryDismissed = true
            binding.advisoryCard.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        VpnStatusMonitor.addListener(this)
        dnsObserver = PrivateDnsManager.observe(requireContext()) { refresh() }
    }

    override fun onPause() {
        super.onPause()
        VpnStatusMonitor.removeListener(this)
        PrivateDnsManager.stopObserving(requireContext(), dnsObserver)
        dnsObserver = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onVpnChanged(active: Boolean, details: VpnStatusMonitor.VpnRuntimeInfo?) {
        if (_binding != null) refresh()
    }

    private fun go(tab: NavTab) {
        (requireActivity() as MainActivity).selectTab(tab)
    }

    fun refresh() {
        val context = requireContext()
        val dns = PrivateDnsManager.read(context)
        val vpnActiveRaw = VpnStatusMonitor.isActive(context)
        val secureDnsOn = SecureDnsVpnService.runningHostname != null
        // Only OUR tunnels count as NetPilot being "on" — device farms and
        // other apps run VPNs too (LambdaTest finding).
        val systemVpnActive = VpnStatusMonitor.ourVpnActive(context)
        val foreignVpn = vpnActiveRaw && !systemVpnActive
        val dnsEncrypting = dns.mode == DnsMode.CUSTOM || secureDnsOn
        val activeProfile = dnsRepo.activeProfile()
        val providerName = activeProfile?.name ?: dns.specifier

        // Hero status
        val (textRes, subRes, colorRes) = when {
            dnsEncrypting && systemVpnActive ->
                Triple(R.string.status_protected, R.string.protection_sub_protected, R.color.status_success)
            dnsEncrypting ->
                Triple(R.string.status_dns_only, R.string.protection_sub_dns, R.color.status_info)
            systemVpnActive ->
                Triple(R.string.status_vpn_only, R.string.protection_sub_vpn, R.color.status_info)
            foreignVpn ->
                Triple(R.string.status_foreign_vpn, R.string.protection_sub_foreign, R.color.status_info)
            else ->
                Triple(R.string.status_unprotected, R.string.protection_sub_none, R.color.status_warning)
        }
        binding.heroStatusText.setText(textRes)
        binding.heroStatusSub.setText(subRes)
        binding.heroPill.set(textRes, colorRes)
        binding.heroIcon.setColorFilter(
            androidx.core.content.ContextCompat.getColor(context, colorRes),
        )

        // Quick cards
        binding.dnsQuickState.text = when {
            dns.mode == DnsMode.CUSTOM -> getString(R.string.state_on) + " · " + (providerName ?: "—")
            dns.mode == DnsMode.AUTOMATIC -> getString(R.string.state_automatic)
            secureDnsOn -> getString(R.string.state_on) + " · " + (SecureDnsVpnService.runningHostname ?: "—") + " (VPN)"
            else -> getString(R.string.state_off)
        }
        binding.vpnQuickState.setText(
            when {
                systemVpnActive -> R.string.state_on
                foreignVpn -> R.string.state_foreign_vpn
                else -> R.string.state_off
            },
        )

        // Setup + advisory cards
        binding.setupCard.visibility =
            if (PrivateDnsManager.hasWritePermission(context)) View.GONE else View.VISIBLE
        binding.advisoryCard.visibility =
            if (dnsEncrypting && systemVpnActive && !prefs.dnsVpnAdvisoryDismissed) {
                View.VISIBLE
            } else {
                View.GONE
            }

        // Network facts
        val info = NetworkInfoProvider.read(context)
        if (info != null) {
            binding.netRowType.factValue.setText(info.typeNameRes)
            binding.netRowIface.factValue.text = info.iface ?: getString(R.string.network_none)
            binding.netRowDns.factValue.text =
                info.dnsServers.joinToString("\n").ifBlank { getString(R.string.network_none) }
            binding.netRowPrivdns.factValue.text = when (dns.mode) {
                DnsMode.CUSTOM -> (dns.specifier ?: "—") + " · " + getString(R.string.dns_status_active)
                DnsMode.AUTOMATIC -> getString(R.string.dns_status_automatic)
                DnsMode.OFF -> getString(R.string.dns_status_off)
            }
        }
    }
}
