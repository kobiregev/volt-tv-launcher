package io.github.kobiregev.volt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoriesTest {
    private val d = Categories.DEFAULTS

    private fun ids(l: List<Category>) = l.map { it.id }

    @Test fun defaultsOrder() {
        assertEquals(listOf("all", "streaming", "music", "games", "tools", "system"), ids(d))
        assertEquals(listOf("All", "Streaming", "Music", "Games", "Tools", "System"), d.map { it.name })
    }

    @Test fun roundTrip() {
        val l = Categories.add(d, "Kids")
        assertEquals(l, Categories.parse(Categories.serialize(l)))
    }

    @Test fun parseBadInputGivesDefaults() {
        assertEquals(d, Categories.parse(null))
        assertEquals(d, Categories.parse(""))
        assertEquals(d, Categories.parse("   "))
        assertEquals(d, Categories.parse("garbage without tab\n\t\nx\t"))
    }

    @Test fun parseAddsAllFirst() {
        assertEquals(listOf("all", "music"), ids(Categories.parse("music\tMusic")))
    }

    @Test fun parseMovesAllToFrontAndFixesName() {
        val l = Categories.parse("music\tMusic\nall\tEverything")
        assertEquals(listOf("all", "music"), ids(l))
        assertEquals("All", l[0].name)
    }

    @Test fun parseDropsDuplicatesAndBadNames() {
        val l = Categories.parse("all\tAll\na\tA\na\tB\nb\t\n c d\tX")
        assertEquals(listOf("all", "a"), ids(l))
        assertEquals("A", l[1].name)
    }

    @Test fun sanitize() {
        assertEquals("a b", Categories.sanitize("  a\tb\n "))
        assertEquals("", Categories.sanitize(" \t\n"))
        assertEquals(24, Categories.sanitize("x".repeat(40)).length)
        assertEquals("x".repeat(23), Categories.sanitize("x".repeat(23) + " yyyy"))
    }

    @Test fun addUsesNextFreeId() {
        var l = Categories.add(d, "A")
        l = Categories.add(l, "B")
        assertEquals(listOf("u1", "u2"), ids(l).takeLast(2))
        l = Categories.delete(l, "u1")
        assertEquals("u1", Categories.add(l, "C").last().id)
    }

    @Test fun addEmptyNameChangesNothing() = assertEquals(d, Categories.add(d, "  \t "))

    @Test fun renameAndAllProtected() {
        assertEquals("Films", Categories.rename(d, "streaming", " Films ")[1].name)
        assertEquals(d, Categories.rename(d, "all", "X"))
        assertEquals(d, Categories.rename(d, "music", " "))
        assertEquals(d, Categories.rename(d, "nope", "X"))
    }

    @Test fun deleteKeepsAll() {
        assertEquals(d, Categories.delete(d, "all"))
        assertEquals(listOf("all", "streaming", "music", "games", "system"), ids(Categories.delete(d, "tools")))
    }

    @Test fun moveUpAndDown() {
        assertEquals(listOf("all", "music", "streaming", "games", "tools", "system"), ids(Categories.moveUp(d, "music")))
        assertEquals(listOf("all", "streaming", "games", "music", "tools", "system"), ids(Categories.moveDown(d, "music")))
    }

    @Test fun moveNeverPassesAllOrEnds() {
        assertEquals(d, Categories.moveUp(d, "streaming"))
        assertEquals(d, Categories.moveUp(d, "all"))
        assertEquals(d, Categories.moveDown(d, "all"))
        assertEquals(d, Categories.moveDown(d, "system"))
        assertEquals(d, Categories.moveUp(d, "nope"))
        assertEquals(d, Categories.moveDown(d, "nope"))
    }

    @Test fun resolveOverrideWins() = assertEquals("games", Categories.resolve("games", "streaming", d))

    @Test fun resolveIgnoresMissingOrAllOverride() {
        assertEquals("streaming", Categories.resolve("gone", "streaming", d))
        assertEquals("tools", Categories.resolve("all", "tools", d))
        assertEquals("tools", Categories.resolve(null, "tools", d))
    }

    @Test fun resolveFallbacks() {
        val noTools = Categories.delete(d, "tools")
        assertEquals("streaming", Categories.resolve(null, "tools", noTools))
        val noStreaming = Categories.delete(noTools, "streaming")
        assertEquals("music", Categories.resolve(null, "tools", noStreaming))
        assertEquals("all", Categories.resolve(null, "tools", listOf(d[0])))
    }

    @Test fun visibleSegmentsHideEmpty() {
        val v = Categories.visibleSegments(d, mapOf("streaming" to 3, "tools" to 1, "music" to 0))
        assertEquals(listOf("all", "streaming", "tools"), ids(v))
        assertEquals(listOf("all"), ids(Categories.visibleSegments(d, emptyMap())))
    }

    @Test fun migrateV1() {
        assertEquals("streaming", Categories.migrateV1(1))
        assertEquals("system", Categories.migrateV1(2))
        assertNull(Categories.migrateV1(0))
        assertNull(Categories.migrateV1(9))
    }

    @Test fun nameOfUnknownIsAll() = assertEquals("All", Categories.nameOf(d, "zzz"))
}
