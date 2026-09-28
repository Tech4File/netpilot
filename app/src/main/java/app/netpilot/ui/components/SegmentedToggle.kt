package app.netpilot.ui.components

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import app.netpilot.R
import com.google.android.material.color.MaterialColors

/**
 * D-pad friendly segmented control (rows of equal-width focusable segments).
 * Focus moves highlight; pressing Enter commits the selection.
 */
class SegmentedToggle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    interface OnSelectionListener {
        fun onSelected(index: Int)
    }

    private var segments: MutableList<FocusCardView> = mutableListOf()
    private var labels: MutableList<TextView> = mutableListOf()
    private var callback: OnSelectionListener? = null

    var selectedIndex: Int = -1
        private set

    var enabled_: Boolean = true

    fun configure(items: List<String>, initialIndex: Int, listener: OnSelectionListener) {
        callback = listener
        removeAllViews()
        segments.clear()
        labels.clear()
        orientation = HORIZONTAL

        val gap = (resources.getDimensionPixelSize(R.dimen.space_8))
        items.forEachIndexed { index, text ->
            val child = TextView(context).apply {
                this.text = text
                gravity = android.view.Gravity.CENTER
                setPadding(0, gap, 0, gap)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val segment = FocusCardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = if (index < items.lastIndex) gap else 0
                }
                radius = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics,
                )
                addView(child)
                setOnClickListener {
                    if (enabled_) setSelection(index, notify = true)
                }
            }
            addView(segment)
            segments += segment
            labels += child
        }
        setSelection(initialIndex.coerceIn(0, items.lastIndex), notify = false)
    }

    fun setSelection(index: Int, notify: Boolean) {
        if (index !in segments.indices) return
        selectedIndex = index
        val surfaceVariant = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorSurfaceVariant, Color.GRAY,
        )
        val primaryContainer = ContextCompat.getColor(context, R.color.brand_primary_container)
        val onSurfaceVariant = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY,
        )
        val primary = ContextCompat.getColor(context, R.color.brand_primary)
        segments.forEachIndexed { i, card ->
            val selected = i == index
            card.setCardBackgroundColor(if (selected) primaryContainer else surfaceVariant)
            labels[i].setTextColor(if (selected) primary else onSurfaceVariant)
        }
        if (notify) callback?.onSelected(index)
    }
}
