package app.netpilot.core.tiles

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import app.netpilot.MainActivity
import app.netpilot.R
import app.netpilot.core.dns.DnsProfileRepository
import app.netpilot.core.dns.PrivateDnsManager
import app.netpilot.core.status.StatusNotifications
import app.netpilot.ui.NavTab

/**
 * The DNS tile's pop-up: a compact dialog over the collapsed shade with the
 * Private DNS on/off switch and the profile list underneath. Launched with
 * TileService.startActivityAndCollapse (Intent overload on API 24-33,
 * PendingIntent overload on API 34+ — see [TileLaunch]).
 *
 * Theme follows the system DayNight palette (Material 3), so the popup looks
 * native in light and dark on every supported Android. Rows are focusable,
 * so the dialog also works on D-pad devices.
 */
class TileDialogActivity : AppCompatActivity() {

    private val repo by lazy { DnsProfileRepository(this) }
    private lateinit var statusLine: TextView
    private lateinit var switch: SwitchCompat
    private lateinit var listBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tap outside closes the popup, like every system tile dialog.
        setFinishOnTouchOutside(true)
        setContentView(R.layout.activity_tile_dialog)
        statusLine = findViewById(R.id.tile_dialog_status)
        switch = findViewById(R.id.tile_dialog_switch)
        listBox = findViewById(R.id.tile_dialog_profiles)
        findViewById<TextView>(R.id.tile_dialog_manage).setOnClickListener { goToDnsTab() }

        if (!PrivateDnsManager.hasWritePermission(this)) {
            // No grant yet: the popup cannot flip the system setting — hand
            // the user into the DNS tab where the guide lives.
            goToDnsTab()
            return
        }
        render()
    }

    private fun render() {
        val model = TileDialogState.compute(
            mode = PrivateDnsManager.read(this).mode,
            profiles = repo.list(),
            activeId = repo.activeId(),
        )
        statusLine.text = when {
            !model.switchOn -> getString(R.string.tile_dns_off)
            model.activeProfileName != null -> model.activeProfileName
            else -> getString(R.string.tile_dns_label)
        }
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = model.switchOn
        switch.isEnabled = model.switchOn || model.canTurnOn
        switch.setOnCheckedChangeListener { _, checked -> onToggle(checked) }

        listBox.removeAllViews()
        if (model.rows.isEmpty()) {
            listBox.addView(row(getString(R.string.tile_dialog_no_profiles), null))
        } else {
            for (r in model.rows) {
                val label = if (r.active) getString(R.string.tile_dialog_active_prefix, r.name) else r.name
                listBox.addView(row(label) { onPick(r.id) })
            }
        }
    }

    private fun onToggle(on: Boolean) {
        if (on) {
            val profile = repo.activeProfile() ?: run { render(); return }
            if (!PrivateDnsManager.applyProfile(this, profile.hostname)) {
                render()
                return
            }
        } else {
            if (!PrivateDnsManager.disable(this)) {
                render()
                return
            }
            repo.clearActive()
        }
        StatusNotifications.reconcileStatusService(this)
        render()
    }

    private fun onPick(id: String) {
        val profile = repo.get(id) ?: return
        if (!PrivateDnsManager.applyProfile(this, profile.hostname)) {
            render()
            return
        }
        repo.setActive(id)
        StatusNotifications.reconcileStatusService(this)
        render()
        // Brief confirmation, then the popup closes itself.
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 350)
    }

    private fun row(text: String, onClick: (() -> Unit)?): TextView {
        val v = TextView(this)
        v.text = text
        val pad = (resources.displayMetrics.density * 14).toInt()
        v.setPadding(pad, pad / 2, pad, pad / 2)
        v.textSize = 16f
        v.isFocusable = true
        v.isClickable = onClick != null
        // selectableItemBackground is an ATTRIBUTE — resolve it through the
        // theme so the ripple follows the current light/dark palette.
        val tv = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        v.setBackgroundResource(tv.resourceId)
        if (onClick != null) v.setOnClickListener { onClick() }
        return v
    }

    private fun goToDnsTab() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SHORTCUT_TAB, NavTab.PRIVATE_DNS.ordinal)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }
}
