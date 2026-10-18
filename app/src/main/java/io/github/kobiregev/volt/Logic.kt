package io.github.kobiregev.volt

/** Pure, Android-free launcher logic. Kept free of framework types so it can be unit-tested on the JVM. */
object Categorizer {
    const val CATEGORY_AUDIO = 1 // ApplicationInfo.CATEGORY_AUDIO
    const val CATEGORY_VIDEO = 2 // ApplicationInfo.CATEGORY_VIDEO

    private val SYSTEM_PKGS = setOf("com.android.tv.settings", "com.android.settings", "com.android.vending")

    // File managers only: "file" alone would also match e.g. "...profile...".
    private val FILES = Regex("(?i)(^|\\.)files?($|\\.)|file(manager|explorer|browser|commander)")
    private val MEDIA = listOf(
        "netflix", "youtube", "stremio", "jellyfin", "cellcom",
        "disney", "amazon", "plex", "kodi", "spotify"
    )

    /** Default dock category of an app: [CAT_SYSTEM] or [CAT_STREAMING]. */
    fun defaultCategory(pkg: String, isSystem: Boolean, appCategory: Int): Int {
        if (pkg in SYSTEM_PKGS || FILES.containsMatchIn(pkg)) return CAT_SYSTEM
        if (isSystem) {
            val media = appCategory == CATEGORY_VIDEO || appCategory == CATEGORY_AUDIO || MEDIA.any { pkg.contains(it) }
            if (!media) return CAT_SYSTEM
        }
        return CAT_STREAMING
    }
}

object DockOrder {
    /** Drops hidden packages, then sorts by saved position (unknown last), ties broken by lowercase label. */
    fun sortVisible(entries: List<Pair<String, String>>, savedOrder: List<String>, hidden: Set<String>): List<Pair<String, String>> {
        val idx = HashMap<String, Int>()
        savedOrder.forEachIndexed { i, p -> if (p !in idx) idx[p] = i }
        return entries.filter { it.first !in hidden }.sortedWith(
            compareBy<Pair<String, String>> { idx[it.first] ?: Int.MAX_VALUE }.thenBy { it.second.lowercase() }
        )
    }

    /**
     * Swaps [pkg] with its neighbour in the currently [shown] list (dir = -1 left, +1 right) inside the
     * full visible order [allVisible]; packages only present in [savedOrder] are appended.
     * Returns null when the move is not possible.
     */
    fun move(allVisible: List<String>, shown: List<String>, pkg: String, dir: Int, savedOrder: List<String>): List<String>? {
        val i = shown.indexOf(pkg)
        val j = i + dir
        if (i < 0 || j !in shown.indices) return null
        val order = allVisible.toMutableList()
        val a = order.indexOf(pkg)
        val b = order.indexOf(shown[j])
        if (a < 0 || b < 0) return null
        java.util.Collections.swap(order, a, b)
        for (p in savedOrder) if (p !in order) order.add(p)
        return order
    }

    /**
     * Full order after the shown list changed from [shownBefore] to [shownAfter] (a permutation of it).
     * The slots in [allVisible] that held [shownBefore] items are refilled in order with [shownAfter];
     * every other package keeps its slot. Packages only present in [savedOrder] are appended, like [move].
     */
    fun reorder(
        allVisible: List<String>,
        shownBefore: List<String>,
        shownAfter: List<String>,
        savedOrder: List<String> = emptyList()
    ): List<String> {
        val moved = shownBefore.toSet()
        val it = shownAfter.iterator()
        val order = allVisible.map { p -> if (p in moved && it.hasNext()) it.next() else p }.toMutableList()
        for (p in savedOrder) if (p !in order) order.add(p)
        return order
    }
}

object WeatherCodes {
    /** Maps a WMO weather code (Open-Meteo) to display text. */
    fun condition(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..67, in 80..82 -> "Rain"
        in 71..77, 85, 86 -> "Snow"
        95, 96, 99 -> "Storm"
        else -> "Cloudy"
    }
}

object WifiText {
    fun band(frequencyMhz: Int): String = if (frequencyMhz > 4900) "5 GHz" else "2.4 GHz"

    fun quality(rssi: Int): String = if (rssi > -60) {
        "strong"
    } else if (rssi > -70) {
        "good"
    } else {
        "weak"
    }

    fun describe(frequencyMhz: Int, rssi: Int): String = "${band(frequencyMhz)} · ${quality(rssi)}"
}
