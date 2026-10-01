package app.netpilot.ui.dns

import android.database.ContentObserver
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import app.netpilot.R
import app.netpilot.core.dns.DnsProfileRepository
import app.netpilot.core.dns.HostnameCheck
import app.netpilot.core.dns.HostnameValidator
import app.netpilot.core.dns.PrivateDnsManager
import app.netpilot.core.model.DnsMode
import app.netpilot.core.model.DnsProfile
import android.content.Intent
import android.net.VpnService
import androidx.activity.result.contract.ActivityResultContracts
import app.netpilot.core.status.StatusNotifications
import app.netpilot.core.vpn.NetPilotVpnService
import app.netpilot.core.vpn.PlatformVpnController
import app.netpilot.core.vpn.SecureDnsVpnService
import app.netpilot.core.vpn.VpnSessionState
import app.netpilot.core.vpn.VpnStatusMonitor
import app.netpilot.core.vpn.WireGuardManager
import app.netpilot.core.vpn.WireGuardRuntime
import app.netpilot.databinding.DialogDnsProfileBinding
import app.netpilot.databinding.FragmentPrivateDnsBinding
import app.netpilot.ui.dialogs.SetupDialogs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Private DNS management: master switch, mode selector and single-active profiles.
 * Switching profiles writes the system setting; only one provider is ever active.
 */
class PrivateDnsFragment : Fragment(), VpnStatusMonitor.Listener {

    private var _binding: FragmentPrivateDnsBinding? = null
    private val binding get() = _binding!!

    private lateinit var repo: DnsProfileRepository
    private lateinit var adapter: DnsProfilesAdapter
    private var dnsObserver: ContentObserver? = null
    private var suppressUiCallbacks = false
    private var editing: DnsProfile? = null
    private var pendingSecureDnsHost: String? = null

