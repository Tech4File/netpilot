package app.netpilot.ui.components

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import app.netpilot.R
import com.google.android.material.color.MaterialColors

/** Settings list row: leading icon, title (+optional subtitle), trailing value & chevron. */
class SettingRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FocusCardView(context, attrs) {

    private val titleView: TextView
    private val subtitleView: TextView
    private val valueView: TextView
    private val iconView: ImageView
    private val chevron: ImageView

    init {
        val density = resources.displayMetrics.density
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = (12 * density).toInt()
        }
        elevation = 0f

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = resources.getDimensionPixelSize(R.dimen.card_padding)
            setPadding(pad, pad, pad, pad)
        }
        addView(row)

        iconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt())
            setColorFilter(ContextCompat.getColor(context, R.color.brand_primary))
        }
        row.addView(iconView)

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (16 * density).toInt()
            }
        }
        row.addView(textColumn)

        titleView = TextView(context).apply {
            setTextSize(TypedValueWrapper.sp(context, 16f))
            setTextColor(MaterialColors.getColor(this@SettingRow, com.google.android.material.R.attr.colorOnSurface, ContextCompat.getColor(context, android.R.color.black)))
            setTypeface(typeface, Typeface.BOLD)
        }
        textColumn.addView(titleView)

        subtitleView = TextView(context).apply {
            visibility = GONE
            setTextSize(TypedValueWrapper.sp(context, 13f))
            alpha = 0.85f
        }
        textColumn.addView(subtitleView)

        valueView = TextView(context).apply {
            visibility = GONE
            setTextSize(TypedValueWrapper.sp(context, 14f))
            setTextColor(MaterialColors.getColor(this@SettingRow, com.google.android.material.R.attr.colorOnSurfaceVariant, ContextCompat.getColor(context, android.R.color.darker_gray)))
        }
        row.addView(valueView)

        chevron = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt()).apply {
                marginStart = (8 * density).toInt()
            }
            setImageResource(R.drawable.ic_chevron_right)
            setColorFilter(MaterialColors.getColor(this@SettingRow, com.google.android.material.R.attr.colorOnSurfaceVariant, ContextCompat.getColor(context, android.R.color.darker_gray)))
        }
        row.addView(chevron)

        val a = context.obtainStyledAttributes(attrs, R.styleable.SettingRow)
        a.getDrawable(R.styleable.SettingRow_npIcon)?.let { iconView.setImageDrawable(it) }
        a.getText(R.styleable.SettingRow_npTitle)?.let { titleView.text = it }
        a.recycle()
    }

    fun setTitle(text: CharSequence) { titleView.text = text }
    fun setSubtitle(text: CharSequence) {
        subtitleView.text = text
        subtitleView.visibility = if (text.isBlank()) GONE else VISIBLE
    }
    fun setValue(text: CharSequence) {
        valueView.text = text
        valueView.visibility = if (text.isBlank()) GONE else VISIBLE
    }
    fun setValueColorRes(res: Int) = valueView.setTextColor(ContextCompat.getColor(context, res))

    private object TypedValueWrapper {
        fun sp(context: Context, value: Float): Float =
            android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, value, context.resources.displayMetrics)
    }
}
