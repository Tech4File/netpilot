package app.netpilot.ui.dialogs

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import app.netpilot.R
import app.netpilot.core.dns.PrivateDnsManager
import app.netpilot.core.shizuku.ShizukuGranter
import com.google.android.material.button.MaterialButton
import rikka.shizuku.Shizuku
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** The one-time WRITE_SECURE_SETTINGS onboarding flow (copy-paste ADB steps). */
object SetupDialogs {

    const val GRANT_COMMAND =
        "adb shell pm grant app.netpilot android.permission.WRITE_SECURE_SETTINGS"
    const val REPO_URL = "https://github.com/Tech4File/netpilot"
    const val ISSUES_URL = "$REPO_URL/issues"
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    fun showPermissionGuide(activity: AppCompatActivity, onGranted: () -> Unit = {}) {
        val context = activity
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = true }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }
        scroll.addView(box)

        fun body(textRes: Int, top: Int): TextView = TextView(context).apply {
            setText(textRes)
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(top) }
            box.addView(this)
        }

        body(R.string.setup_intro, 0)

        // Easiest path first: Shizuku (no PC). Not installed -> Play Store
        // page; installed -> direct grant flow. ADB steps remain right below.
        val shizukuInstalled = ShizukuGranter.installed(context)
        val shizukuLabel = if (shizukuInstalled) R.string.setup_shizuku_btn else R.string.setup_shizuku_install
        val shizuku = MaterialButton(context).apply {
            setText(shizukuLabel)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(12) }
        }
        var permissionListener: Shizuku.OnRequestPermissionResultListener? = null

        fun openUrl(url: String) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: ActivityNotFoundException) {
            }
        }
        fun openStore() {
            val market = "market://details?id=$SHIZUKU_PACKAGE"
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(market)))
            } catch (_: ActivityNotFoundException) {
                openUrl("https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE")
            }
        }
        fun showTroubleshooting(retry: () -> Unit) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.setup_shizuku_trouble_title)
                .setMessage(R.string.setup_shizuku_causes)
                .setPositiveButton(R.string.setup_retry) { _, _ -> retry() }
                .setNeutralButton(R.string.setup_report_issue) { _, _ -> openUrl(ISSUES_URL) }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
        fun grantNow(retry: () -> Unit) {
            ShizukuGranter.grantWriteSecureSettings(context) { ok ->
                if (ok) {
                    Toast.makeText(context, R.string.setup_shizuku_ok, Toast.LENGTH_LONG).show()
                    onGranted()
                } else {
                    showTroubleshooting(retry)
                }
            }
        }
        fun startShizukuGrant() {
            if (!ShizukuGranter.installed(context)) {
                openStore()
                return
            }
            if (!ShizukuGranter.serverAvailable()) {
                Toast.makeText(context, R.string.setup_shizuku_missing, Toast.LENGTH_LONG).show()
                return
            }
            if (ShizukuGranter.permissionGranted()) {
                grantNow(::startShizukuGrant)
                return
            }
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != 4242) return
                    ShizukuGranter.removePermissionListener(this)
                    if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(context, R.string.setup_shizuku_wait, Toast.LENGTH_SHORT).show()
                        grantNow(::startShizukuGrant)
                    } else {
                        showTroubleshooting(::startShizukuGrant)
                    }
                }
            }
            permissionListener = listener
            ShizukuGranter.addPermissionListener(listener)
            ShizukuGranter.requestPermission()
        }
        shizuku.setOnClickListener { startShizukuGrant() }
        box.addView(shizuku)
        body(R.string.setup_step1, 16)
        body(R.string.setup_step2, 8)
        body(R.string.setup_step3, 8)

        box.addView(TextView(context).apply {
            text = GRANT_COMMAND
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.code_block_fg))
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = ContextCompat.getDrawable(context, R.drawable.bg_code_block)
            setTextIsSelectable(true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) }
        })

        body(R.string.setup_step4, 8)
        body(R.string.setup_note_tv, 8)

        val copy = MaterialButton(context).apply {
            setText(R.string.action_copy)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(12) }
            setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("adb", GRANT_COMMAND))
                Toast.makeText(context, R.string.export_ok, Toast.LENGTH_SHORT).show()
            }
        }
        box.addView(copy)

        val verify = MaterialButton(context).apply {
            setText(R.string.setup_verify)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) }
            setOnClickListener {
                if (PrivateDnsManager.hasWritePermission(context)) {
                    Toast.makeText(context, R.string.setup_granted_ok, Toast.LENGTH_LONG).show()
                    onGranted()
                } else {
                    Toast.makeText(context, R.string.dns_no_permission_body, Toast.LENGTH_LONG).show()
                }
            }
        }
        box.addView(verify)

        val link = MaterialButton(context).apply {
            setText(R.string.setup_project_link)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) }
            setOnClickListener { openUrl(REPO_URL) }
        }
        box.addView(link)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.setup_title)
            .setView(scroll)
            .setPositiveButton(R.string.action_done, null)
            .setOnDismissListener {
                permissionListener?.let { ShizukuGranter.removePermissionListener(it) }
                permissionListener = null
            }
            .show()
    }
}
