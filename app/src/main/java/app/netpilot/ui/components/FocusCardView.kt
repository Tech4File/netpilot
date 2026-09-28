package app.netpilot.ui.components

import android.content.Context
import android.util.AttributeSet
import app.netpilot.R
import app.netpilot.core.ui.isTelevision
import com.google.android.material.card.MaterialCardView
import com.google.android.material.R.attr.materialCardViewStyle

/**
 * The workhorse card of the design system.
 * Focus behaviour tuned for 10-foot UI: visible stroke ring + lift + subtle zoom
 * so the focused element is unmistakable from a couch (Android TV guidance).
 * On touch devices it degrades to a normal Material card with ripple.
 */
open class FocusCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = materialCardViewStyle,
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val focusScale: Float

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.FocusCardView)
        val scaleEnabled = a.getBoolean(R.styleable.FocusCardView_npFocusScale, true)
        a.recycle()
        focusScale = if (scaleEnabled && context.isTelevision) 1.05f else 1f

        isFocusable = true
        isClickable = true
        strokeWidth = 0
        setOnFocusChangeListener { _, hasFocus -> renderFocus(hasFocus) }
    }

    private fun renderFocus(hasFocus: Boolean) {
        strokeWidth = if (hasFocus) {
            resources.getDimensionPixelSize(R.dimen.focus_stroke)
        } else {
            0
        }
        translationZ = if (hasFocus) resources.getDimension(R.dimen.focus_translation) else 0f
        if (focusScale > 1f) {
            val target = if (hasFocus) focusScale else 1f
            animate().scaleX(target).scaleY(target).setDuration(120).start()
        }
    }

    /** Re-assert focus visuals after recycling (RecyclerView). */
    public override fun drawableStateChanged() {
        super.drawableStateChanged()
        // no-op hook kept for future state layers
    }
}
