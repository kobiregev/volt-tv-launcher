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

    @Test fun reorderMoveInAll() {
        val all = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), DockOrder.reorder(all, all, listOf("b", "c", "a", "d")))
    }

    @Test fun reorderInFilteredSubsetKeepsOtherSlots() = assertEquals(
        listOf("c", "b", "a", "d", "e"),
        DockOrder.reorder(listOf("a", "b", "c", "d", "e"), listOf("a", "c", "e"), listOf("c", "a", "e"))
    )

    @Test fun reorderSubsetMoveToLastKeepsHiddenByFilterApps() = assertEquals(
        listOf("b", "x", "c", "y", "a"),
        DockOrder.reorder(listOf("a", "x", "b", "y", "c"), listOf("a", "b", "c"), listOf("b", "c", "a"))
    )

    @Test fun reorderNoopKeepsOrder() {
        val all = listOf("a", "b", "c")
        assertEquals(all, DockOrder.reorder(all, listOf("a", "c"), listOf("a", "c")))
    }

    @Test fun reorderToFirstPlace() {
        val all = listOf("a", "b", "c")
        assertEquals(listOf("c", "a", "b"), DockOrder.reorder(all, all, listOf("c", "a", "b")))
    }

    @Test fun reorderToLastPlace() {
        val all = listOf("a", "b", "c")
        assertEquals(listOf("b", "c", "a"), DockOrder.reorder(all, all, listOf("b", "c", "a")))
    }

    @Test fun reorderEmptyIsEmpty() = assertEquals(emptyList<String>(), DockOrder.reorder(emptyList(), emptyList(), emptyList()))

    @Test fun reorderAppendsStaleSavedPackagesAndSortsBack() {
        val saved = listOf("gone", "a", "b")
        val order = DockOrder.reorder(listOf("a", "b"), listOf("a", "b"), listOf("b", "a"), saved)
        assertEquals(listOf("b", "a", "gone"), order)
        val apps = listOf("a" to "A", "b" to "B")
        assertEquals(listOf("b", "a"), DockOrder.sortVisible(apps, order, emptySet()).map { it.first })
    }
}
