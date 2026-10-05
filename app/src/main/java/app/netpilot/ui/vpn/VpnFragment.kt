package app.netpilot.ui.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import app.netpilot.R
import app.netpilot.core.model.VpnProfile
import app.netpilot.core.model.VpnType
import app.netpilot.core.status.VpnStatusService
import app.netpilot.core.vpn.EngineBridge
import app.netpilot.core.vpn.NetPilotVpnService
import app.netpilot.core.vpn.OvpnConfigParser
import app.netpilot.core.vpn.PlatformVpnController
import app.netpilot.core.vpn.SecureDnsVpnService
import app.netpilot.core.vpn.VpnDataChannel
import app.netpilot.core.vpn.VpnProfileRepository
import app.netpilot.core.vpn.VpnSessionState
import app.netpilot.core.vpn.VpnStatusMonitor
import app.netpilot.core.vpn.WgConfigCheck
import app.netpilot.core.vpn.WireGuardConfBuilder
import app.netpilot.core.vpn.WireGuardManager
import app.netpilot.core.vpn.WireGuardRuntime
import app.netpilot.core.vpn.WireGuardSplitTunnel
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
    private var pendingWgProfile: VpnProfile? = null
    private var suppressUiCallbacks = false
    private var editing: VpnProfile? = null

    /** Per-app (split tunnel) selection being edited in the open dialog. */
    private var perAppSelection: WireGuardSplitTunnel.Selection? = null
    private val connectWatchdog = Handler(Looper.getMainLooper())

    /** True while the watchdog is armed (a platform TUN is expected to appear). */
    private var connectWatchdogArmed = false

    private val consentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                pendingPlatformProfile?.let { p ->
                    stopOtherTunnelsForNewVpn()
                    if (controller.start(p)) {
                        // Ownership proof for the platform tunnel (no in-process
                        // service exists for VpnManager sessions — VpnSessionState
                        // is what keeps "our own IKEv2" out of the foreign bucket).
                        VpnSessionState.markPlatformStarted(requireContext(), p.id, p.name)
                        repo.setLastConnectedId(p.id)
                        toast(getString(R.string.vpn_profile_connected_toast, p.name))
                    } else {
                        toast(getString(R.string.vpn_start_failed))
                    }
                }
                pendingOpenVpnProfile?.let { startOpenVpnService(it) }
                pendingWgProfile?.let { startWireGuardNow(it) }
                pendingPlatformProfile = null
                pendingOpenVpnProfile = null
                pendingWgProfile = null
                refresh()
            } else {
                pendingPlatformProfile = null
                pendingOpenVpnProfile = null
                pendingWgProfile = null
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
                val target = repo.lastConnectedId()?.let { repo.get(it) } ?: repo.list().firstOrNull()
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
        // Recognise our own platform IKEv2 tunnel after process death — or drop
        // the stale marker if the tunnel is gone (reboot / teardown elsewhere).
        VpnSessionState.reconcile(requireContext(), VpnStatusMonitor.isActive(requireContext()))
        refresh()
    }

    override fun onPause() {
        super.onPause()
        disarmConnectWatchdog()
        VpnStatusMonitor.removeListener(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disarmConnectWatchdog()
        _binding = null
    }

    override fun onVpnChanged(active: Boolean, details: VpnStatusMonitor.VpnRuntimeInfo?) {
        if (_binding != null) refresh()
    }

    // ------------------------------------------------------------------ state

    fun refresh() {
        val context = requireContext()
        // Self-heal an abandoned connect attempt before rendering (no screen
        // may sit on "Connecting…" for a tunnel that will never appear).
        VpnSessionState.clearIfExpired(context, VpnStatusMonitor.isActive(context))
        val profiles = repo.list()
        val vpnActive = VpnStatusMonitor.ourVpnActive(context)
        val foreignVpn = VpnStatusMonitor.foreignVpnActive(context)
        val claimed = VpnSessionState.anySessionClaimed(context)
        val tunnelId = VpnSessionState.activeTunnel(context)?.profileId
        val connectedId = tunnelId?.takeIf { id -> profiles.any { p -> p.id == id } }
        val connectingId = if (!vpnActive && claimed) connectedId else null

        suppressUiCallbacks = true
        binding.vpnSwitch.isChecked = vpnActive || (claimed && tunnelId != null)
        when {
            vpnActive -> binding.vpnStatusPill.set(R.string.vpn_status_connected, R.color.status_success)
            claimed -> binding.vpnStatusPill.set(R.string.vpn_status_connecting, R.color.status_info)
            foreignVpn && EngineBridge.primaryEngineInstalled(context) ->
                binding.vpnStatusPill.set(R.string.vpn_status_engine, R.color.status_info)
            foreignVpn -> binding.vpnStatusPill.set(R.string.vpn_status_foreign, R.color.status_info)
            else -> binding.vpnStatusPill.set(R.string.vpn_status_disconnected, R.color.on_surface_variant)
        }
        adapter.submit(profiles, connectedId, connectingId)
        binding.vpnEmpty.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE
        binding.vpnProfilesList.visibility = if (profiles.isEmpty()) View.GONE else View.VISIBLE
        suppressUiCallbacks = false
        app.netpilot.core.status.StatusNotifications.reconcileStatusService(requireContext())
        armConnectWatchdog(vpnActive, claimed)
    }

    /**
     * A platform session is claimed before its TUN exists; if the transport
     * never appears (e.g. server auth failed at the OS layer there is no
     * callback for), the watchdog clears the claim so the UI honestly returns
     * to Disconnected instead of sitting on "Connecting…" forever.
     */
    private fun armConnectWatchdog(vpnActive: Boolean, claimed: Boolean) {
        val context = _binding?.root?.context ?: return
        val platformPending = !vpnActive && claimed &&
            !NetPilotVpnService.isRunning &&
            SecureDnsVpnService.runningHostname == null &&
            VpnSessionState.platformSessionActive(context)
        when {
            platformPending && !connectWatchdogArmed -> {
                connectWatchdogArmed = true
                connectWatchdog.postDelayed({ onConnectWatchdogFired() }, CONNECT_TIMEOUT_MS)
            }
            !platformPending && connectWatchdogArmed -> disarmConnectWatchdog()
        }
    }

    private fun disarmConnectWatchdog() {
        connectWatchdogArmed = false
        connectWatchdog.removeCallbacksAndMessages(null)
    }

    private fun onConnectWatchdogFired() {
        connectWatchdogArmed = false
        val context = _binding?.root?.context ?: return
        if (VpnSessionState.platformSessionActive(context) && !VpnStatusMonitor.isActive(context)) {
            VpnSessionState.markPlatformStopped(context)
            toast(getString(R.string.vpn_start_failed))
        }
        refresh()
    }

    // --------------------------------------------------------------- connect

    private fun onRowActivated(profile: VpnProfile) {
        val active = VpnStatusMonitor.ourVpnActive(requireContext())
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
            VpnType.WIREGUARD -> connectWireGuard(profile)
        }
    }

    /** WireGuard runs INSIDE NetPilot (embedded official engine) — one app. */
    private fun connectWireGuard(profile: VpnProfile) {
        if (profile.wgConfig.isNullOrBlank()) {
            toast(getString(R.string.err_wg_needs_config))
            return
        }
        val prepare = VpnService.prepare(requireContext())
        if (prepare != null) {
            pendingWgProfile = profile
            consentLauncher.launch(prepare)
        } else {
            startWireGuardNow(profile)
        }
    }

    private fun startWireGuardNow(profile: VpnProfile) {
        val context = requireContext()
        // One VPN at a time: make room, then bring up the embedded engine.
        stopOtherTunnelsForNewVpn(silent = true)
        toast(getString(R.string.wg_connecting_toast))
        WireGuardManager.get(context).connect(
            profile,
            onResult = { ok, errRes ->
                if (ok) {
                    repo.setLastConnectedId(profile.id)
                    toast(getString(R.string.wg_started_toast, profile.name))
                } else {
                    toast(getString(errRes))
                }
                refresh()
            },
            onHandshake = { confirmed ->
                toast(
                    getString(
                        if (confirmed) R.string.wg_handshake_ok else R.string.wg_no_handshake,
                    ),
                )
            },
        )
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
            stopOtherTunnelsForNewVpn()
            VpnSessionState.markPlatformStarted(requireContext(), profile.id, profile.name)
            repo.setLastConnectedId(profile.id)
            toast(getString(R.string.vpn_profile_connected_toast, profile.name))
            refresh()
        } else {
            toast(getString(R.string.vpn_start_failed))
        }
    }

    /**
     * Android enforces one active VPN system-wide; make room for the new
     * NetPilot tunnel by stopping the other tunnels we own. Never called
     * before the replacement is actually starting, so cancelling a consent
     * dialog never tears down a working tunnel.
     */
    private fun stopOtherTunnelsForNewVpn(silent: Boolean = false) {
        val context = requireContext()
        if (VpnSessionState.platformSessionActive(context)) {
            controller.stop()
            VpnSessionState.markPlatformStopped(context)
        }
        if (NetPilotVpnService.isRunning) {
            context.startService(
                Intent(context, NetPilotVpnService::class.java)
                    .setAction(NetPilotVpnService.ACTION_DISCONNECT),
            )
        }
        if (SecureDnsVpnService.runningHostname != null) {
            context.startService(
                Intent(context, SecureDnsVpnService::class.java)
                    .setAction(SecureDnsVpnService.ACTION_STOP),
            )
            if (!silent) toast(getString(R.string.secure_dns_vpn_replaced))
        }
        if (WireGuardRuntime.isRunning) {
            WireGuardManager.get(context).disconnect()
        }
    }

    private fun connectOpenVpn(profile: VpnProfile) {
        if (profile.ovpnConfig.isNullOrBlank()) {
            toast(getString(R.string.err_no_ovpn))
            return
        }
        // A bundled engine module (optional, not shipped) takes the in-app path.
        if (VpnDataChannel.factory != null) {
            val prepare = VpnService.prepare(requireContext())
            if (prepare != null) {
                pendingOpenVpnProfile = profile
                consentLauncher.launch(prepare)
            } else {
                startOpenVpnService(profile)
            }
            return
        }
        // Standard path: drive the official open-source engine app through its
        // documented external control API (works on Android 9/10, where the
        // platform IKEv2 API does not exist).
        showEngineBridge(profile)
    }

    /** Import / connect flow for the external OpenVPN engine app. */
    private fun showEngineBridge(profile: VpnProfile) {
        val context = requireContext()
        val name = profile.name
        val config = profile.ovpnConfig ?: return
        if (!EngineBridge.primaryEngineInstalled(context)) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.engine_bridge_title)
                .setMessage(R.string.engine_install_msg)
                .setPositiveButton(R.string.engine_install_btn) { _, _ ->
                    if (!EngineBridge.launchInstall(context)) {
                        toast(getString(R.string.engine_handoff_failed))
                    }
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
            return
        }
        val message = getString(R.string.engine_import_hint, name) + "\n\n" +
            getString(R.string.engine_connect_hint, name)
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.engine_bridge_title)
            .setMessage(message)
            .setPositiveButton(R.string.engine_import_btn) { _, _ ->
                if (EngineBridge.launchImport(context, name, config)) {
                    toast(getString(R.string.engine_handoff_ok))
                } else {
                    toast(getString(R.string.engine_handoff_failed))
                }
            }
            .setNeutralButton(R.string.engine_connect_btn) { _, _ ->
                // Make room: Android allows one active VPN; our tunnels and the
                // engine's cannot coexist (system strict Private DNS can).
                stopOtherTunnelsForNewVpn(silent = true)
                if (EngineBridge.launchConnect(context, name)) {
                    toast(getString(R.string.engine_connect_sent))
                    repo.setLastConnectedId(profile.id)
                } else {
                    toast(getString(R.string.engine_connect_missing))
                }
                refresh()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun startOpenVpnService(profile: VpnProfile) {
        stopOtherTunnelsForNewVpn()
        val intent = Intent(requireContext(), NetPilotVpnService::class.java)
            .setAction(NetPilotVpnService.ACTION_CONNECT)
            .putExtra(NetPilotVpnService.EXTRA_OVPN, profile.ovpnConfig)
            .putExtra(NetPilotVpnService.EXTRA_SESSION, profile.name)
            .putExtra(NetPilotVpnService.EXTRA_PROFILE_ID, profile.id)
        requireContext().startService(intent)
        repo.setLastConnectedId(profile.id)
        refresh()
    }

    private fun stopAll() {
        val context = requireContext()
        val hadOurs = VpnSessionState.anySessionClaimed(context)
        // Stop every tunnel we own — the platform session (a no-op when none),
        // the OpenVPN transport and the Secure-DNS tunnel.
        controller.stop()
        VpnSessionState.markPlatformStopped(context)
        // Only wake the transport service when it is actually running —
        // launching it just to say "disconnect" costs a process start.
        if (NetPilotVpnService.isRunning) {
            context.startService(
                Intent(context, NetPilotVpnService::class.java)
                    .setAction(NetPilotVpnService.ACTION_DISCONNECT),
            )
        }
        if (SecureDnsVpnService.runningHostname != null) {
            context.startService(
                Intent(context, SecureDnsVpnService::class.java)
                    .setAction(SecureDnsVpnService.ACTION_STOP),
            )
        }
        if (WireGuardRuntime.isRunning) {
            WireGuardManager.get(context).disconnect()
        }
        // If the active tunnel belongs to the engine app, ask IT to stop too —
        // DisconnectVPN only ever touches the engine's own VPN.
        if (VpnStatusMonitor.foreignVpnActive(context)) {
            EngineBridge.launchDisconnect(context)
        }
        VpnStatusService.stop(context)
        repo.setLastConnectedId(null)
        when {
            hadOurs -> toast(getString(R.string.vpn_profile_stopped_toast))
            VpnStatusMonitor.foreignVpnActive(context) ->
                toast(getString(R.string.vpn_foreign_active_toast))
            else -> toast(getString(R.string.vpn_not_active_toast))
        }
        refresh()
    }

    // --------------------------------------------------------------- dialogs

    private fun onDeleteClicked(profile: VpnProfile) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_profile_title)
            .setMessage(getString(R.string.delete_vpn_profile_msg, profile.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                val tunnel = VpnSessionState.activeTunnel(requireContext())
                repo.delete(profile.id)
                if (tunnel?.profileId == profile.id) {
                    // Deleting the connected profile also tears its tunnel down.
                    stopAll()
                } else {
                    refresh()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    fun showProfileDialog(existing: VpnProfile?) {
        editing = existing
        perAppSelection = null
        val dialogBinding = DialogVpnProfileBinding.inflate(layoutInflater)
        val segment = dialogBinding.vpnTypeSegment

        val initialType = existing?.type ?: VpnType.defaultFor(android.os.Build.VERSION.SDK_INT)
        var currentType = initialType
        fun renderFields(type: VpnType) {
            currentType = type
            // Per-type, per-device availability hint (the add-ons feel native).
            val hintRes = when {
                type == VpnType.PLATFORM_IKEV2 && android.os.Build.VERSION.SDK_INT < 30 ->
                    R.string.hint_ikev2_needs_11
                type == VpnType.PLATFORM_IKEV2 -> R.string.hint_ikev2_native
                type == VpnType.WIREGUARD -> R.string.hint_wg_embedded
                // OpenVPN: embedded engine when this build ships the native
                // library; honest bridge guidance when it does not.
                type == VpnType.OPENVPN &&
                    app.netpilot.openvpn.core.OvpnCoreAvailability.isAvailable ->
                    R.string.hint_ovpn_engine
                type == VpnType.OPENVPN -> R.string.hint_ovpn_bridge
                else -> R.string.hint_ovpn_bridge
            }
            dialogBinding.vpnTypeHint.setText(hintRes)
            dialogBinding.vpnTypeHint.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    requireContext(),
                    if (type == VpnType.PLATFORM_IKEV2 && android.os.Build.VERSION.SDK_INT < 30) {
                        R.color.status_warning
                    } else {
                        R.color.on_surface_variant
                    },
                ),
            )
            dialogBinding.tilPort.visibility =
                if (type == VpnType.OPENVPN || type == VpnType.WIREGUARD) View.VISIBLE else View.GONE
            val wgSection = type == VpnType.WIREGUARD
            dialogBinding.tilWgAddress.visibility = if (wgSection) View.VISIBLE else View.GONE
            dialogBinding.wgClientKeyRow.visibility = if (wgSection) View.VISIBLE else View.GONE
            dialogBinding.tilWgPeerKey.visibility = if (wgSection) View.VISIBLE else View.GONE
            dialogBinding.tilWgAllowedIps.visibility = if (wgSection) View.VISIBLE else View.GONE
            if (wgSection && dialogBinding.etWgAllowedIps.text.isNullOrBlank()) {
                dialogBinding.etWgAllowedIps.setText("0.0.0.0/0, ::/0")
            }
            // Per-app VPN (v2.2.0): only for saved WireGuard profiles — the
            // selection edits the existing .conf.
            val perAppUsable = wgSection && editing?.wgConfig != null
            dialogBinding.btnPerApp.visibility = if (perAppUsable) View.VISIBLE else View.GONE
            dialogBinding.tvPerApp.visibility = if (perAppUsable) View.VISIBLE else View.GONE
            updatePerAppCaption(dialogBinding)
            dialogBinding.btnImportOvpn.visibility = View.VISIBLE
            dialogBinding.tilUsername.visibility =
                if (type == VpnType.PLATFORM_IKEV2) View.VISIBLE else View.GONE
            dialogBinding.tilPassword.visibility =
                if (type == VpnType.PLATFORM_IKEV2) View.VISIBLE else View.GONE
            dialogBinding.tilPsk.visibility =
                if (type == VpnType.PLATFORM_IKEV2) View.VISIBLE else View.GONE
            dialogBinding.btnImportOvpn.setText(
                when (type) {
                    VpnType.OPENVPN -> R.string.btn_import_ovpn
                    VpnType.WIREGUARD -> R.string.btn_import_wg
                    VpnType.PLATFORM_IKEV2 -> R.string.btn_import_ca
                },
            )
        }

        segment.configure(
            items = listOf(
                getString(R.string.vpn_type_ikev2),
                getString(R.string.vpn_type_openvpn),
                getString(R.string.vpn_type_wireguard),
            ),
            initialIndex = when (initialType) {
                VpnType.PLATFORM_IKEV2 -> 0
                VpnType.OPENVPN -> 1
                VpnType.WIREGUARD -> 2
            },
            listener = object : SegmentedToggle.OnSelectionListener {
                override fun onSelected(index: Int) {
                    renderFields(if (index == 0) VpnType.PLATFORM_IKEV2 else VpnType.OPENVPN)
                }
            },
        )
        renderFields(initialType)

        // On-device client key generation for the manual (own-server) flow.
        dialogBinding.btnPerApp.setOnClickListener { showPerAppDialog() }
        dialogBinding.btnWgGenerate.setOnClickListener {
            val (priv, pub) = WireGuardConfBuilder.generateKeyPair()
            dialogBinding.etWgClientKey.setText(priv)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.action_wg_generate)
                .setMessage(getString(R.string.wg_public_key_show, pub))
                .setPositiveButton(R.string.action_copy) { _, _ ->
                    val clipboard =
                        requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText("wg pubkey", pub),
                    )
                    toast(getString(R.string.export_ok))
                }
                .setNegativeButton(R.string.action_ok, null)
                .show()
        }

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
            // Dedicated launchers — the callback always knows what it is
            // handling, so a stale toggle index can never cross the streams.
            when (currentType) {
                VpnType.OPENVPN -> ovpnPicker.launch(arrayOf("*/*"))
                VpnType.WIREGUARD -> wgPicker.launch(arrayOf("*/*"))
                VpnType.PLATFORM_IKEV2 -> caPicker.launch(arrayOf("*/*"))
            }
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
                    val type = when (segment.selectedIndex) {
                        0 -> VpnType.PLATFORM_IKEV2
                        1 -> VpnType.OPENVPN
                        else -> VpnType.WIREGUARD
                    }
                    saveProfile(
                        dialogBinding, type,
                        lastImportedConfig, lastImportedSummary, lastImportedCaPem,
                        lastImportedWg,
                        dialog,
                    )
                    lastImportedConfig = null
                    lastImportedSummary = null
                    lastImportedCaPem = null
                    lastImportedWg = null
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
                toast(getString(R.string.err_ovpn_unusable))
            }
        }

    private val caPicker =
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
            val pem = app.netpilot.core.vpn.CaCertParser.normalizeToPem(bytes)
            if (pem == null) {
                toast(getString(R.string.err_ca_invalid))
            } else {
                lastImportedCaPem = pem
                toast(getString(R.string.ca_imported_ok))
            }
        }

    private var activeVpnDialogBinding: DialogVpnProfileBinding? = null
    private var lastImportedConfig: String? = null
    private var lastImportedSummary: String? = null
    private var lastImportedCaPem: String? = null
    private var lastImportedWg: String? = null

    private val wgPicker =
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
            val text = bytes.toString(Charsets.UTF_8)
            if (!WgConfigCheck.isPlausible(text)) {
                toast(getString(R.string.err_wg_invalid))
                return@registerForActivityResult
            }
            lastImportedWg = text
            WgConfigCheck.endpointOf(text)?.let { (host, port) ->
                dialogBinding.etServer.setText(host)
                if (dialogBinding.etPort.text.isNullOrBlank()) {
                    dialogBinding.etPort.setText(port.toString())
                }
            }
            toast(getString(R.string.wg_imported_ok))
        }

    // ---- Per-app (split) tunneling, v2.2.0 -------------------------------

    private fun updatePerAppCaption(dialogBinding: DialogVpnProfileBinding) {
        val sel = perAppSelection ?: editing?.wgConfig?.let { WireGuardSplitTunnel.read(it) }
        dialogBinding.tvPerApp.text = when (sel?.mode) {
            WireGuardSplitTunnel.Mode.INCLUDE ->
                getString(R.string.per_app_count, sel.packages.size) + " · " + getString(R.string.per_app_mode_include)
            WireGuardSplitTunnel.Mode.EXCLUDE ->
                getString(R.string.per_app_count, sel.packages.size) + " · " + getString(R.string.per_app_mode_exclude)
            else -> getString(R.string.per_app_mode_all)
        }
    }

    /**
     * Multi-select picker. Decisive save buttons instead of a mode radio:
     * "Exclude selected" and "Include selected" — WireGuard permits exactly
     * one of the two, so the UI can't produce a contradictory config.
     */
    private fun showPerAppDialog() {
        val conf = editing?.wgConfig ?: return
        val apps = launchableApps()
        if (apps.isEmpty()) {
            toast(getString(R.string.per_app_none))
            return
        }
        val current = perAppSelection ?: WireGuardSplitTunnel.read(conf)
        val checked = apps.map { it.first in current.packages }.toBooleanArray()
        val labels = apps.map { it.second }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.per_app_title)
            .setMessage(R.string.per_app_hint)
            .setMultiChoiceItems(labels, checked) { _, _, _ -> }
            .setPositiveButton(R.string.per_app_exclude) { d, _ ->
                val picked = pickedPackages(d as androidx.appcompat.app.AlertDialog, apps)
                if (picked.isEmpty()) {
                    perAppSelection = WireGuardSplitTunnel.Selection(WireGuardSplitTunnel.Mode.ALL, emptyList())
                } else {
                    perAppSelection = WireGuardSplitTunnel.Selection(WireGuardSplitTunnel.Mode.EXCLUDE, picked)
                }
                activeVpnDialogBinding?.let { updatePerAppCaption(it) }
            }
            .setNeutralButton(R.string.per_app_include) { d, _ ->
                val picked = pickedPackages(d as androidx.appcompat.app.AlertDialog, apps)
                if (picked.isEmpty()) {
                    perAppSelection = WireGuardSplitTunnel.Selection(WireGuardSplitTunnel.Mode.ALL, emptyList())
                } else {
                    perAppSelection = WireGuardSplitTunnel.Selection(WireGuardSplitTunnel.Mode.INCLUDE, picked)
                }
                activeVpnDialogBinding?.let { updatePerAppCaption(it) }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun pickedPackages(dialog: androidx.appcompat.app.AlertDialog, apps: List<Pair<String, String>>): List<String> {
        val lv = dialog.listView
        return apps.filterIndexed { i, _ -> lv.isItemChecked(i) }.map { it.first }
    }

    /** Launchable user apps (D-pad friendly labels, alphabetical). */
    private fun launchableApps(): List<Pair<String, String>> {
        val pm = requireContext().packageManager
        val main = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        return runCatching {
            pm.queryIntentActivities(main, 0)
                .mapNotNull { ri ->
                    val pkg = ri.activityInfo?.applicationInfo?.packageName ?: return@mapNotNull null
                    if (pkg == requireContext().packageName) return@mapNotNull null
                    pkg to (ri.loadLabel(pm)?.toString().orEmpty().ifBlank { pkg })
                }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        }.getOrDefault(emptyList())
    }

    private fun saveProfile(
        dialogBinding: DialogVpnProfileBinding,
        type: VpnType,
        importedConfig: String?,
        importedSummary: String?,
        importedCaPem: String?,
        importedWg: String?,
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
            // OpenVPN profiles carry their full tunnel config; manual fields alone
            // cannot produce one (and this build ships no engine module).
            dialogBinding.tilServer.error = getString(R.string.err_openvpn_needs_config)
            valid = false
        }

        var wgConf = importedWg ?: editing?.wgConfig
        if (type == VpnType.WIREGUARD) {
            if (wgConf.isNullOrBlank()) {
                // Direct field input: assemble a real .conf from the fields
                // (client key can be generated in-dialog). Endpoint comes from
                // the server + port fields.
                val assembled = WireGuardConfBuilder.assembleOrNull(
                    address = dialogBinding.etWgAddress.text?.toString().orEmpty(),
                    clientKey = dialogBinding.etWgClientKey.text?.toString().orEmpty(),
                    peerKey = dialogBinding.etWgPeerKey.text?.toString().orEmpty(),
                    endpointHost = server,
                    endpointPort = port,
                    allowedIps = dialogBinding.etWgAllowedIps.text?.toString().orEmpty(),
                )
                if (assembled == null) {
                    dialogBinding.tilServer.error = getString(R.string.err_wg_manual_fields)
                    valid = false
                } else {
                    wgConf = assembled
                    toast(getString(R.string.wg_manual_assembled))
                }
            } else if (!WgConfigCheck.isPlausible(wgConf)) {
                dialogBinding.tilServer.error = getString(R.string.err_wg_invalid)
                valid = false
            }
            // Per-app selection (v2.2.0): write Included/ExcludedApplications
            // into the conf. Invalid selection → save blocked, honest error.
            perAppSelection?.let { sel ->
                val source = wgConf ?: editing?.wgConfig
                if (source != null) {
                    val applied = WireGuardSplitTunnel.apply(source, sel)
                    if (applied == null) {
                        dialogBinding.tilServer.error = getString(R.string.err_per_app)
                        valid = false
                    } else {
                        wgConf = applied
                    }
                }
            }
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
                    wgConfig = wgConf,
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
                    wgConfig = wgConf,
                ),
            )
        }
        dialog.dismiss()
        refresh()
    }

    private fun toast(message: String) =
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()

    private companion object {
        /** How long a claimed platform session may wait for its TUN to appear. */
        const val CONNECT_TIMEOUT_MS = 10_000L
    }
}
