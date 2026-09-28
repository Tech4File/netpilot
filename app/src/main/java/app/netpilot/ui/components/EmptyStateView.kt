package app.netpilot.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import app.netpilot.R
import app.netpilot.core.ui.isTelevision
import com.google.android.material.color.MaterialColors

/** Centered icon + title + body used when a list has no content. */
class EmptyStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val icon: ImageView
    private val titleView: TextView
    private val bodyView: TextView

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val pad = resources.getDimensionPixelSize(R.dimen.space_32)
        setPadding(pad, pad * 2, pad, pad * 2)

        icon = ImageView(context).apply {
            val size = (44 * resources.displayMetrics.density).toInt()
            layoutParams = LayoutParams(size, size)
            alpha = 0.45f
            setColorFilter(
                MaterialColors.getColor(this@EmptyStateView, com.google.android.material.R.attr.colorOnSurfaceVariant, ContextCompat.getColor(context, android.R.color.darker_gray)),
            )
        }
        addView(icon)

        titleView = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = (16 * resources.displayMetrics.density).toInt()
            }
            gravity = Gravity.CENTER
            setTextSize(TypedValueWrapper.sp(context, 18f))
            setTextColor(MaterialColors.getColor(this@EmptyStateView, com.google.android.material.R.attr.colorOnSurface, ContextCompat.getColor(context, android.R.color.black)))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        addView(titleView)

        bodyView = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * resources.displayMetrics.density).toInt()
            }
            gravity = Gravity.CENTER
            setTextSize(TypedValueWrapper.sp(context, 14f))
            alpha = 0.85f
        }
        addView(bodyView)

        if (!isInEditMode) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.EmptyStateView)
            a.getDrawable(R.styleable.EmptyStateView_npIcon)?.let { icon.setImageDrawable(it) }
            a.getText(R.styleable.EmptyStateView_npTitle)?.let { titleView.text = it }
            a.getText(R.styleable.EmptyStateView_npBody)?.let { bodyView.text = it }
            a.recycle()
        }
    }

    fun setIconRes(res: Int) = icon.setImageResource(res)
    fun setTitle(text: CharSequence) { titleView.text = text }
    fun setBody(text: CharSequence) { bodyView.text = text }

    /** Avoids pulling android.util.TypedValue into user code paths. */
    private object TypedValueWrapper {
        fun sp(context: Context, value: Float): Float =
            android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, value, context.resources.displayMetrics)
    }
}