    private val secureDnsConsentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val host = pendingSecureDnsHost
            pendingSecureDnsHost = null
            if (result.resultCode == android.app.Activity.RESULT_OK && host != null) {
                launchSecureDns(host)
            } else {
                toast(getString(R.string.secure_dns_consent_failed))
            }
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = DnsProfileRepository(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentPrivateDnsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = DnsProfilesAdapter(
            onRowClick = ::onRowActivated,
            onEdit = ::onEditClicked,
            onDelete = ::onDeleteClicked,
        )
        binding.dnsProfilesList.layoutManager = LinearLayoutManager(requireContext())
        binding.dnsProfilesList.adapter = adapter

        binding.dnsSegment.configure(
            items = listOf(
                getString(R.string.dns_mode_off),
                getString(R.string.dns_mode_automatic),
                getString(R.string.dns_mode_custom),
            ),
            initialIndex = 0,
            listener = object : app.netpilot.ui.components.SegmentedToggle.OnSelectionListener {
                override fun onSelected(index: Int) = onModeSelected(index)
            },
        )

        binding.dnsSwitch.setOnCheckedChangeListener { _, checked ->
            if (suppressUiCallbacks) return@setOnCheckedChangeListener
            onMasterSwitch(checked)
        }
        binding.secureDnsSwitch.setOnCheckedChangeListener { _, checked ->
            if (suppressUiCallbacks) return@setOnCheckedChangeListener
            onSecureDnsSwitch(checked)
        }
        binding.dnsAddRow.setOnClickListener { showProfileDialog(null) }
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

    // ------------------------------------------------------------------ state

    fun refresh() {
        val context = requireContext()
        val state = PrivateDnsManager.read(context)
        val profiles = repo.list()
        val activeId = repo.activeId().takeIf { id -> profiles.any { it.id == id } }

        suppressUiCallbacks = true
        binding.dnsSwitch.isChecked = state.mode != DnsMode.OFF
        binding.dnsSegment.setSelection(
            when (state.mode) {
                DnsMode.OFF -> 0
                DnsMode.AUTOMATIC -> 1
                DnsMode.CUSTOM -> 2
            },
            notify = false,
        )
        val (pillText, pillColor) = when (state.mode) {
            DnsMode.CUSTOM -> R.string.dns_status_active to R.color.status_success
            DnsMode.AUTOMATIC -> R.string.dns_status_automatic to R.color.status_info
            DnsMode.OFF -> R.string.dns_status_off to R.color.on_surface_variant
        }
        binding.dnsStatusPill.set(pillText, pillColor)
        adapter.submit(profiles, activeId)
        binding.dnsEmpty.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE
        binding.dnsProfilesList.visibility = if (profiles.isEmpty()) View.GONE else View.VISIBLE
        binding.secureDnsSwitch.isChecked = SecureDnsVpnService.runningHostname != null
        suppressUiCallbacks = false
        StatusNotifications.reconcileStatusService(requireContext())
    }

    // ------------------------------------------------- zero-setup secure DNS

    private fun onSecureDnsSwitch(checked: Boolean) {
        if (checked) {
            val host = repo.activeProfile()?.hostname
            if (host == null) {
                toast(getString(R.string.secure_dns_need_profile))
                refresh()
                showProfileDialog(null)
                return
            }
            val prepare = VpnService.prepare(requireContext())
            if (prepare != null) {
                pendingSecureDnsHost = host
                secureDnsConsentLauncher.launch(prepare)
            } else {
                launchSecureDns(host)
            }
        } else {
            requireContext().startService(
                Intent(requireContext(), SecureDnsVpnService::class.java)
                    .setAction(SecureDnsVpnService.ACTION_STOP),
            )
            toast(getString(R.string.secure_dns_stopped_toast))
            refresh()
        }
    }

    private fun launchSecureDns(host: String) {
        // One VPN per app: make room for the Secure-DNS tunnel.
        var replacedSomething = false
        if (VpnSessionState.platformSessionActive(requireContext())) {
            PlatformVpnController(requireContext()).stop()
            VpnSessionState.markPlatformStopped(requireContext())
            replacedSomething = true
        }
        if (NetPilotVpnService.isRunning) {
            requireContext().startService(
                Intent(requireContext(), NetPilotVpnService::class.java)
                    .setAction(NetPilotVpnService.ACTION_DISCONNECT),
            )
            replacedSomething = true
        }
        if (WireGuardRuntime.isRunning) {
            WireGuardManager.get(requireContext()).disconnect()
            replacedSomething = true
        }
        if (replacedSomething) toast(getString(R.string.secure_dns_vpn_replaced))
        requireContext().startService(
            Intent(requireContext(), SecureDnsVpnService::class.java)
                .setAction(SecureDnsVpnService.ACTION_START)
                .putExtra(SecureDnsVpnService.EXTRA_HOST, host),
        )
        toast(getString(R.string.secure_dns_enabled_toast, host))
        refresh()
    }

    // --------------------------------------------------------------- actions

    private fun onMasterSwitch(checked: Boolean) {
        val context = requireContext()
        if (!PrivateDnsManager.hasWritePermission(context)) {
            showPermissionGuide()
            refresh()
            return
        }
        if (checked) {
            val active = repo.activeProfile()
            if (active != null) {
                applyProfile(active)
            } else {
                refresh()
                showProfileDialog(null)
            }
        } else {
            val confirmed = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dns_deactivate_confirm_title)
                .setMessage(R.string.dns_deactivate_confirm_msg)
                .setPositiveButton(R.string.action_disable) { _, _ ->
                    PrivateDnsManager.disable(context)
                    repo.clearActive()
                    toast(getString(R.string.dns_profile_deactivated))
                    refresh()
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
            confirmed.setOnDismissListener { refresh() }
        }
    }

    private fun onModeSelected(index: Int) {
        val context = requireContext()
        if (!PrivateDnsManager.hasWritePermission(context)) {
            showPermissionGuide()
            refresh()
            return
        }
        when (index) {
            0 -> {
                PrivateDnsManager.disable(context)
                repo.clearActive()
                toast(getString(R.string.dns_profile_deactivated))
            }
            1 -> PrivateDnsManager.setAutomatic(context)
            2 -> {
                val active = repo.activeProfile()
                if (active != null) {
                    applyProfile(active)
                } else {
                    refresh()
                    showProfileDialog(null)
                    return
                }
            }
        }
        refresh()
    }

    private fun onRowActivated(profile: DnsProfile) {
        val context = requireContext()
        if (repo.activeId() == profile.id && PrivateDnsManager.read(context).mode == DnsMode.CUSTOM) {
            // Tapping the active profile turns Private DNS off (single-active toggle UX).
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dns_deactivate_confirm_title)
                .setMessage(R.string.dns_deactivate_confirm_msg)
                .setPositiveButton(R.string.action_disable) { _, _ ->
                    PrivateDnsManager.disable(context)
                    repo.clearActive()
                    refresh()
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
            return
        }
        if (!PrivateDnsManager.hasWritePermission(context)) {
            showPermissionGuide()
            return
        }
        repo.setActive(profile.id)
        applyProfile(profile)
    }

    private fun applyProfile(profile: DnsProfile) {
        val ok = PrivateDnsManager.applyProfile(requireContext(), profile.hostname)
        if (ok) {
            toast(getString(R.string.dns_profile_activated, profile.name))
        } else {
            showPermissionGuide()
        }
        refresh()
    }

    private fun onEditClicked(profile: DnsProfile) {
        showProfileDialog(profile)
    }

    private fun onDeleteClicked(profile: DnsProfile) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_profile_title)
            .setMessage(getString(R.string.delete_profile_msg, profile.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                val wasActive = repo.delete(profile.id)
                if (wasActive) {
                    PrivateDnsManager.disable(requireContext())
                    toast(getString(R.string.dns_profile_deactivated))
                }
                if (SecureDnsVpnService.runningHostname == profile.hostname) {
                    requireContext().startService(
                        android.content.Intent(requireContext(), SecureDnsVpnService::class.java)
                            .setAction(SecureDnsVpnService.ACTION_STOP),
                    )
                }
                refresh()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- dialog

    /** Visible for UI test automation (Robolectric + instrumentation). */
    fun showProfileDialog(existing: DnsProfile?) {
        editing = existing
        val dialogBinding = DialogDnsProfileBinding.inflate(layoutInflater)
        dialogBinding.etName.setText(existing?.name.orEmpty())
        dialogBinding.etHost.setText(existing?.hostname.orEmpty())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (existing == null) R.string.dialog_dns_add_title else R.string.dialog_dns_edit_title,
            )
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_save, null) // overridden below to validate first
            .setNegativeButton(R.string.action_cancel, null)
            .show()
            .also { dialog ->
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    saveProfile(dialogBinding, dialog)
                }
                dialogBinding.etName.requestFocus()
            }
    }

    private fun saveProfile(dialogBinding: DialogDnsProfileBinding, dialog: androidx.appcompat.app.AlertDialog) {
        val name = dialogBinding.etName.text?.toString()?.trim().orEmpty()
        val hostRaw = dialogBinding.etHost.text?.toString().orEmpty()

        val check = HostnameValidator.check(hostRaw)
        var valid = true

        if (name.isEmpty()) {
            dialogBinding.tilName.error = getString(R.string.err_name_required)
            valid = false
        } else {
            dialogBinding.tilName.error = null
        }
        if (check is HostnameCheck.Valid) {
            dialogBinding.tilHost.error = null
        } else {
            dialogBinding.tilHost.error = getString((check as HostnameCheck.Invalid).toMessageRes())
            valid = false
        }
        if (!valid) return

        val normalized = (check as? HostnameCheck.Valid)?.normalized ?: hostRaw.trim()
        val current = editing
        if (current == null) {
            repo.add(name, normalized)
        } else {
            val updated = current.copy(name = name, hostname = normalized)
            repo.update(updated)
            if (repo.activeId() == current.id) {
                PrivateDnsManager.applyProfile(requireContext(), updated.hostname)
            }
        }
        dialog.dismiss()
        refresh()
    }

    private fun HostnameCheck.Invalid.toMessageRes(): Int = when (reason) {
        HostnameCheck.Reason.EMPTY -> R.string.err_hostname_empty
        HostnameCheck.Reason.TOO_LONG -> R.string.err_hostname_too_long
        HostnameCheck.Reason.IP_NOT_ALLOWED -> R.string.err_hostname_ip
        HostnameCheck.Reason.BAD_LABEL -> R.string.err_hostname_bad
    }

    private fun showPermissionGuide() {
        SetupDialogs.showPermissionGuide(requireActivity() as AppCompatActivity) { refresh() }
    }

    private fun toast(message: String) =
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
}
