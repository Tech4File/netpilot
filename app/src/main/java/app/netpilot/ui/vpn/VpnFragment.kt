package app.netpilot.ui.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import app.netpilot.R
import app.netpilot.core.model.VpnProfile
import app.netpilot.core.model.VpnType
import app.netpilot.core.vpn.NetPilotVpnService
import app.netpilot.core.vpn.OvpnConfigParser
import app.netpilot.core.vpn.PlatformVpnController
import app.netpilot.core.vpn.VpnDataChannel
import app.netpilot.core.vpn.VpnProfileRepository
import app.netpilot.core.vpn.VpnStatusMonitor
import app.netpilot.databinding.DialogVpnProfileBinding
import app.netpilot.databinding.FragmentVpnBinding
import app.netpilot.ui.components.SegmentedToggle
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * VPN profile management. Android allows one active VPN system-wide; NetPilot
 * reflects that: connecting a profile replaces the previous one.
 */
class VpnFragment : Fragment(), VpnStatusMonitor.Listener {

    private var _binding: FragmentVpnBinding? = null
    private val binding get() = _binding!!

    private lateinit var repo: VpnProfileRepository
    private lateinit var controller: PlatformVpnController
    private lateinit var adapter: VpnProfilesAdapter
    private var pendingPlatformProfile: VpnProfile? = null
    private var pendingOpenVpnProfile: VpnProfile? = null
    private var suppressUiCallbacks = false
    private var editing: VpnProfile? = null

