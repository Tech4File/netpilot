package app.netpilot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import app.netpilot.core.prefs.AppPreferences
import app.netpilot.core.status.StatusNotifications
import app.netpilot.core.vpn.VpnSessionState
import app.netpilot.core.vpn.VpnStatusMonitor
import app.netpilot.databinding.ActivityMainBinding
import app.netpilot.ui.NavTab
import app.netpilot.ui.components.NavRailView
import app.netpilot.ui.dashboard.DashboardFragment
import app.netpilot.ui.dns.PrivateDnsFragment
import app.netpilot.ui.settings.SettingsFragment
import app.netpilot.ui.vpn.VpnFragment

/**
 * Single host activity. On Android TV it renders a left navigation rail;
 * on phones/tablets a bottom navigation bar. Fragments are shared by both.
 */
class MainActivity : AppCompatActivity(), NavRailView.Callback {

    private lateinit var binding: ActivityMainBinding
    private val isTv: Boolean by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            resources.getBoolean(R.bool.is_television)
    }
    private var selectedTab: NavTab = NavTab.DASHBOARD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        if (isTv) {
            binding.navRail.visibility = View.VISIBLE
            binding.navBottom.visibility = View.GONE
            binding.navRail.setup(NavTab.entries.toList(), this)
        } else {
            binding.navRail.visibility = View.GONE
            binding.navBottom.visibility = View.VISIBLE
            binding.navBottom.setOnItemSelectedListener { item ->
                val tab = tabForMenuItem(item.itemId)
                if (tab != selectedTab) {
                    selectTab(tab)
                }
                true
            }
            binding.navBottom.setOnItemReselectedListener { /* no-op */ }
        }

        savedInstanceState?.let {
            selectedTab = NavTab.entries.getOrElse(it.getInt(KEY_TAB, 0)) { NavTab.DASHBOARD }
        }
        if (savedInstanceState == null) handleLaunchIntent(intent)
        showFragment(selectedTab)
        syncNavSelection()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    selectedTab != NavTab.DASHBOARD -> selectTab(NavTab.DASHBOARD)
                    isTv && !binding.navRail.hasFocus() -> binding.navRail.focusFirstItem()
                    else -> finish()
                }
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, selectedTab.ordinal)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLaunchIntent(intent)
    }

    /**
     * App shortcuts (long-press icon) and the Quick Settings tiles land here:
     * static shortcuts carry the tab index (NavTab.ordinal) as an extra.
     * singleTask launchMode → warm launches arrive via [onNewIntent].
     */
    private fun handleLaunchIntent(intent: Intent?) {
        val tab = intent?.getIntExtra(EXTRA_SHORTCUT_TAB, -1) ?: -1
        if (tab >= 0 && tab < NavTab.entries.size) selectTab(NavTab.entries[tab])
    }

    override fun onTabSelected(tab: NavTab) = selectTab(tab)

    fun selectTab(tab: NavTab) {
        selectedTab = tab
        showFragment(tab)
        syncNavSelection()
    }

    private fun showFragment(tab: NavTab) {
        val fragment: Fragment = when (tab) {
            NavTab.DASHBOARD -> DashboardFragment()
            NavTab.PRIVATE_DNS -> PrivateDnsFragment()
            NavTab.VPN -> VpnFragment()
            NavTab.SETTINGS -> SettingsFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_container, fragment)
            .commit()
    }

    private fun syncNavSelection() {
        if (isTv) binding.navRail.setActive(selectedTab)
        val itemId = when (selectedTab) {
            NavTab.DASHBOARD -> R.id.nav_dashboard
            NavTab.PRIVATE_DNS -> R.id.nav_dns
            NavTab.VPN -> R.id.nav_vpn
            NavTab.SETTINGS -> R.id.nav_settings
        }
        if (binding.navBottom.selectedItemId != itemId) {
            binding.navBottom.selectedItemId = itemId
        }
    }

    private fun tabForMenuItem(id: Int): NavTab = when (id) {
        R.id.nav_dns -> NavTab.PRIVATE_DNS
        R.id.nav_vpn -> NavTab.VPN
        R.id.nav_settings -> NavTab.SETTINGS
        else -> NavTab.DASHBOARD
    }

    /** Android 15 edge-to-edge: pad content below system bars. */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    override fun onPause() {
        super.onPause()
        // Battery model: the VPN transport listener is only needed while the
        // UI is visible. Queries keep working; nothing listens in background.
        VpnStatusMonitor.stop(this)
    }

    override fun onResume() {
        super.onResume()
        VpnStatusMonitor.start(this)
        val appCtx = applicationContext
        // A platform IKEv2 tunnel can outlive this process: re-own it (and its
        // status notification) on return, or drop the stale marker.
        VpnSessionState.clearIfExpired(appCtx, VpnStatusMonitor.isActive(appCtx))
        VpnSessionState.reconcile(appCtx, VpnStatusMonitor.isActive(appCtx))
        StatusNotifications.reconcileStatusService(appCtx)
        ensureNotificationPermission()
    }

    /** Android 13+ posts the status notification only with POST_NOTIFICATIONS. */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        // CI emulators: never block automated UI runs with a permission dialog
        // (same guard as the first-run guide).
        if (isEmulatorLike()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val prefs = AppPreferences(this)
        if (prefs.notificationPermissionAsked) return
        prefs.notificationPermissionAsked = true
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIF_PERMISSION)
    }

    /** CI emulators (goldfish/ranchu, generic fingerprints): skip runtime prompts. */
    private fun isEmulatorLike(): Boolean =
        Build.HARDWARE.contains("goldfish", true) ||
            Build.HARDWARE.contains("ranchu", true) ||
            Build.FINGERPRINT.contains("generic", true)

    companion object {
        private const val KEY_TAB = "selected_tab"
        const val EXTRA_SHORTCUT_TAB = "shortcut_tab"
        private const val REQUEST_NOTIF_PERMISSION = 1001
    }
}
