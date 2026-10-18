package io.github.kobiregev.volt

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** 38x22dp switch: pill track and a round knob. */
private class Toggle(c: Context) : View(c) {
    var on = false
        set(v) {
            field = v
            invalidate()
        }
    var rowFocused = false
        set(v) {
            field = v
            invalidate()
        }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        setMeasuredDimension((38 * d + 0.5f).toInt(), (22 * d + 0.5f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        // On a focused (blue) row the plain colors would vanish, so the track is tinted instead.
        p.color = when {
            rowFocused && on -> 0xFF2547A8.toInt()
            rowFocused -> 0x55000000
            on -> C_BLUE
            else -> C_TRACK
        }
        r.set(0f, 0f, w, h)
        canvas.drawRoundRect(r, h / 2, h / 2, p)
        val inset = (h - 18 * resources.displayMetrics.density) / 2
        val knob = h - inset * 2
        p.color = Color.WHITE
        val x = if (on) w - inset - knob else inset
        canvas.drawCircle(x + knob / 2, h / 2, knob / 2, p)
    }
}

/** Name editor. Back cancels at once, even while the keyboard is open (the keyboard would eat it). */
private class NameEdit(c: Context) : EditText(c) {
    var onCancel: (() -> Unit)? = null

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onCancel?.invoke()
            return true
        }
        return super.onKeyPreIme(keyCode, event)
    }
}

/** One entry in the left list. Add a new section here (for example "Weather" first). */
private val DPAD = setOf(
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT
)

private class Section(val title: String, val iconRes: Int, val build: () -> Unit)

private sealed interface Editing {
    class Rename(val id: String) : Editing

    object Add : Editing
}

class SettingsActivity : Activity() {
    private val exec: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var store: Store
    private var density = 1f
    private var destroyed = false

    private lateinit var sectionList: LinearLayout
    private lateinit var detailTitle: TextView
    private lateinit var rows: LinearLayout

    // left column
    private val sections = ArrayList<Section>()
    private val sectionParts = ArrayList<RowParts>()
    private lateinit var androidRow: RowParts
    private var current = -1

    // right column: focusable rows in order (pills and the editor are not in this list)
    private val detailRows = ArrayList<View>()
    private var rendering = false

    // categories state
    private var cats: List<Category> = Categories.DEFAULTS
    private var entries: List<AppEntry>? = null
    private var counts: Map<String, Int> = emptyMap()
    private var expandedId: String? = null
    private var editing: Editing? = null
    private val rowById = HashMap<String, View>()
    private val valueById = HashMap<String, TextView>()
    private val pillByName = LinkedHashMap<String, TextView>()
    private var addRow: View? = null
    private var editor: NameEdit? = null

    private fun dp(v: Int) = (v * density + 0.5f).toInt()

    private fun rr(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * density
    }

