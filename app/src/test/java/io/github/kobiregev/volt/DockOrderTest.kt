package io.github.kobiregev.volt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DockOrderTest {
    private val apps = listOf("c.pkg" to "Charlie", "a.pkg" to "alpha", "b.pkg" to "Bravo")

    private fun pkgs(l: List<Pair<String, String>>) = l.map { it.first }

    @Test fun noSavedOrderSortsByLabelIgnoringCase() =
        assertEquals(listOf("a.pkg", "b.pkg", "c.pkg"), pkgs(DockOrder.sortVisible(apps, emptyList(), emptySet())))

    @Test fun savedOrderWins() =
        assertEquals(listOf("c.pkg", "b.pkg", "a.pkg"), pkgs(DockOrder.sortVisible(apps, listOf("c.pkg", "b.pkg", "a.pkg"), emptySet())))

    @Test fun unknownAppsGoLastByLabel() =
        assertEquals(listOf("c.pkg", "a.pkg", "b.pkg"), pkgs(DockOrder.sortVisible(apps, listOf("c.pkg"), emptySet())))

    @Test fun hiddenAreRemoved() = assertEquals(listOf("a.pkg", "c.pkg"), pkgs(DockOrder.sortVisible(apps, emptyList(), setOf("b.pkg"))))

    @Test fun hiddenAndOrderInterplay() =
        assertEquals(listOf("b.pkg", "a.pkg"), pkgs(DockOrder.sortVisible(apps, listOf("b.pkg", "c.pkg", "a.pkg"), setOf("c.pkg"))))

    @Test fun staleSavedPackagesIgnored() =
        assertEquals(listOf("b.pkg", "a.pkg", "c.pkg"), pkgs(DockOrder.sortVisible(apps, listOf("gone", "b.pkg"), emptySet())))

    @Test fun emptyInputGivesEmptyResult() =
        assertEquals(emptyList<Pair<String, String>>(), DockOrder.sortVisible(emptyList(), listOf("x"), setOf("y")))

    @Test fun allHidden() = assertEquals(0, DockOrder.sortVisible(apps, emptyList(), apps.map { it.first }.toSet()).size)

    @Test fun moveRight() = assertEquals(
        listOf("b", "a", "c"),
        DockOrder.move(listOf("a", "b", "c"), listOf("a", "b", "c"), "a", 1, emptyList())
    )

    @Test fun moveLeft() = assertEquals(
        listOf("a", "c", "b"),
        DockOrder.move(listOf("a", "b", "c"), listOf("a", "b", "c"), "c", -1, emptyList())
    )

    @Test fun moveAtLeftEdgeIsNoop() = assertNull(DockOrder.move(listOf("a", "b"), listOf("a", "b"), "a", -1, emptyList()))

    @Test fun moveAtRightEdgeIsNoop() = assertNull(DockOrder.move(listOf("a", "b"), listOf("a", "b"), "b", 1, emptyList()))

    @Test fun moveUnknownIsNoop() = assertNull(DockOrder.move(listOf("a", "b"), listOf("a", "b"), "z", 1, emptyList()))

    @Test fun moveInEmptyIsNoop() = assertNull(DockOrder.move(emptyList(), emptyList(), "a", 1, emptyList()))

    @Test fun moveWithinCategoryFilterSwapsWithShownNeighbour() =
        assertEquals(listOf("c", "b", "a", "d"), DockOrder.move(listOf("a", "b", "c", "d"), listOf("a", "c"), "a", 1, emptyList()))

    @Test fun hiddenSavedPackagesAreAppendedAfterMove() =
        assertEquals(listOf("b", "a", "h"), DockOrder.move(listOf("a", "b"), listOf("a", "b"), "a", 1, listOf("h", "a")))
}
