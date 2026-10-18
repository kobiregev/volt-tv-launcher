package io.github.kobiregev.volt

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** One item in a [Sheet]: a [SheetRow] or a [SheetDivider]. */
sealed interface SheetItem

object SheetDivider : SheetItem

class SheetRow(
    val label: String,
    val iconRes: Int,
    val value: String? = null,
    val chevron: Boolean = false,
    val trailingIcon: Int? = null,
    val onLeft: (() -> Unit)? = null,
    val onRight: (() -> Unit)? = null,
    val onClick: () -> Unit
) : SheetItem

/**
 * Bottom sheet drawn inside the activity (no dialog window). It overlays [root], traps D-pad focus
 * in its rows, and gives focus back to the previous view on close.
 */
class Sheet(private val activity: Activity, private val root: FrameLayout) {
    private val density = activity.resources.displayMetrics.density
    private var scrim: View? = null
    private var panel: View? = null
    private var blocked: ViewGroup? = null
    private var blockedMode = 0
    private var previousFocus: View? = null
    private val removeViews = Runnable { removeNow() }

    /** True from [show] until [close] starts. */
    var isOpen = false
        private set

    private fun dp(v: Int) = (v * density + 0.5f).toInt()

    private fun rr(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * density
    }

    private fun tv(text: String, sp: Float, color: Int, face: String = "sans-serif") = TextView(activity).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        typeface = Typeface.create(face, Typeface.NORMAL)
        includeFontPadding = false
    }

    private fun icon(res: Int, tint: Int, sizeDp: Int) = ImageView(activity).apply {
        setImageResource(res)
        setColorFilter(tint)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    /** Small header: tile image, then name and sub line. */
    fun header(tile: View, name: String, sub: String): View = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(tile, LinearLayout.LayoutParams(dp(64), dp(36)))
        val col = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tv(name, 18f, C_TEXT, "sans-serif-medium").apply { maxLines = 1 })
        col.addView(tv(sub, 13f, C_SEC), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(3) })
        addView(col, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(14) })
    }

    fun show(header: View?, items: List<SheetItem>, focusIndex: Int = 0) {
        if (isOpen) return
        removeNow()
        isOpen = true
        previousFocus = activity.currentFocus
        // Block focus search in the screen below so nothing can take focus from the sheet.
        blocked = root.getChildAt(0) as? ViewGroup
        blocked?.let {
            blockedMode = it.descendantFocusability
            it.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }

        val sc = View(activity).apply {
            setBackgroundColor(0xA6000000.toInt())
            isFocusable = false
            isClickable = true
            alpha = 0f
        }
        root.addView(sc, FrameLayout.LayoutParams(-1, -1))
        scrim = sc

        val p = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val r = 16 * density
            background = GradientDrawable().apply {
                setColor(C_CARD)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(dp(16), dp(16), dp(16), dp(20))
            isClickable = true
            clipChildren = false
            clipToPadding = false
        }
        val handle = View(activity).apply { background = rr(0xFF3A3D42.toInt(), 2) }
        p.addView(handle, LinearLayout.LayoutParams(dp(36), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        if (header != null) {
            p.addView(header, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
            p.addView(divider(), LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(14) })
            p.addView(View(activity), LinearLayout.LayoutParams(-1, dp(10)))
        } else {
            p.addView(View(activity), LinearLayout.LayoutParams(-1, dp(10)))
        }

        val rows = ArrayList<View>()
        val lastRow = items.indexOfLast { it is SheetRow }
        for ((i, it) in items.withIndex()) {
            when (it) {
                is SheetDivider -> p.addView(
                    divider(),
                    LinearLayout.LayoutParams(-1, dp(1)).apply {
                        topMargin = dp(8)
                        bottomMargin = dp(8)
                    }
                )
                is SheetRow -> {
                    val v = makeRow(it, isFirst = rows.isEmpty(), isLast = i == lastRow)
                    rows.add(v)
                    p.addView(v, LinearLayout.LayoutParams(-1, dp(44)))
                }
            }
        }
        root.addView(p, FrameLayout.LayoutParams(dp(560), -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        panel = p

        // Slide up once the height is known (before the first frame, so there is no flash).
        p.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                p.viewTreeObserver.removeOnPreDrawListener(this)
                p.translationY = p.height.toFloat()
                p.animate().translationY(0f).setDuration(150).start()
                sc.animate().alpha(1f).setDuration(150).start()
                return true
            }
        })
        (rows.getOrNull(focusIndex) ?: rows.firstOrNull())?.requestFocus()
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        blocked?.descendantFocusability = blockedMode
        blocked = null
        val prev = previousFocus
        previousFocus = null
        if (prev != null && prev.isAttachedToWindow) prev.requestFocus()
        panel?.animate()?.translationY((panel?.height ?: 0).toFloat())?.setDuration(120)?.start()
        scrim?.animate()?.alpha(0f)?.setDuration(120)?.start()
        panel?.postDelayed(removeViews, 120)
    }

    private fun removeNow() {
        panel?.removeCallbacks(removeViews)
        panel?.animate()?.cancel()
        scrim?.animate()?.cancel()
        panel?.let { root.removeView(it) }
        scrim?.let { root.removeView(it) }
        panel = null
        scrim = null
    }

    private fun divider() = View(activity).apply { setBackgroundColor(0xFF2C2F33.toInt()) }

    private fun makeRow(row: SheetRow, isFirst: Boolean, isLast: Boolean): View {
        val bg = rr(Color.TRANSPARENT, 9)
        val ic = icon(row.iconRes, C_ICON, 18)
        val label = tv(row.label, 16f, C_TEXT)
        val value = row.value?.let { tv(it, 15f, C_SEC) }
        val chev = if (row.chevron) icon(R.drawable.ic_chevron_right, C_ICON, 16) else null
        val trail = row.trailingIcon?.let { icon(it, C_BLUE, 16) }
        return LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
            background = bg
            isFocusable = true
            isClickable = true
            defaultFocusHighlightEnabled = false
            addView(ic)
            addView(label, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
            if (value != null) addView(value)
            if (chev != null) {
                addView(chev, LinearLayout.LayoutParams(dp(16), dp(16)).apply { leftMargin = dp(8) })
            }
            if (trail != null) {
                addView(trail, LinearLayout.LayoutParams(dp(16), dp(16)).apply { leftMargin = dp(8) })
            }
            setOnClickListener { row.onClick() }
            setOnFocusChangeListener { _, has ->
                bg.setColor(if (has) C_BLUE else Color.TRANSPARENT)
                ic.setColorFilter(if (has) Color.WHITE else C_ICON)
                chev?.setColorFilter(if (has) Color.WHITE else C_ICON)
                trail?.setColorFilter(if (has) Color.WHITE else C_BLUE)
                label.setTextColor(if (has) Color.WHITE else C_TEXT)
                value?.setTextColor(if (has) 0xFFDCE5FB.toInt() else C_SEC)
            }
            setOnKeyListener { _, code, ev ->
                if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (code) {
                    KeyEvent.KEYCODE_DPAD_UP -> isFirst
                    KeyEvent.KEYCODE_DPAD_DOWN -> isLast
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        row.onLeft?.invoke()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        row.onRight?.invoke()
                        true
                    }
                    else -> false
                }
            }
        }
    }
}