    private fun tv(text: String, sp: Float, color: Int, face: String = "sans-serif") = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        typeface = Typeface.create(face, Typeface.NORMAL)
        includeFontPadding = false
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        density = resources.displayMetrics.density
        store = Store(this)
        cats = store.categories()
        setContentView(buildUi())
        sectionParts[0].view.requestFocus()
        loadApps()
    }

    override fun onStop() {
        // Home key, a system settings screen or the screensaver: do not stay on top of the launcher.
        if (!isFinishing) finish()
        super.onStop()
    }

    override fun onDestroy() {
        destroyed = true
        exec.shutdownNow()
        super.onDestroy()
    }

    @Deprecated("Back closes an open edit or strip first")
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        when {
            editing != null -> cancelEdit()
            expandedId != null -> collapseStrip(focusRow = true)
            else -> finish()
        }
    }

    // ---------------------------------------------------------------- layout

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(C_BG)
            setPadding(dp(32), dp(16), dp(32), dp(12))
        }
        val titleRow = FrameLayout(this)
        titleRow.addView(
            tv("Volt settings", 20f, C_TEXT),
            FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL)
        )
        root.addView(titleRow, LinearLayout.LayoutParams(-1, dp(28)))

        val body = LinearLayout(this)
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })

        sectionList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rr(C_CARD, 12)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        body.addView(sectionList, LinearLayout.LayoutParams(dp(240), -1))

        val detail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rr(C_CARD, 12)
            setPadding(dp(22), dp(20), dp(22), dp(20))
        }
        body.addView(detail, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(14) })
        detailTitle = tv("", 22f, C_TEXT, "sans-serif-medium")
        detail.addView(detailTitle)
        rows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(rows, FrameLayout.LayoutParams(-1, -2))
        }
        detail.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(14) })

        // Sections, in list order. A "Weather" section goes first when it exists.
        sections.add(Section("Categories", R.drawable.ic_category) { renderCategories() })
        sections.add(Section("Home cards", R.drawable.ic_grid) { renderCards() })
        sections.add(Section("About Volt", R.drawable.ic_info) { renderAbout() })
        for ((i, s) in sections.withIndex()) {
            val parts = Rows.build(this, s.iconRes, s.title)
            parts.onFocus = { has -> if (has && !rendering && current != i) showSection(i) }
            parts.view.setOnClickListener { if (current != i) showSection(i) }
            sectionParts.add(parts)
            sectionList.addView(parts.view, rowParams())
        }
        sectionList.addView(
            View(this).apply { setBackgroundColor(0xFF2C2F33.toInt()) },
            LinearLayout.LayoutParams(-1, dp(1)).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
        )
        androidRow = Rows.build(this, R.drawable.ic_settings, "Android settings", chevron = true)
        androidRow.view.setOnClickListener { openAndroidSettings() }
        sectionList.addView(androidRow.view, rowParams())

        showSection(0)
        return root
    }

    private fun rowParams() = LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(2) }

    private fun showSection(i: Int) {
        current = i
        editing = null
        expandedId = null
        for ((k, p) in sectionParts.withIndex()) p.selected = k == i
        detailTitle.text = sections[i].title
        sections[i].build()
    }

    /** Clears the detail rows. Focus that was inside them is lost, so guard the section listener. */
    private inline fun rebuild(block: () -> Unit) {
        rendering = true
        try {
            rows.removeAllViews()
            detailRows.clear()
            rowById.clear()
            valueById.clear()
            pillByName.clear()
            addRow = null
            editor = null
            block()
        } finally {
            rendering = false
        }
    }

    private fun addDetailRow(parts: RowParts): View {
        detailRows.add(parts.view)
        rows.addView(parts.view, rowParams())
        return parts.view
    }

    private fun openAndroidSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Not available on this device", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- focus model

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        if (ev.action == KeyEvent.ACTION_DOWN) {
            val f = currentFocus
            if (f != null && f !is EditText && navigate(f, ev.keyCode)) return true
        }
        return super.dispatchKeyEvent(ev)
    }

    /** Keeps each column closed: Up/Down stop at the ends and Left/Right only switch columns. */
    private fun navigate(f: View, key: Int): Boolean {
        if (key !in DPAD) return false
        val up = key == KeyEvent.KEYCODE_DPAD_UP
        val down = key == KeyEvent.KEYCODE_DPAD_DOWN
        val right = key == KeyEvent.KEYCODE_DPAD_RIGHT

        // left column: sections, then "Android settings"
        val left = sectionParts.map { it.view } + androidRow.view
        val li = left.indexOf(f)
        if (li >= 0) {
            when {
                up -> left.getOrNull(li - 1)?.requestFocus()
                down -> left.getOrNull(li + 1)?.requestFocus()
                right -> detailRows.firstOrNull()?.requestFocus()
            }
            return true
        }

        // action pills under a category row
        val pills = pillByName.values.toList()
        val pi = pills.indexOf(f)
        if (pi >= 0) {
            when {
                up || down -> collapseStrip(focusRow = true)
                else -> {
                    val step = if (right) 1 else -1
                    var k = pi + step
                    while (k in pills.indices && !pills[k].isFocusable) k += step
                    pills.getOrNull(k)?.requestFocus()
                }
            }
            return true
        }

        // detail rows
        val di = detailRows.indexOf(f)
        if (di >= 0) {
            when {
                up -> detailRows.getOrNull(di - 1)?.requestFocus()
                down -> detailRows.getOrNull(di + 1)?.requestFocus()
                right -> {}
                else -> {
                    if (expandedId != null) collapseStrip(focusRow = false)
                    sectionParts[current].view.requestFocus()
                }
            }
            return true
        }
        return false
    }

    // ---------------------------------------------------------------- categories

    private fun loadApps() {
        exec.execute {
            val list = try {
                AppLoader.load(this)
            } catch (t: Throwable) {
                return@execute
            }
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                entries = list
                recount()
                refreshValues()
            }
        }
    }

    private fun recount() {
        val list = entries ?: return
        val hidden = store.hidden()
        counts = Categories.countApps(
            cats,
            list.filter { it.pkg !in hidden }.map { store.catOverride(it.pkg) to it.defCat }
        )
    }

    private fun valueFor(id: String): String {
        if (entries == null && id != Categories.ALL) return ""
        return Categories.valueText(id, counts[id] ?: 0)
    }

    private fun refreshValues() {
        for ((id, t) in valueById) t.text = valueFor(id)
    }

    private fun renderCategories(): Unit = rebuild {
        for (c in cats) {
            val e = editing
            if (e is Editing.Rename && e.id == c.id) {
                rows.addView(editRow(categoryIconRes(c.id), c.name), rowParams())
                continue
            }
            val parts = Rows.build(this, categoryIconRes(c.id), c.name, value = valueFor(c.id))
            val v = addDetailRow(parts)
            rowById[c.id] = v
            valueById[c.id] = parts.value!!
            if (c.id != Categories.ALL) {
                v.setOnClickListener {
                    if (expandedId == c.id) {
                        collapseStrip(focusRow = true)
                    } else {
                        expandedId = c.id
                        renderCategories()
                        pillByName["Rename"]?.requestFocus()
                    }
                }
            }
            if (expandedId == c.id) rows.addView(strip(c), stripParams())
        }
        if (editing is Editing.Add) {
            rows.addView(editRow(R.drawable.ic_plus, ""), rowParams())
        } else {
            val full = !Categories.canAdd(cats)
            val parts = Rows.build(this, R.drawable.ic_plus, "Add category", value = if (full) "Limit reached" else null)
            addRow = addDetailRow(parts)
            parts.view.setOnClickListener {
                if (Categories.canAdd(cats)) {
                    editing = Editing.Add
                    expandedId = null
                    renderCategories()
                    startEditing()
                }
            }
        }
    }

    private fun stripParams() = LinearLayout.LayoutParams(-2, -2).apply {
        leftMargin = dp(48)
        topMargin = dp(4)
        bottomMargin = dp(4)
    }

    private fun strip(c: Category): View {
        val strip = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun add(name: String, enabled: Boolean, onClick: (TextView) -> Unit): TextView {
            val bg = rr(C_TRACK, 14)
            val t = tv(name, 13f, C_TEXT).apply {
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = bg
                isFocusable = enabled
                isClickable = enabled
                alpha = if (enabled) 1f else 0.35f
                defaultFocusHighlightEnabled = false
                setOnClickListener { onClick(this) }
                setOnFocusChangeListener { _, has ->
                    bg.setColor(if (has) Color.WHITE else C_TRACK)
                    setTextColor(if (has) C_BG else C_TEXT)
                }
            }
            pillByName[name] = t
            strip.addView(t, LinearLayout.LayoutParams(-2, -2).apply { if (strip.childCount > 0) leftMargin = dp(8) })
            return t
        }
        add("Rename", true) {
            editing = Editing.Rename(c.id)
            expandedId = null
            renderCategories()
            startEditing()
        }
        add("Move up", Categories.canMoveUp(cats, c.id)) { moveCategory(c.id, up = true) }
        add("Move down", Categories.canMoveDown(cats, c.id)) { moveCategory(c.id, up = false) }
        var armed = false
        val del = add("Delete", true) { self ->
            if (!armed) {
                armed = true
                self.text = "Confirm delete"
            } else {
                deleteCategory(c.id)
            }
        }
        val base = del.onFocusChangeListener
        del.setOnFocusChangeListener { v, has ->
            base.onFocusChange(v, has)
            if (!has && armed) {
                armed = false
                del.text = "Delete"
            }
        }
        return strip
    }

    private fun collapseStrip(focusRow: Boolean) {
        val id = expandedId ?: return
        expandedId = null
        renderCategories()
        if (focusRow) rowById[id]?.requestFocus()
    }

    private fun moveCategory(id: String, up: Boolean) {
        cats = if (up) Categories.moveUp(cats, id) else Categories.moveDown(cats, id)
        store.setCategories(cats)
        renderCategories()
        // Keep the same pill so repeated presses keep moving the category; fall back when it hit an end.
        val order = if (up) listOf("Move up", "Move down", "Rename") else listOf("Move down", "Move up", "Rename")
        order.firstNotNullOfOrNull { n -> pillByName[n]?.takeIf { it.isFocusable } }?.requestFocus()
    }

    private fun deleteCategory(id: String) {
        val idx = cats.indexOfFirst { it.id == id }
        cats = Categories.delete(cats, id)
        store.setCategories(cats)
        store.clearOverridesFor(id)
        expandedId = null
        recount()
        renderCategories()
        val next = cats.getOrNull(idx) ?: cats.last()
        rowById[next.id]?.requestFocus()
    }

    // inline editor ------------------------------------------------------

    private fun editRow(iconRes: Int, initial: String): View {
        val ed = NameEdit(this).apply {
            setText(initial)
            hint = "Category name"
            setHintTextColor(C_SEC)
            setTextColor(C_TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            includeFontPadding = false
            background = null
            setPadding(0, 0, 0, 0)
            isSingleLine = true
            maxLines = 1
            filters = arrayOf(InputFilter.LengthFilter(Categories.MAX_NAME))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            setSelectAllOnFocus(true)
            id = View.generateViewId()
            nextFocusUpId = id
            nextFocusDownId = id
            nextFocusLeftId = id
            nextFocusRightId = id
            onCancel = { cancelEdit() }
            setOnEditorActionListener { _, action, ev ->
                val enter = ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN
                if (action == EditorInfo.IME_ACTION_DONE || enter) {
                    commitEdit()
                    true
                } else {
                    false
                }
            }
        }
        editor = ed
        val bg = rr(C_TRACK, 9).apply { setStroke(dp(2), C_BLUE) }
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
            background = bg
            addView(
                ImageView(context).apply {
                    setImageResource(iconRes)
                    setColorFilter(C_ICON)
                },
                LinearLayout.LayoutParams(dp(18), dp(18))
            )
            addView(ed, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
        }
    }

    private fun startEditing() {
        val ed = editor ?: return
        ed.requestFocus()
        ed.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(ed, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        val ed = editor ?: return
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(ed.windowToken, 0)
    }

    private fun commitEdit() {
        val e = editing ?: return
        val name = Categories.sanitize(editor?.text?.toString().orEmpty())
        if (name.isEmpty()) {
            cancelEdit()
            return
        }
        hideKeyboard()
        editing = null
        when (e) {
            is Editing.Rename -> {
                cats = Categories.rename(cats, e.id, name)
                store.setCategories(cats)
                renderCategories()
                rowById[e.id]?.requestFocus()
            }
            Editing.Add -> {
                cats = Categories.add(cats, name)
                store.setCategories(cats)
                recount()
                renderCategories()
                rowById[cats.last().id]?.requestFocus()
            }
        }
    }

    private fun cancelEdit() {
        val e = editing ?: return
        hideKeyboard()
        editing = null
        renderCategories()
        when (e) {
            is Editing.Rename -> rowById[e.id]?.requestFocus()
            Editing.Add -> addRow?.requestFocus()
        }
    }

    // ---------------------------------------------------------------- home cards

    private fun renderCards(): Unit = rebuild {
        toggleRow(R.drawable.wx_partly, "Weather card", store.showWeatherCard) { store.showWeatherCard = it }
        toggleRow(R.drawable.ic_bolt, "System card", store.showSystemCard) { store.showSystemCard = it }
    }

    private fun toggleRow(iconRes: Int, label: String, initial: Boolean, save: (Boolean) -> Unit) {
        val t = Toggle(this).apply { on = initial }
        val parts = Rows.build(this, iconRes, label, trailingView = t)
        parts.onFocus = { has -> t.rowFocused = has }
        var on = initial
        parts.view.setOnClickListener {
            on = !on
            t.on = on
            save(on)
        }
        addDetailRow(parts)
    }

    // ---------------------------------------------------------------- about

    private fun renderAbout(): Unit = rebuild {
        fun info(icon: Int, label: String, value: String) {
            rows.addView(Rows.build(this, icon, label, value = value, focusable = false).view, rowParams())
        }
        info(R.drawable.ic_info, "Version", BuildConfig.VERSION_NAME)
        info(R.drawable.ic_category, "Package", packageName)
        info(R.drawable.ic_bolt, "RAM budget", "About 30 MB")
        val src = Rows.build(this, R.drawable.ic_home, "Source code", value = "github.com/kobiregev/volt-tv-launcher")
        addDetailRow(src)
    }
}
