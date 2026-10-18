package io.github.kobiregev.volt

import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal const val C_BG = 0xFF141517.toInt()
internal const val C_CARD = 0xFF1D1F22.toInt()
internal const val C_DOCK = 0xFF1B1D20.toInt()
internal const val C_SEL = 0xFF34373B.toInt()
internal const val C_TEXT = 0xFFE9EAEC.toInt()
internal const val C_SEC = 0xFF8D9197.toInt()
internal const val C_BLUE = 0xFF3E6AE1.toInt()
internal const val C_TRACK = 0xFF2C2F33.toInt()
internal const val C_ICON = 0xFFC9CCD0.toInt()

/** Horizontal scroll view that leaves scrolling to us (smooth, centered on the focused tile). */
private class SmoothHsv(c: Context) : HorizontalScrollView(c) {
    override fun requestChildRectangleOnScreen(child: View, rectangle: android.graphics.Rect, immediate: Boolean) = false
}

private class Bar(c: Context) : View(c) {
    var frac = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        p.color = C_TRACK
        r.set(0f, 0f, width.toFloat(), h)
        canvas.drawRoundRect(r, h / 2, h / 2, p)
        p.color = C_BLUE
        r.set(0f, 0f, width * frac, h)
        canvas.drawRoundRect(r, h / 2, h / 2, p)
    }
}

class HomeActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val exec: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var store: Store
    private lateinit var visuals: Visuals
    private var density = 1f
    private var destroyed = false
    private var resumed = false

    // data
    private var allEntries: List<AppEntry> = emptyList()
    private var allApps: List<AppEntry> = emptyList() // visible, sorted
    private var shown: List<AppEntry> = emptyList()
    private var category = CAT_ALL
    private var heroEntry: AppEntry? = null
    private var lastTilePkg: String? = null
    private var pendingInitialFocus = true

    // views
    private lateinit var heroBg: GradientDrawable
    private lateinit var heroCat: TextView
    private lateinit var heroName: TextView
    private lateinit var heroIcon: ImageView
    private lateinit var btnOpen: TextView
    private lateinit var btnOptions: TextView
    private lateinit var wxTemp: TextView
    private lateinit var wxCond: TextView
    private lateinit var wxIcon: ImageView
    private lateinit var sysWifi: TextView
    private lateinit var sysMem: TextView
    private lateinit var sysBar: Bar
    private lateinit var topTemp: TextView
    private lateinit var topWifi: ImageView
    private lateinit var seg: LinearLayout
    private val segViews = ArrayList<TextView>()
    private lateinit var quickRow: LinearLayout
    private lateinit var hsv: SmoothHsv
    private lateinit var dockRow: LinearLayout
    private lateinit var frame: FrameLayout
    private lateinit var sheet: Sheet
    private val quickRefresh = ArrayList<() -> Unit>()
    private var heroColor = C_CARD
    private var heroAnim: ValueAnimator? = null

    private fun dp(v: Int) = (v * density + 0.5f).toInt()
    private fun dpf(v: Float) = v * density

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        density = resources.displayMetrics.density
        store = Store(this)
        visuals = Visuals(this)
        val content = buildUi()
        frame = FrameLayout(this)
        frame.addView(content, FrameLayout.LayoutParams(-1, -1))
        sheet = Sheet(this, frame)
        setContentView(frame)
        showWeather()
        updateSystem()
        seg.requestFocus()

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(pkgReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pkgReceiver, f)
        }
        reload()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        refreshQuick()
        main.removeCallbacks(sysTick)
        main.post(sysTick)
        main.removeCallbacks(weatherTick)
        if (System.currentTimeMillis() - store.weatherTime > Weather.MAX_AGE_MS) refreshWeather()
        main.postDelayed(weatherTick, Weather.MAX_AGE_MS)
        if (currentFocus == null) focusFirstTileOrSeg()
    }

    override fun onPause() {
        resumed = false
        main.removeCallbacks(sysTick)
        main.removeCallbacks(weatherTick)
        main.removeCallbacks(weatherRetry)
        super.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        main.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(pkgReceiver)
        } catch (_: Exception) {}
        exec.shutdownNow()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            sheet.close()
            setCategory(CAT_ALL, focusFirst = true)
            hsv.scrollTo(0, 0)
        }
    }

    @Deprecated("Back only closes the sheet on the home screen")
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (sheet.isOpen) sheet.close()
    }

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        if (ev.action == KeyEvent.ACTION_DOWN && ev.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            val f = currentFocus
            if (f != null && (f === seg || quickRow.indexOfChild(f) >= 0)) {
                val t = lastDockTile()
                if (t != null && t.requestFocus()) return true
            }
        }
        return super.dispatchKeyEvent(ev)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (sheet.isOpen) {
                sheet.close()
                return true
            }
            heroEntry?.let { showOptions(it) }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private val sysTick = object : Runnable {
        override fun run() {
            updateSystem()
            if (resumed) main.postDelayed(this, 5000)
        }
    }

    private val weatherTick = object : Runnable {
        override fun run() {
            refreshWeather()
            if (resumed) main.postDelayed(this, Weather.MAX_AGE_MS)
        }
    }

    private val weatherRetry = Runnable { refreshWeather() }

    private val reloadRun = Runnable { reload() }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            i.data?.schemeSpecificPart?.let { visuals.cache.remove(it) }
            main.removeCallbacks(reloadRun)
            main.postDelayed(reloadRun, 600)
        }
    }

    // ---------------------------------------------------------------- UI building

    private fun tv(text: String, sp: Float, color: Int, face: String = "sans-serif"): TextView = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        typeface = Typeface.create(face, Typeface.NORMAL)
        includeFontPadding = false
    }

    private fun rr(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dpf(radiusDp.toFloat())
    }

    private fun icon(res: Int, tint: Int, sizeDp: Int): ImageView = ImageView(this).apply {
        setImageResource(res)
        setColorFilter(tint)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(C_BG)
            setPadding(dp(32), dp(16), dp(32), dp(12))
        }

        // 1. status bar
        val status = FrameLayout(this)
        val left = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        left.addView(icon(R.drawable.ic_home, C_SEC, 16))
        left.addView(
            tv(deviceName(), 14f, C_SEC).apply {
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(6) }
            }
        )
        status.addView(left, FrameLayout.LayoutParams(-2, -1, Gravity.START or Gravity.CENTER_VERTICAL))
        val clock = TextClock(this).apply {
            format24Hour = "HH:mm"
            format12Hour = "HH:mm"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(C_TEXT)
            includeFontPadding = false
        }
        status.addView(clock, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        val right = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        topTemp = tv("--°", 16f, C_TEXT)
        right.addView(topTemp)
        topWifi = icon(R.drawable.ic_wifi, C_TEXT, 20).also {
            (it.layoutParams as LinearLayout.LayoutParams).leftMargin = dp(10)
        }
        right.addView(topWifi)
        status.addView(right, FrameLayout.LayoutParams(-2, -1, Gravity.END or Gravity.CENTER_VERTICAL))
        root.addView(status, LinearLayout.LayoutParams(-1, dp(28)))

        // 2. top row
        val top = LinearLayout(this)
        root.addView(top, LinearLayout.LayoutParams(-1, dp(228)).apply { topMargin = dp(12) })

        val hero = FrameLayout(this)
        heroBg = rr(C_CARD, 12)
        hero.background = heroBg
        top.addView(hero, LinearLayout.LayoutParams(0, -1, 1.75f))
        heroIcon = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        hero.addView(
            heroIcon,
            FrameLayout.LayoutParams(dp(110), dp(110), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(20)
                rightMargin = dp(24)
            }
        )
        val heroCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heroCat = tv("", 13f, 0xB3FFFFFF.toInt())
        heroName = tv("", 36f, Color.WHITE, "sans-serif-medium").apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = dp(400)
        }
        heroCol.addView(heroCat)
        heroCol.addView(heroName, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
        val btnRow = LinearLayout(this)
        btnOpen = pill("Open", C_BLUE)
        btnOptions = pill("Options", 0x24FFFFFF)
        btnOpen.setOnClickListener { heroEntry?.let { launch(it) } }
        btnOptions.setOnClickListener { heroEntry?.let { showOptions(it) } }
        btnRow.addView(btnOpen, LinearLayout.LayoutParams(-2, dp(36)))
        btnRow.addView(btnOptions, LinearLayout.LayoutParams(-2, dp(36)).apply { leftMargin = dp(10) })
        heroCol.addView(btnRow, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14) })
        hero.addView(
            heroCol,
            FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = dp(24)
                bottomMargin = dp(20)
            }
        )

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        top.addView(col, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(14) })

        // weather card
        val wx = FrameLayout(this).apply { background = rr(C_CARD, 12) }
        col.addView(wx, LinearLayout.LayoutParams(-1, 0, 1f))
        val wxCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wxTemp = tv("--°", 42f, C_TEXT, "sans-serif-light")
        wxCond = tv("", 14f, C_SEC)
        wxCol.addView(wxTemp)
        wxCol.addView(wxCond, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        wx.addView(
            wxCol,
            FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                leftMargin = dp(20)
            }
        )
        wxIcon = ImageView(this)
        wx.addView(
            wxIcon,
            FrameLayout.LayoutParams(dp(56), dp(56), Gravity.END or Gravity.CENTER_VERTICAL).apply {
                rightMargin = dp(20)
            }
        )

        // system card
        val sys = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = rr(C_CARD, 12)
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        col.addView(sys, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(14) })
        sysWifi = tv("", 15f, C_TEXT)
        sysMem = tv("", 15f, C_TEXT)
        sys.addView(sysRow("Wi-Fi", sysWifi))
        sys.addView(sysRow("Free memory", sysMem), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        sysBar = Bar(this)
        sys.addView(sysBar, LinearLayout.LayoutParams(-1, dp(4)).apply { topMargin = dp(10) })

        // 3. middle row
        val mid = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        root.addView(mid, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(14) })
        seg = LinearLayout(this).apply {
            background = rr(C_CARD, 9)
            setPadding(dp(3), dp(3), dp(3), dp(3))
            isFocusable = true
            defaultFocusHighlightEnabled = false
            id = View.generateViewId()
            setOnKeyListener { _, code, ev ->
                if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (category > 0) setCategory(category - 1)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> if (category < 2) {
                        setCategory(category + 1)
                        true
                    } else {
                        false
                    }
                    else -> false
                }
            }
            setOnFocusChangeListener { _, _ -> updateSeg() }
        }
        for (i in 0..2) {
            val t = tv(catName(i), 14f, C_TEXT).apply {
                gravity = Gravity.CENTER
                setPadding(dp(18), 0, dp(18), 0)
            }
            segViews.add(t)
            seg.addView(t, LinearLayout.LayoutParams(-2, -1))
        }
        mid.addView(seg, LinearLayout.LayoutParams(-2, -1))
        mid.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        quickRow = LinearLayout(this)
        mid.addView(quickRow, LinearLayout.LayoutParams(-2, -1))
        quick(R.drawable.ic_wifi, { wifiOn() }) { openSettings(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_SETTINGS) }
        quick(R.drawable.ic_bluetooth, { btOn() }) { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, Settings.ACTION_SETTINGS) }
        quick(R.drawable.ic_bolt) { freeMemory() }
        quick(R.drawable.ic_moon) {
            openSettings(Settings.ACTION_DREAM_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SETTINGS)
        }
        quick(R.drawable.ic_refresh) { openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS, Settings.ACTION_SETTINGS) }
        quick(R.drawable.ic_settings) { openSettings(Settings.ACTION_SETTINGS) }

        // explicit focus links
        btnOpen.id = View.generateViewId()
        btnOptions.id = View.generateViewId()
        btnOpen.nextFocusDownId = seg.id
        btnOptions.nextFocusDownId = seg.id
        seg.nextFocusUpId = btnOpen.id
        for (i in 0 until quickRow.childCount) quickRow.getChildAt(i).nextFocusUpId = btnOptions.id

        // 4. dock
        root.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))
        val dock = LinearLayout(this).apply {
            background = rr(C_DOCK, 12)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            clipChildren = false
            clipToPadding = false
        }
        hsv = SmoothHsv(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, dp(10), 0, 0)
        }
        dockRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
        }
        hsv.addView(dockRow, FrameLayout.LayoutParams(-2, -2))
        dock.addView(hsv, LinearLayout.LayoutParams(-1, -2))
        root.addView(dock, LinearLayout.LayoutParams(-1, -2))

        updateSeg()
        return root
    }

    /** The name the user gave the device in Settings, else the model. */
    private fun deviceName(): String = Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
        ?: Build.MODEL

    private fun sysRow(label: String, value: TextView): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(tv(label, 13f, C_SEC), LinearLayout.LayoutParams(0, -2, 1f))
        addView(value)
    }

    private fun pill(text: String, bg: Int): TextView {
        val d = rr(bg, 18)
        return tv(text, 14f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setPadding(dp(20), 0, dp(20), 0)
            background = d
            isFocusable = true
            isClickable = true
            defaultFocusHighlightEnabled = false
            setOnFocusChangeListener { v, has ->
                d.setStroke(if (has) dp(2) else 0, Color.WHITE)
                v.animate().scaleX(if (has) 1.05f else 1f).scaleY(if (has) 1.05f else 1f).setDuration(120).start()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun wifiOn(): Boolean = try {
        (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    } catch (_: Exception) {
        false
    }

    private fun btOn(): Boolean = try {
        Settings.Global.getInt(contentResolver, "bluetooth_on", 0) == 1
    } catch (_: Exception) {
        false
    }

    private fun refreshQuick() {
        for (r in quickRefresh) r()
    }

    private fun quick(iconRes: Int, stateFn: (() -> Boolean)? = null, action: () -> Unit) {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(C_CARD)
        }
        val img = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(C_ICON)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = bg
        }
        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(C_BLUE)
            }
            visibility = View.GONE
        }
        val b = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            isFocusable = true
            isClickable = true
            defaultFocusHighlightEnabled = false
            setOnClickListener { action() }
        }
        b.addView(img, FrameLayout.LayoutParams(-1, -1))
        b.addView(
            dot,
            FrameLayout.LayoutParams(dp(4), dp(4), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(4) }
        )
        var on = false
        val apply = {
            val has = b.isFocused
            bg.setColor(if (has) C_BLUE else C_CARD)
            img.setColorFilter(
                if (has) {
                    Color.WHITE
                } else if (on) {
                    C_BLUE
                } else {
                    C_ICON
                }
            )
            dot.visibility = if (on && !has) View.VISIBLE else View.GONE
        }
        b.setOnFocusChangeListener { _, _ -> apply() }
        if (stateFn != null) {
            quickRefresh.add {
                on = stateFn()
                apply()
            }
        }
        quickRow.addView(
            b,
            LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                if (quickRow.childCount > 0) leftMargin = dp(10)
            }
        )
    }

    private fun updateSeg() {
        val focused = seg.hasFocus()
        for (i in 0..2) {
            segViews[i].background = if (i == category) rr(if (focused) C_BLUE else C_SEL, 7) else null
            segViews[i].setTextColor(if (i == category) Color.WHITE else C_SEC)
        }
    }

    // ---------------------------------------------------------------- dock

    private fun catOf(e: AppEntry): Int {
        val o = store.catOverride(e.pkg)
        return if (o != 0) o else e.defCat
    }

    private fun applySort() {
        val byPkg = allEntries.associateBy { it.pkg }
        allApps = DockOrder.sortVisible(allEntries.map { it.pkg to it.label }, store.order(), store.hidden())
            .mapNotNull { byPkg[it.first] }
    }

    private fun reload() {
        exec.execute {
            val list = try {
                AppLoader.load(this)
            } catch (t: Throwable) {
                return@execute
            }
            main.post {
                if (destroyed) return@post
                allEntries = list
                applySort()
                rebuildDock(null, false)
            }
        }
    }

    private fun setCategory(c: Int, focusFirst: Boolean = false) {
        category = c
        updateSeg()
        rebuildDock(null, focusFirst)
    }

    private fun rebuildDock(focusPkg: String?, focusFirst: Boolean) {
        val focusedTile = currentFocus?.takeIf { it.tag is AppEntry && it.parent === dockRow }
        val wasInDock = focusedTile != null
        val keepPkg = focusPkg ?: (focusedTile?.tag as? AppEntry)?.pkg
        dockRow.removeAllViews()
        shown = allApps.filter { category == CAT_ALL || catOf(it) == category }
        var target: View? = null
        for (e in shown) {
            val t = makeTile(e)
            dockRow.addView(t, LinearLayout.LayoutParams(-2, -2))
            if (e.pkg == keepPkg) target = t
        }
        val doFocus = focusFirst || pendingInitialFocus || wasInDock || focusPkg != null
        if (shown.isNotEmpty() && pendingInitialFocus) pendingInitialFocus = false
        if (doFocus && shown.isNotEmpty()) {
            val t = (if (focusFirst) null else target) ?: dockRow.getChildAt(0)
            t.requestFocus()
        } else {
            if (currentFocus == null) seg.requestFocus()
            if (heroEntry == null || shown.none { it === heroEntry }) showHero(shown.firstOrNull())
        }
        if (shown.isEmpty()) showHero(null)
    }

    private fun lastDockTile(): View? {
        val p = lastTilePkg
        for (i in 0 until dockRow.childCount) {
            val v = dockRow.getChildAt(i)
            if ((v.tag as? AppEntry)?.pkg == p) return v
        }
        return dockRow.getChildAt(0)
    }

    private fun focusFirstTileOrSeg() {
        if (dockRow.childCount > 0) dockRow.getChildAt(0).requestFocus() else seg.requestFocus()
    }

    private fun makeTile(e: AppEntry): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true
            isClickable = true
            isLongClickable = true
            defaultFocusHighlightEnabled = false
            clipChildren = false
            // The padding box is exactly the tile: with clipping on, the focus zoom is cut to its old size.
            clipToPadding = false
            setPadding(dp(10), 0, dp(10), 0)
            tag = e
        }
        val tileBg = rr(0xFF2A2D31.toInt(), 9)
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER
            background = tileBg
            alpha = 0.55f
        }
        col.addView(img, LinearLayout.LayoutParams(dp(118), dp(66)))
        val dotBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(C_BLUE)
        }
        val dot = View(this).apply {
            background = dotBg
            visibility = View.INVISIBLE
        }
        col.addView(dot, LinearLayout.LayoutParams(dp(5), dp(5)).apply { topMargin = dp(8) })
        val name = tv(e.label, 12f, C_TEXT).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = dp(138)
            visibility = View.INVISIBLE
        }
        col.addView(name, LinearLayout.LayoutParams(-2, dp(16)).apply { topMargin = dp(4) })

        col.setOnFocusChangeListener { v, has ->
            img.animate().cancel()
            img.animate()
                .scaleX(if (has) 1.15f else 1f).scaleY(if (has) 1.15f else 1f)
                .translationY(if (has) -dpf(4f) else 0f)
                .alpha(if (has) 1f else 0.55f)
                .setDuration(150).start()
            val vis = if (has) View.VISIBLE else View.INVISIBLE
            dot.visibility = vis
            name.visibility = vis
            if (has) {
                lastTilePkg = e.pkg
                showHero(e)
                scrollToTile(v)
            }
        }
        col.setOnClickListener { launch(e) }
        col.setOnLongClickListener {
            showOptions(e)
            true
        }

        loadVisual(e) { v ->
            if (v.hasBanner) img.background = null else tileBg.setColor(v.tileBg)
            img.scaleType = if (v.hasBanner) ImageView.ScaleType.FIT_XY else ImageView.ScaleType.CENTER
            img.setImageBitmap(v.tile)
        }
        return col
    }

    private fun scrollToTile(v: View) {
        hsv.post {
            val maxScroll = maxOf(0, dockRow.width - hsv.width)
            val target = (v.left + v.width / 2 - hsv.width / 2).coerceIn(0, maxScroll)
            hsv.smoothScrollTo(target, 0)
        }
    }

    private fun loadVisual(e: AppEntry, cb: (Visual) -> Unit) {
        visuals.cache.get(e.pkg)?.let {
            cb(it)
            return
        }
        try {
            exec.execute {
                val v = visuals.cache.get(e.pkg) ?: try {
                    visuals.compute(e).also { visuals.cache.put(e.pkg, it) }
                } catch (t: Throwable) {
                    return@execute
                }
                main.post { if (!destroyed) cb(v) }
            }
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------- hero

    private fun showHero(e: AppEntry?) {
        heroEntry = e
        if (e == null) {
            heroCat.text = ""
            heroName.text = ""
            heroIcon.setImageDrawable(null)
            animateHero(C_CARD)
            return
        }
        heroCat.text = catName(catOf(e))
        heroName.text = e.label
        heroIcon.setImageDrawable(null)
        loadVisual(e) { v ->
            if (heroEntry === e) {
                heroIcon.setImageBitmap(v.hero)
                animateHero(v.heroColor)
            }
        }
    }

    private fun animateHero(to: Int) {
        heroAnim?.cancel()
        heroAnim = ValueAnimator.ofArgb(heroColor, to).apply {
            duration = 250
            addUpdateListener {
                heroColor = it.animatedValue as Int
                heroBg.setColor(heroColor)
            }
            start()
        }
    }

    // ---------------------------------------------------------------- options

    private fun showOptions(e: AppEntry) {
        if (sheet.isOpen) return
        val cur = catOf(e)
        val other = if (cur == CAT_SYSTEM) CAT_STREAMING else CAT_SYSTEM
        val tile = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_XY }
        loadVisual(e) { v ->
            if (!v.hasBanner) tile.background = rr(v.tileBg, 6)
            tile.setImageBitmap(v.tile)
        }
        val head = sheet.header(tile, e.label, "${catName(cur)} · position ${shown.indexOf(e) + 1} of ${shown.size}")

        // Close first so focus returns to the dock, then run the action (it may rebuild the dock).
        fun act(f: () -> Unit): () -> Unit = {
            sheet.close()
            f()
        }
        sheet.show(
            head,
            listOf(
                SheetRow("Move left", R.drawable.ic_move, onClick = act { move(e, -1) }),
                SheetRow("Move right", R.drawable.ic_move, onClick = act { move(e, 1) }),
                SheetRow(
                    "Category",
                    R.drawable.ic_category,
                    value = catName(cur),
                    chevron = true,
                    onClick = act {
                        store.setCat(e.pkg, other)
                        val keep = if (category == CAT_ALL || category == other) e.pkg else null
                        heroEntry = null
                        rebuildDock(keep, keep == null)
                    }
                ),
                SheetRow("Hide from dock", R.drawable.ic_hide, onClick = act { hide(e) }),
                SheetRow(
                    "App info",
                    R.drawable.ic_info,
                    onClick = act {
                        try {
                            startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${e.pkg}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {
                            toast("App info unavailable")
                        }
                    }
                ),
                SheetDivider,
                SheetRow(
                    "Show hidden apps",
                    R.drawable.ic_restore,
                    value = store.hidden().size.toString(),
                    onClick = act {
                        store.setHidden(emptySet())
                        applySort()
                        rebuildDock(e.pkg, false)
                    }
                )
            )
        )
    }

    private fun move(e: AppEntry, dir: Int) {
        val order = DockOrder.move(allApps.map { it.pkg }, shown.map { it.pkg }, e.pkg, dir, store.order()) ?: return
        store.setOrder(order)
        applySort()
        rebuildDock(e.pkg, false)
    }

    private fun hide(e: AppEntry) {
        val i = shown.indexOf(e)
        store.setHidden(store.hidden() + e.pkg)
        applySort()
        val next = (shown.getOrNull(i + 1) ?: shown.getOrNull(i - 1))?.pkg
        heroEntry = null
        rebuildDock(next, next == null)
    }

    // ---------------------------------------------------------------- actions

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun launch(e: AppEntry) {
        val pm = packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(e.pkg)
            ?: pm.getLaunchIntentForPackage(e.pkg)
            ?: Intent(Intent.ACTION_MAIN).setComponent(e.component)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(i)
        } catch (_: ActivityNotFoundException) {
            toast("Cannot open ${e.label}")
        } catch (_: SecurityException) {
            toast("Cannot open ${e.label}")
        }
    }

    private fun openSettings(vararg actions: String) {
        for (a in actions) {
            try {
                startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        toast("Not available on this device")
    }

    private fun availMem(): Long {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem
    }

    private fun freeMemory() {
        val before = availMem()
        val entries = allEntries
        exec.execute {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pkgs = HashSet<String>()
            entries.forEach { pkgs.add(it.pkg) }
            val protect = HashSet<String>()
            am.runningAppProcesses?.forEach { p ->
                if (p.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                    protect.addAll(p.pkgList)
                } else {
                    pkgs.addAll(p.pkgList)
                }
            }
            pkgs.remove(packageName)
            pkgs.removeAll(protect)
            for (p in pkgs) {
                try {
                    am.killBackgroundProcesses(p)
                } catch (_: Exception) {}
            }
            main.postDelayed({
                if (!destroyed) {
                    val mb = maxOf(0L, (availMem() - before) / (1024 * 1024))
                    toast("Freed $mb MB")
                    updateSystem()
                }
            }, 1000)
        }
    }

    // ---------------------------------------------------------------- weather / system

    private fun refreshWeather() {
        if (!Weather.enabled) return
        try {
            exec.execute {
                val r = Weather.fetch()
                if (r == null) {
                    // Network is often not ready right after boot: retry soon, not in 30 min.
                    main.post {
                        if (resumed && !destroyed) {
                            main.removeCallbacks(weatherRetry)
                            main.postDelayed(weatherRetry, Weather.RETRY_MS)
                        }
                    }
                    return@execute
                }
                store.weatherTemp = r.temp
                store.weatherCode = r.code
                store.weatherTime = System.currentTimeMillis()
                main.post { if (!destroyed) showWeather() }
            }
        } catch (_: Exception) {
        }
    }

    private fun showWeather() {
        val t = store.weatherTemp
        val code = store.weatherCode
        if (!Weather.enabled) {
            wxCond.text = "Weather off"
            return
        }
        if (t.isNaN() || code < 0) {
            wxCond.text = "Weather · ${Weather.CITY}"
            return
        }
        val s = "${Math.round(t)}°"
        wxTemp.text = s
        topTemp.text = s
        wxCond.text = "${Weather.condition(code)} · ${Weather.CITY}"
        wxIcon.setImageResource(Weather.icon(code))
    }

    @Suppress("DEPRECATION")
    private fun updateSystem() {
        refreshQuick()
        // network
        var text = "Offline"
        var online = false
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    text = "Ethernet"
                    online = true
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    online = true
                    val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val info = wm.connectionInfo
                    text = WifiText.describe(info.frequency, info.rssi)
                } else {
                    online = true
                    text = "Connected"
                }
            }
        } catch (_: Exception) {
        }
        sysWifi.text = text
        topWifi.setImageResource(if (online) R.drawable.ic_wifi else R.drawable.ic_wifi_off)

        // memory
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        sysMem.text = "${mi.availMem / (1024 * 1024)} MB"
        sysBar.frac = if (mi.totalMem > 0) mi.availMem.toFloat() / mi.totalMem else 0f
    }
}