    private val consentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                pendingPlatformProfile?.let { p ->
                    if (controller.start(p)) {
                        repo.setLastConnectedId(p.id)
                        toast(getString(R.string.vpn_profile_connected_toast, p.name))
                    } else {
                        toast(getString(R.string.vpn_start_failed))
                    }
                }
                pendingOpenVpnProfile?.let { startOpenVpnService(it) }
                pendingPlatformProfile = null
                pendingOpenVpnProfile = null
                refresh()
            } else {
                pendingPlatformProfile = null
                pendingOpenVpnProfile = null
                toast(getString(R.string.vpn_consent_failed))
                refresh()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = VpnProfileRepository(requireContext())
        controller = PlatformVpnController(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentVpnBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = VpnProfilesAdapter(
            onRowClick = ::onRowActivated,
            onEdit = ::showProfileDialog,
            onDelete = ::onDeleteClicked,
        )
        binding.vpnProfilesList.layoutManager = LinearLayoutManager(requireContext())
        binding.vpnProfilesList.adapter = adapter

        binding.vpnSwitch.setOnCheckedChangeListener { _, checked ->
            if (suppressUiCallbacks) return@setOnCheckedChangeListener
            if (checked) {
                val target = repo.list().firstOrNull()
                if (target == null) {
                    refresh()
                    showProfileDialog(null)
                } else {
                    connect(target)
                }
            } else {
                stopAll()
            }
        }
        binding.vpnAddRow.setOnClickListener { showProfileDialog(null) }
    }

    override fun onResume() {
        super.onResume()
        VpnStatusMonitor.addListener(this)
        refresh()
    }

    override fun onPause() {
        super.onPause()
        VpnStatusMonitor.removeListener(this)
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
        val profiles = repo.list()
        val vpnActive = VpnStatusMonitor.isActive(context)
        val connectedId = repo.lastConnectedId()?.takeIf { vpnActive && profiles.any { p -> p.id == it } }

        suppressUiCallbacks = true
        binding.vpnSwitch.isChecked = vpnActive
        if (vpnActive) {
            binding.vpnStatusPill.set(R.string.vpn_status_connected, R.color.status_success)
        } else {
            binding.vpnStatusPill.set(R.string.vpn_status_disconnected, R.color.on_surface_variant)
        }
        adapter.submit(profiles, connectedId)
        binding.vpnEmpty.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE
        binding.vpnProfilesList.visibility = if (profiles.isEmpty()) View.GONE else View.VISIBLE
        suppressUiCallbacks = false
    }

    // --------------------------------------------------------------- connect

    private fun onRowActivated(profile: VpnProfile) {
        val active = VpnStatusMonitor.isActive(requireContext())
        if (active && repo.lastConnectedId() == profile.id) {
            stopAll()
        } else {
            connect(profile)
        }
    }

    private fun connect(profile: VpnProfile) {
        when (profile.type) {
            VpnType.PLATFORM_IKEV2 -> connectPlatform(profile)
            VpnType.OPENVPN -> connectOpenVpn(profile)
        }
    }

    private fun connectPlatform(profile: VpnProfile) {
        if (!PlatformVpnController.isSupported()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.vpn_unsupported_device)
                .setMessage(R.string.vpn_unsupported_device_msg)
                .setPositiveButton(R.string.action_ok, null)
                .show()
            return
        }
        val consent = controller.provisionIntent(profile)
        if (consent != null) {
            pendingPlatformProfile = profile
            consentLauncher.launch(consent)
        } else if (controller.start(profile)) {
            repo.setLastConnectedId(profile.id)
            toast(getString(R.string.vpn_profile_connected_toast, profile.name))
            refresh()
        } else {
            toast(getString(R.string.vpn_start_failed))
        }
    }

    private fun connectOpenVpn(profile: VpnProfile) {
        if (VpnDataChannel.factory == null) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ovpn_engine_title)
                .setMessage(getString(R.string.ovpn_engine_msg))
                .setPositiveButton(R.string.action_ok, null)
                .show()
            return
        }
        if (profile.ovpnConfig.isNullOrBlank()) {
            toast(getString(R.string.err_no_ovpn))
            return
        }
        val prepare = VpnService.prepare(requireContext())
        if (prepare != null) {
            pendingOpenVpnProfile = profile
            consentLauncher.launch(prepare)
        } else {
            startOpenVpnService(profile)
        }
    }

    private fun startOpenVpnService(profile: VpnProfile) {
        val intent = Intent(requireContext(), NetPilotVpnService::class.java)
            .setAction(NetPilotVpnService.ACTION_CONNECT)
            .putExtra(NetPilotVpnService.EXTRA_OVPN, profile.ovpnConfig)
            .putExtra(NetPilotVpnService.EXTRA_SESSION, profile.name)
        requireContext().startService(intent)
        repo.setLastConnectedId(profile.id)
        refresh()
    }

    private fun stopAll() {
        controller.stop()
        requireContext().startService(
            Intent(requireContext(), NetPilotVpnService::class.java)
                .setAction(NetPilotVpnService.ACTION_DISCONNECT),
        )
        repo.setLastConnectedId(null)
        toast(getString(R.string.vpn_profile_stopped_toast))
        refresh()
    }

    // --------------------------------------------------------------- dialogs

    private fun onDeleteClicked(profile: VpnProfile) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_profile_title)
            .setMessage(getString(R.string.delete_vpn_profile_msg, profile.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                repo.delete(profile.id)
                refresh()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    fun showProfileDialog(existing: VpnProfile?) {
        editing = existing
        val dialogBinding = DialogVpnProfileBinding.inflate(layoutInflater)
        val segment = dialogBinding.vpnTypeSegment

        fun renderFields(type: VpnType) {
            dialogBinding.tilPort.visibility =
                if (type == VpnType.OPENVPN) View.VISIBLE else View.GONE
            dialogBinding.btnImportOvpn.visibility = View.VISIBLE
            dialogBinding.tilUsername.visibility =
                if (type == VpnType.PLATFORM_IKEV2) View.VISIBLE else View.GONE
            dialogBinding.tilPassword.visibility =
                if (type == VpnType.PLATFORM_IKEV2) View.VISIBLE else View.GONE
            dialogBinding.tilPsk.visibility =
                if (type == VpnType.PLATFORM_IKEV2) View.VISIBLE else View.GONE
            dialogBinding.btnImportOvpn.setText(
                if (type == VpnType.OPENVPN) R.string.btn_import_ovpn else R.string.btn_import_ca,
            )
        }

        val initialType = existing?.type ?: VpnType.PLATFORM_IKEV2
        segment.configure(
            items = listOf(getString(R.string.vpn_type_ikev2), getString(R.string.vpn_type_openvpn)),
            initialIndex = if (initialType == VpnType.PLATFORM_IKEV2) 0 else 1,
            listener = object : SegmentedToggle.OnSelectionListener {
                override fun onSelected(index: Int) {
                    renderFields(if (index == 0) VpnType.PLATFORM_IKEV2 else VpnType.OPENVPN)
                }
            },
        )
        renderFields(initialType)

        dialogBinding.etName.setText(existing?.name.orEmpty())
        dialogBinding.etServer.setText(existing?.serverHost.orEmpty())
        dialogBinding.etPort.setText(existing?.serverPort?.toString() ?: "1194")
        dialogBinding.etUsername.setText(existing?.username.orEmpty())
        // Mask credential fields in code as well as via the XML inputType —
        // masking must be provable at the call site (CodeQL sensitive-text).
        // Secrets are NEVER loaded into the UI (CodeQL sensitive-text, and the
        // same policy as password managers): on edit, credential fields start
        // empty and blank means "keep the stored value" (see saveProfile).
        if (existing != null) {
            dialogBinding.etPassword.hint = getString(R.string.vpn_hint_keep_password)
            dialogBinding.etPsk.hint = getString(R.string.vpn_hint_keep_psk)
        }

        dialogBinding.btnImportOvpn.setOnClickListener {
            activeVpnDialogBinding = dialogBinding
            importIsOpenVpn = segment.selectedIndex == 1
            ovpnPicker.launch(
                if (importIsOpenVpn) {
                    arrayOf("application/openvpn-profile", "application/x-openvpn-profile", "text/plain", "application/octet-stream")
                } else {
                    arrayOf("application/x-x509-ca-cert", "application/pem-certificate-chain", "text/plain", "application/octet-stream")
                },
            )
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (existing == null) R.string.vpn_dialog_add_title else R.string.vpn_dialog_edit_title,
            )
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.action_cancel, null)
            .show()
            .also { dialog ->
                dialog.setOnDismissListener { activeVpnDialogBinding = null }
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val type = if (segment.selectedIndex == 0) VpnType.PLATFORM_IKEV2 else VpnType.OPENVPN
                    saveProfile(
                        dialogBinding, type,
                        lastImportedConfig, lastImportedSummary, lastImportedCaPem,
                        dialog,
                    )
                    lastImportedConfig = null
                    lastImportedSummary = null
                    lastImportedCaPem = null
                }
                dialogBinding.etName.requestFocus()
            }
    }

    private val ovpnPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val dialogBinding = activeVpnDialogBinding
            if (uri == null || dialogBinding == null) return@registerForActivityResult
            val bytes = runCatching {
                requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes == null) {
                toast(getString(R.string.import_err))
                return@registerForActivityResult
            }
            if (!importIsOpenVpn) {
                val pem = app.netpilot.core.vpn.CaCertParser.normalizeToPem(bytes)
                if (pem == null) {
                    toast(getString(R.string.err_ca_invalid))
                } else {
                    lastImportedCaPem = pem
                    toast(getString(R.string.ca_imported_ok))
                }
                return@registerForActivityResult
            }
            val config = OvpnConfigParser.parse(bytes.toString(Charsets.UTF_8))
            if (config.isUsableForProfile()) {
                lastImportedConfig = bytes.toString(Charsets.UTF_8)
                lastImportedSummary = config.primaryEndpoint()
                val remote = config.remotes.first()
                dialogBinding.etServer.setText(remote.host)
                if (dialogBinding.etPort.text.isNullOrBlank()) {
                    dialogBinding.etPort.setText(remote.port.toString())
                }
                toast(getString(R.string.ovpn_imported_ok, remote.host, remote.port.toString()))
            } else {
                lastImportedConfig = null
                lastImportedSummary = null
                toast(getString(R.string.err_no_ovpn))
            }
        }

    private var activeVpnDialogBinding: DialogVpnProfileBinding? = null
    private var lastImportedConfig: String? = null
    private var lastImportedSummary: String? = null
    private var lastImportedCaPem: String? = null
    private var importIsOpenVpn: Boolean = true

    private fun saveProfile(
        dialogBinding: DialogVpnProfileBinding,
        type: VpnType,
        importedConfig: String?,
        importedSummary: String?,
        importedCaPem: String?,
        dialog: androidx.appcompat.app.AlertDialog,
    ) {
        val name = dialogBinding.etName.text?.toString()?.trim().orEmpty()
        val server = dialogBinding.etServer.text?.toString()?.trim().orEmpty()
        val port = dialogBinding.etPort.text?.toString()?.trim()?.toIntOrNull() ?: 1194
        // Blank credential fields mean "keep the stored value" (edit mode).
        val currentProfile = editing
        val psk = dialogBinding.etPsk.text?.toString().orEmpty()
            .ifBlank { currentProfile?.preSharedKey.orEmpty() }

        var valid = true
        if (name.isEmpty()) {
            dialogBinding.tilName.error = getString(R.string.err_name_required)
            valid = false
        } else dialogBinding.tilName.error = null

        if (server.isEmpty()) {
            dialogBinding.tilServer.error = getString(R.string.err_server_invalid)
            valid = false
        } else dialogBinding.tilServer.error = null

        if (port !in 1..65535) {
            dialogBinding.tilPort.error = getString(R.string.err_port_invalid)
            valid = false
        } else dialogBinding.tilPort.error = null

        if (type == VpnType.OPENVPN && importedConfig.isNullOrBlank() && (editing?.ovpnConfig).isNullOrBlank()) {
            dialogBinding.tilServer.error = getString(R.string.err_no_ovpn)
            valid = false
        }

        val caCert = importedCaPem ?: editing?.caCertPem
        if (type == VpnType.PLATFORM_IKEV2 && psk.isBlank() && caCert.isNullOrBlank()) {
            toast(getString(R.string.ikev2_need_ca))
            return
        }
        if (!valid) return

        val config = importedConfig ?: editing?.ovpnConfig
        val summary = importedSummary ?: editing?.ovpnSummary
        val authType = if (psk.isNotBlank()) app.netpilot.core.model.VpnAuthType.PSK else app.netpilot.core.model.VpnAuthType.USER_PASS

        val current = currentProfile
        if (current == null) {
            repo.add(
                VpnProfile(
                    id = "",
                    name = name,
                    type = type,
                    serverHost = server,
                    serverPort = port,
                    authType = authType,
                    username = dialogBinding.etUsername.text?.toString()?.trim().orEmpty(),
                    password = dialogBinding.etPassword.text?.toString().orEmpty(),
                    preSharedKey = psk,
                    caCertPem = caCert,
                    ovpnConfig = config,
                    ovpnSummary = summary,
                ),
            )
        } else {
            repo.update(
                current.copy(
                    name = name,
                    type = type,
                    serverHost = server,
                    serverPort = port,
                    authType = authType,
                    username = dialogBinding.etUsername.text?.toString()?.trim().orEmpty(),
                    password = dialogBinding.etPassword.text?.toString().orEmpty().ifBlank { current.password },
                    preSharedKey = psk,
                    caCertPem = caCert,
                    ovpnConfig = config,
                    ovpnSummary = summary,
                ),
            )
        }
        dialog.dismiss()
        refresh()
    }

    private fun toast(message: String) =
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
}
