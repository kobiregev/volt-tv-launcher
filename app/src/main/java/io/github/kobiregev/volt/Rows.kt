package io.github.kobiregev.volt

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Icon for a category id. Custom categories get the generic one. */
internal fun categoryIconRes(id: String): Int = when (id) {
    "streaming" -> R.drawable.ic_cat_streaming
    "music" -> R.drawable.ic_cat_music
    "games" -> R.drawable.ic_cat_games
    "tools" -> R.drawable.ic_cat_tools
    "system" -> R.drawable.ic_cat_system
    else -> R.drawable.ic_category
}

/**
 * A built row (the look used by the sheet and the settings screen). The caller sets the layout
 * params (44dp high) and the click handler. [onFocus] runs after the row restyled itself.
 */
internal class RowParts(
    val view: LinearLayout,
    val label: TextView,
    val value: TextView?,
    private val bg: GradientDrawable,
    private val ic: ImageView,
    private val chev: ImageView?,
    private val trail: ImageView?
) {
    var onFocus: ((Boolean) -> Unit)? = null

    /** Shows the C_SEL background when the row is not focused (a selected settings section). */
    var selected = false
        set(v) {
            field = v
            refresh()
        }

    fun refresh() = apply(view.isFocused)

    internal fun apply(has: Boolean) {
        bg.setColor(
            when {
                has -> C_BLUE
                selected -> C_SEL
                else -> Color.TRANSPARENT
            }
        )
        ic.setColorFilter(if (has) Color.WHITE else C_ICON)
        chev?.setColorFilter(if (has) Color.WHITE else C_ICON)
        trail?.setColorFilter(if (has) Color.WHITE else C_BLUE)
        label.setTextColor(if (has) Color.WHITE else C_TEXT)
        value?.setTextColor(if (has) 0xFFDCE5FB.toInt() else C_SEC)
    }
}

internal object Rows {
    /**
     * Builds one row: icon, label, optional value, chevron, trailing icon and trailing view.
     * A row that is not [focusable] is plain info text and never highlights.
     */
    fun build(
        ctx: Context,
        iconRes: Int,
        label: String,
        value: String? = null,
        chevron: Boolean = false,
        trailingIcon: Int? = null,
        trailingView: View? = null,
        focusable: Boolean = true
    ): RowParts {
        val density = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * density + 0.5f).toInt()

        fun tv(text: String, sp: Float, color: Int) = TextView(ctx).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            includeFontPadding = false
        }

        fun icon(res: Int, tint: Int, sizeDp: Int) = ImageView(ctx).apply {
            setImageResource(res)
            setColorFilter(tint)
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        }

        val bg = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = 9 * density
        }
        val ic = icon(iconRes, C_ICON, 18)
        val lab = tv(label, 16f, C_TEXT)
        val val_ = value?.let { tv(it, 15f, C_SEC) }
        val chev = if (chevron) icon(R.drawable.ic_chevron_right, C_ICON, 16) else null
        val trail = trailingIcon?.let { icon(it, C_BLUE, 16) }
        val row = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
            background = bg
            addView(ic)
            addView(lab, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
            if (val_ != null) addView(val_, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
            if (chev != null) addView(chev, LinearLayout.LayoutParams(dp(16), dp(16)).apply { leftMargin = dp(8) })
            if (trail != null) addView(trail, LinearLayout.LayoutParams(dp(16), dp(16)).apply { leftMargin = dp(8) })
            if (trailingView != null) addView(trailingView, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        }
        val parts = RowParts(row, lab, val_, bg, ic, chev, trail)
        if (focusable) {
            row.isFocusable = true
            row.isClickable = true
            row.defaultFocusHighlightEnabled = false
            row.setOnFocusChangeListener { _, has ->
                parts.apply(has)
                parts.onFocus?.invoke(has)
            }
        }
        return parts
    }
}
