package app.netpilot.ui.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import app.netpilot.BuildConfig
import app.netpilot.R
import app.netpilot.core.dns.PrivateDnsManager
import app.netpilot.core.dns.DnsProfileRepository
import app.netpilot.core.prefs.AppPreferences
import app.netpilot.core.prefs.ThemeMode
import app.netpilot.core.profiles.ProfileBackup
import app.netpilot.core.vpn.VpnProfileRepository
import app.netpilot.databinding.FragmentSettingsBinding
import app.netpilot.ui.licenses.LicensesActivity
import app.netpilot.ui.dialogs.SetupDialogs
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: AppPreferences
    private lateinit var dnsRepo: DnsProfileRepository
    private lateinit var vpnRepo: VpnProfileRepository

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(ProfileBackup.MIME_TYPE)) { uri ->
            if (uri == null) return@registerForActivityResult
            val ok = runCatching {
                requireContext().contentResolver.openOutputStream(uri)?.use {
                    it.write(ProfileBackup.export(dnsRepo, vpnRepo).toByteArray())
                } != null
            }.getOrDefault(false)
            toast(getString(if (ok) R.string.export_ok else R.string.export_err))
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val text = runCatching {
                requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            val count = text?.let {
                runCatching { ProfileBackup.import(it, dnsRepo, vpnRepo) }.getOrDefault(0)
            } ?: 0
            if (count > 0) toast(getString(R.string.import_ok, count)) else toast(getString(R.string.import_err))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPreferences(requireContext())
        dnsRepo = DnsProfileRepository(requireContext())
        vpnRepo = VpnProfileRepository(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rowTheme.setOnClickListener { showThemeDialog() }
        binding.rowSecurity.setOnClickListener {
            SetupDialogs.showPermissionGuide(requireActivity() as AppCompatActivity) { refresh() }
        }
        binding.rowExport.setOnClickListener { exportLauncher.launch(ProfileBackup.SUGGESTED_NAME) }
        binding.rowImport.setOnClickListener {
            importLauncher.launch(arrayOf(ProfileBackup.MIME_TYPE, "text/plain", "application/octet-stream"))
        }
        binding.rowPrivacy.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.privacy_title)
                .setMessage(R.string.privacy_body)
                .setPositiveButton(R.string.action_ok, null)
                .show()
        }
        binding.rowLicenses.setOnClickListener {
            startActivity(Intent(requireContext(), LicensesActivity::class.java))
        }
        binding.rowAbout.setOnClickListener { showAboutDialog() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun refresh() {
        val granted = PrivateDnsManager.hasWritePermission(requireContext())
        binding.rowSecurity.setValue(getString(if (granted) R.string.security_value_granted else R.string.security_value_missing))
        binding.rowSecurity.setValueColorRes(if (granted) R.color.status_success else R.color.status_warning)
        binding.rowTheme.setValue(getString(prefs.theme.labelRes))
    }

    private fun showThemeDialog() {
        val entries = ThemeMode.entries.toTypedArray()
        val labels = entries.map { getString(it.labelRes) }.toTypedArray()
        val checked = entries.indexOf(prefs.theme)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.row_theme)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                prefs.theme = entries[which]
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(entries[which].nightMode)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showAboutDialog() {
        val message = getString(
            R.string.about_body_fmt,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT,
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.about_title)
            .setMessage(message + "\n\n" + getString(R.string.about_repo))
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }

    private fun toast(message: String) =
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
}
