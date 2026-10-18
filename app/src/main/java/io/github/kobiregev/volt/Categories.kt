package io.github.kobiregev.volt

/** A dock category. The position in the list is the display order. */
data class Category(val id: String, val name: String)

/** Pure, Android-free category list logic: defaults, storage format, editing and resolving. */
object Categories {
    const val ALL = "all"
    const val STREAMING = "streaming"
    const val MUSIC = "music"
    const val GAMES = "games"
    const val TOOLS = "tools"
    const val SYSTEM = "system"
    const val MAX_NAME = 24
    const val MAX_CATEGORIES = 12

    // v1 stored the override as an int: 0 = none, 1 = Streaming, 2 = System.
    private const val V1_STREAMING = 1
    private const val V1_SYSTEM = 2

    val DEFAULTS: List<Category> = listOf(
        Category(ALL, "All"),
        Category(STREAMING, "Streaming"),
        Category(MUSIC, "Music"),
        Category(GAMES, "Games"),
        Category(TOOLS, "Tools"),
        Category(SYSTEM, "System")
    )

    /** Strips tabs and line breaks, trims, and cuts to [MAX_NAME] characters. May return an empty string. */
    fun sanitize(name: String): String = name.map { if (it == '\t' || it == '\n' || it == '\r') ' ' else it }
        .joinToString("").trim().take(MAX_NAME).trim()

    /** One category per line: `id<TAB>name`. */
    fun serialize(list: List<Category>): String = list.joinToString("\n") { "${it.id}\t${it.name}" }

    /** Reads [serialize] output. Bad or empty input gives [DEFAULTS]. "all" is always present and first. */
    fun parse(s: String?): List<Category> {
        if (s.isNullOrBlank()) return DEFAULTS
        val seen = HashSet<String>()
        val out = ArrayList<Category>()
        for (line in s.split('\n')) {
            val t = line.indexOf('\t')
            if (t <= 0) continue
            val id = line.substring(0, t).trim()
            val name = sanitize(line.substring(t + 1))
            if (id.isEmpty() || id.any { it.isWhitespace() } || name.isEmpty() || !seen.add(id)) continue
            out.add(Category(id, if (id == ALL) DEFAULTS[0].name else name))
        }
        if (out.isEmpty()) return DEFAULTS
        val all = out.firstOrNull { it.id == ALL } ?: DEFAULTS[0]
        return listOf(all) + out.filter { it.id != ALL }
    }

    /** Name of category [id], or "All" when it is unknown. */
    fun nameOf(list: List<Category>, id: String): String = list.firstOrNull { it.id == id }?.name ?: DEFAULTS[0].name

    /** Adds a category at the end with the next free id ("u1", "u2", ...). Unchanged when the name is empty. */
    fun add(list: List<Category>, name: String): List<Category> {
        val n = sanitize(name)
        if (n.isEmpty()) return list
        var i = 1
        while (list.any { it.id == "u$i" }) i++
        return list + Category("u$i", n)
    }

    /** Renames [id]. "all" cannot be renamed; an empty name changes nothing. */
    fun rename(list: List<Category>, id: String, name: String): List<Category> {
        val n = sanitize(name)
        if (id == ALL || n.isEmpty()) return list
        return list.map { if (it.id == id) it.copy(name = n) else it }
    }

    /** Deletes [id]. "all" cannot be deleted. */
    fun delete(list: List<Category>, id: String): List<Category> = if (id == ALL) list else list.filter { it.id != id }

    /** Moves [id] one place up. It never goes above "all". */
    fun moveUp(list: List<Category>, id: String): List<Category> {
        val i = list.indexOfFirst { it.id == id }
        return if (id == ALL || i < 2) list else swap(list, i, i - 1)
    }

    /** Moves [id] one place down. */
    fun moveDown(list: List<Category>, id: String): List<Category> {
        val i = list.indexOfFirst { it.id == id }
        return if (id == ALL || i < 0 || i >= list.size - 1) list else swap(list, i, i + 1)
    }

    private fun swap(list: List<Category>, a: Int, b: Int): List<Category> = list.toMutableList().also {
        java.util.Collections.swap(it, a, b)
    }

    /**
     * The category of an app: the user override if it still exists (and is not "all"), else [autoId].
     * When [autoId] was deleted, falls back to "streaming", else the first non-"all" category, else "all".
     */
    fun resolve(overrideId: String?, autoId: String, categories: List<Category>): String {
        if (overrideId != null && overrideId != ALL && categories.any { it.id == overrideId }) return overrideId
        if (categories.any { it.id == autoId }) return autoId
        if (categories.any { it.id == STREAMING }) return STREAMING
        return categories.firstOrNull { it.id != ALL }?.id ?: ALL
    }

    /** True when another category fits (the cap counts "all" too). */
    fun canAdd(list: List<Category>): Boolean = list.size < MAX_CATEGORIES

    /** True when [moveUp] would change the list. */
    fun canMoveUp(list: List<Category>, id: String): Boolean = id != ALL && list.indexOfFirst { it.id == id } >= 2

    /** True when [moveDown] would change the list. */
    fun canMoveDown(list: List<Category>, id: String): Boolean {
        val i = list.indexOfFirst { it.id == id }
        return id != ALL && i >= 0 && i < list.size - 1
    }

    /**
     * App count per category id. Each app is a pair of (user override or null, auto category id).
     * Uses the same [resolve] rule as the home screen. "all" is not in the result.
     */
    fun countApps(categories: List<Category>, apps: List<Pair<String?, String>>): Map<String, Int> {
        val out = HashMap<String, Int>()
        for ((override, auto) in apps) out.merge(resolve(override, auto, categories), 1, Int::plus)
        return out
    }

    /** Detail text of a category row in settings. */
    fun valueText(id: String, count: Int): String = when {
        id == ALL -> "Always first"
        count <= 0 -> "Empty · hidden"
        count == 1 -> "1 app"
        else -> "$count apps"
    }

    /** "all" plus every category that has apps, in list order. */
    fun visibleSegments(categories: List<Category>, counts: Map<String, Int>): List<Category> =
        categories.filter { it.id == ALL || (counts[it.id] ?: 0) > 0 }

    /** Maps a v1 int override to a category id. 0 and unknown values give null. */
    fun migrateV1(oldInt: Int): String? = when (oldInt) {
        V1_STREAMING -> STREAMING
        V1_SYSTEM -> SYSTEM
        else -> null
    }
}
