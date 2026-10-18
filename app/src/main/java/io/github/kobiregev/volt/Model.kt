package io.github.kobiregev.volt

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.LruCache

class AppEntry(val pkg: String, val label: String, val component: ComponentName, val defCat: String, val ri: ResolveInfo)

/** Pre-scaled bitmaps and colors for one app. */
class Visual(val tile: Bitmap, val hasBanner: Boolean, val hero: Bitmap, val heroColor: Int, val tileBg: Int) {
    val bytes: Int get() = tile.byteCount + hero.byteCount
}

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("volt", Context.MODE_PRIVATE)

    fun order(): List<String> = (sp.getString("order", "") ?: "").split('\n').filter { it.isNotEmpty() }

    fun setOrder(l: List<String>) = sp.edit().putString("order", l.joinToString("\n")).apply()

    fun hidden(): Set<String> = HashSet(sp.getStringSet("hidden", emptySet()) ?: emptySet())
    fun setHidden(s: Set<String>) = sp.edit().putStringSet("hidden", HashSet(s)).apply()

    fun categories(): List<Category> = Categories.parse(sp.getString("categories", null))

    fun setCategories(l: List<Category>) = sp.edit().putString("categories", Categories.serialize(l)).apply()

    /** The category id the user chose for [pkg], or null for auto. */
    fun catOverride(pkg: String): String? = sp.getString("cat2:$pkg", null)

    /** Null removes the override. */
    fun setCat(pkg: String, id: String?) = sp.edit().apply {
        if (id == null) remove("cat2:$pkg") else putString("cat2:$pkg", id)
    }.apply()

    /** Removes every user override that points at category [id] (those apps go back to Auto). */
    fun clearOverridesFor(id: String) {
        val ed = sp.edit()
        for ((k, v) in sp.all) if (k.startsWith("cat2:") && v == id) ed.remove(k)
        ed.apply()
    }

    /** Home card switches (default on). */
    var showWeatherCard: Boolean
        get() = sp.getBoolean("card_weather", true)
        set(v) {
            sp.edit().putBoolean("card_weather", v).apply()
        }
    var showSystemCard: Boolean
        get() = sp.getBoolean("card_system", true)
        set(v) {
            sp.edit().putBoolean("card_system", v).apply()
        }

    init {
        migrate()
    }

    /** Schema 2: v1 `cat:<pkg>` ints become `cat2:<pkg>` ids. Runs once. */
    private fun migrate() {
        if (sp.getInt("schema", 1) >= 2) return
        val ed = sp.edit()
        for ((k, v) in sp.all) {
            if (!k.startsWith("cat:")) continue
            val id = (v as? Int)?.let { Categories.migrateV1(it) }
            if (id != null) ed.putString("cat2:" + k.substring(4), id)
            ed.remove(k)
        }
        ed.putInt("schema", 2).apply()
    }

    var weatherTemp: Float
        get() = sp.getFloat("w_temp", Float.NaN)
        set(v) {
            sp.edit().putFloat("w_temp", v).apply()
        }
    var weatherCode: Int
        get() = sp.getInt("w_code", -1)
        set(v) {
            sp.edit().putInt("w_code", v).apply()
        }
    var weatherTime: Long
        get() = sp.getLong("w_time", 0L)
        set(v) {
            sp.edit().putLong("w_time", v).apply()
        }
}

object AppLoader {
    private fun defaultCat(ai: ApplicationInfo, pkg: String): String {
        val sys = ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return Categorizer.defaultCategory(pkg, sys, ai.category)
    }

    @Suppress("DEPRECATION")
    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val lb = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            0
        )
        val la = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0
        )
        val map = LinkedHashMap<String, AppEntry>()
        fun add(ri: ResolveInfo) {
            val ai = ri.activityInfo ?: return
            val pkg = ai.packageName
            if (pkg == ctx.packageName || map.containsKey(pkg)) return
            map[pkg] = AppEntry(
                pkg,
                ri.loadLabel(pm).toString(),
                ComponentName(pkg, ai.name),
                defaultCat(ai.applicationInfo, pkg),
                ri
            )
        }
        lb.forEach { add(it) }
        la.forEach { add(it) }
        return map.values.toList()
    }
}

class Visuals(private val ctx: Context) {
    private val density = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density + 0.5f).toInt()

    val cache = object : LruCache<String, Visual>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Visual) = value.bytes
    }

    private fun render(d: Drawable, w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val old = d.copyBounds()
        d.setBounds(0, 0, w, h)
        d.draw(c)
        d.bounds = old
        return b
    }

    /** Bake the corner radius into the bitmap: view outline clipping drops it while the tile is scaled. */
    private fun rounded(src: Bitmap, r: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        Canvas(out).drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), r, r, p)
        src.recycle()
        return out
    }

    fun compute(e: AppEntry): Visual {
        val pm = ctx.packageManager
        val ai = e.ri.activityInfo
        val banner = try {
            ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
        } catch (t: Throwable) {
            null
        }
        val icon = e.ri.loadIcon(pm)
        val hero = render(icon, dp(110), dp(110))
        val tile: Bitmap
        val src: Bitmap
        if (banner != null) {
            tile = rounded(render(banner, dp(118), dp(66)), dp(9).toFloat())
            src = tile
        } else {
            tile = render(icon, dp(36), dp(36))
            src = hero
        }
        val base = dominant(src)
        val hsv = FloatArray(3)
        Color.colorToHSV(base, hsv)
        hsv[2] = minOf(hsv[2], 0.55f)
        val heroColor = Color.HSVToColor(hsv)
        hsv[2] = minOf(hsv[2], 0.38f)
        val tileBg = Color.HSVToColor(hsv)
        return Visual(tile, banner != null, hero, heroColor, tileBg)
    }

    /** Average of saturated opaque pixels (fallback: all opaque pixels) of a 24x24 copy. */
    private fun dominant(b: Bitmap): Int {
        val s = Bitmap.createScaledBitmap(b, 24, 24, true)
        val px = IntArray(24 * 24)
        s.getPixels(px, 0, 24, 0, 0, 24, 24)
        if (s !== b) s.recycle()
        var sr = 0L
        var sg = 0L
        var sb = 0L
        var sn = 0
        var ar = 0L
        var ag = 0L
        var ab = 0L
        var an = 0
        val hsv = FloatArray(3)
        for (p in px) {
            if (Color.alpha(p) < 128) continue
            val r = Color.red(p)
            val g = Color.green(p)
            val bl = Color.blue(p)
            ar += r
            ag += g
            ab += bl
            an++
            Color.RGBToHSV(r, g, bl, hsv)
            if (hsv[1] > 0.25f) {
                sr += r
                sg += g
                sb += bl
                sn++
            }
        }
        return when {
            sn > 0 -> Color.rgb((sr / sn).toInt(), (sg / sn).toInt(), (sb / sn).toInt())
            an > 0 -> Color.rgb((ar / an).toInt(), (ag / an).toInt(), (ab / an).toInt())
            else -> 0xFF3A3D42.toInt()
        }
    }
}
