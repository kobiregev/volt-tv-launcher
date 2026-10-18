package io.github.kobiregev.volt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetCitiesTest {
    private val all = PresetCities.ALL

    @Test fun enoughCities() = assertTrue(all.size >= 35)

    @Test fun namesAreUnique() = assertEquals(all.size, all.map { it.name }.toSet().size)

    @Test fun coordinatesAreValid() {
        for (c in all) {
            assertTrue(c.name, c.lat in -90.0..90.0)
            assertTrue(c.name, c.lon in -180.0..180.0)
            assertTrue(c.name, c.country.isNotBlank())
        }
    }

    @Test fun sortedByName() = assertEquals(all.map { it.name }.sorted(), all.map { it.name })

    @Test fun hasRegionSpread() {
        val names = all.map { it.name }.toSet()
        for (n in listOf("Tel Aviv", "Jerusalem", "London", "New York", "Tokyo", "Sydney", "Cape Town", "Sao Paulo")) {
            assertTrue(n, n in names)
        }
    }
}
