package app.netpilot.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** The one-time WRITE_SECURE_SETTINGS onboarding flow (copy-paste ADB steps). */
object SetupDialogs {

    const val GRANT_COMMAND =
        "adb shell pm grant app.netpilot android.permission.WRITE_SECURE_SETTINGS"

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
        body(R.string.setup_step1, 12)
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
            setTextColor(ContextCompat.getColor(context, R.color.brand_primary))
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

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.setup_title)
            .setView(scroll)
            .setPositiveButton(R.string.action_done, null)
            .show()
    }
}
