package app.netpilot.ui.components

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.content.ContextCompat
import app.netpilot.BuildConfig
import app.netpilot.R
import app.netpilot.ui.NavTab

/**
 * TV navigation rail: brand on top, sections below, version pinned at the bottom.
 * Items are large, focusable, and clearly highlighted (10-foot UI rules).
 */
class NavRailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    interface Callback {
        fun onTabSelected(tab: NavTab)
    }

    private val itemViews = mutableListOf<LinearLayout>()
    private val icons = mutableListOf<ImageView>()
    private val labels = mutableListOf<TextView>()
    private var tabs: List<NavTab> = emptyList()
    private var callback: Callback? = null

    init {
        orientation = VERTICAL
    }

    fun setup(tabs: List<NavTab>, callback: Callback) {
        this.tabs = tabs
        this.callback = callback
        removeAllViews()

        // Brand
        val brand = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(6), dp(20))
        }
        brand.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_logo_shield)
            setColorFilter(ContextCompat.getColor(context, R.color.brand_primary))
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
        })
        brand.addView(TextView(context).apply {
            text = context.getString(R.string.app_name)
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.brand_primary))
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(10) }
        })
        addView(brand)

        tabs.forEach { tab -> addView(makeItem(tab)) }

        addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))

        addView(TextView(context).apply {
            text = "v${BuildConfig.VERSION_NAME}"
            textSize = 12f
            alpha = 0.55f
            setPadding(dp(8), dp(8), dp(8), dp(8))
        })
    }

    private fun makeItem(tab: NavTab): LinearLayout {
        val item = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            background = ContextCompat.getDrawable(context, R.drawable.bg_nav_item)
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.rail_item_height),
            ).apply { topMargin = dp(6) }
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        val icon = ImageView(context).apply {
            setImageResource(tab.iconRes)
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
        }
        val label = TextView(context).apply {
            setText(tab.titleRes)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(4) }
        }
        item.addView(icon)
        item.addView(label)
        item.setOnFocusChangeListener { _, hasFocus ->
            val scale = if (hasFocus) 1.04f else 1f
            item.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
        }
        item.setOnClickListener {
            callback?.onTabSelected(tab)
            setActive(tab)
        }
        itemViews += item
        icons += icon
        labels += label
        return item
    }

    fun setActive(tab: NavTab) {
        itemViews.forEachIndexed { i, view ->
            val active = tabs[i] == tab
            view.isActivated = active
            icons[i].setColorFilter(
                ContextCompat.getColor(
                    context,
                    if (active) R.color.brand_primary else R.color.nav_item_inactive,
                ),
            )
            labels[i].setTextColor(
                ContextCompat.getColor(
                    context,
                    if (active) R.color.brand_primary else R.color.nav_item_inactive,
                ),
            )
            labels[i].setTypeface(labels[i].typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    fun focusFirstItem(): Boolean = itemViews.firstOrNull()?.requestFocus() ?: false

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics,
    ).toInt()
}
