package app.netpilot.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import app.netpilot.R

/** Small rounded status indicator: colored dot + label on a tinted pill. */
class StatusPill @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val dot: View
    private val label: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = resources.getDimensionPixelSize(R.dimen.pill_height)
        val hPad = resources.getDimensionPixelSize(R.dimen.pill_padding_h)
        setPadding(hPad, 0, hPad, 0)
        background = ContextCompat.getDrawable(context, R.drawable.bg_status_pill)

        inflate(context, R.layout.view_status_pill, this)
        dot = findViewById(R.id.pill_dot)
        label = findViewById(R.id.pill_text)
    }

    fun set(textRes: Int, colorRes: Int) {
        set(context.getString(textRes), ContextCompat.getColor(context, colorRes))
    }

    fun set(text: String, color: Int) {
        label.text = text
        label.setTextColor(color)
        dot.background?.mutate()?.setTint(color)
        backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 26))
    }
}
