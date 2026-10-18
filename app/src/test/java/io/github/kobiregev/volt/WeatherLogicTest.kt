package io.github.kobiregev.volt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherLogicTest {
    @Test fun geocodeUrlEncodesAndTrims() {
        assertEquals(
            "https://geocoding-api.open-meteo.com/v1/search?name=New+York&count=5&language=en&format=json",
            WeatherLogic.geocodeUrl("  New York ")
        )
        assertTrue(WeatherLogic.geocodeUrl("Zürich&x=1")!!.contains("name=Z%C3%BCrich%26x%3D1&"))
    }

    @Test fun geocodeUrlNeedsTwoChars() {
        assertNull(WeatherLogic.geocodeUrl(""))
        assertNull(WeatherLogic.geocodeUrl(" a "))
        assertNotNull(WeatherLogic.geocodeUrl("ab"))
    }

    @Test fun forecastUrlUnit() {
        assertFalse(WeatherLogic.forecastUrl("1.00", "2.00", "c").contains("temperature_unit"))
        assertTrue(WeatherLogic.forecastUrl("1.00", "2.00", "f").endsWith("&temperature_unit=fahrenheit"))
        assertTrue(WeatherLogic.forecastUrl("1.00", "2.00", "x").contains("latitude=1.00&longitude=2.00"))
    }

    @Test fun resultLine() {
        assertEquals("Bavaria, Germany", WeatherLogic.resultLine("Bavaria", "Germany"))
        assertEquals("Germany", WeatherLogic.resultLine(null, "Germany"))
        assertEquals("Bavaria", WeatherLogic.resultLine("Bavaria", " "))
        assertEquals("", WeatherLogic.resultLine(null, null))
    }

    @Test fun location() {
        assertEquals(WeatherLoc("Berlin", "52.52", "13.41"), WeatherLogic.location(" Berlin ", "52.5200", "13.405"))
        assertEquals("Your city", WeatherLogic.location("", "1", "2")!!.city)
        assertNull(WeatherLogic.location("X", "", "2"))
        assertNull(WeatherLogic.location("X", "abc", "2"))
        assertNull(WeatherLogic.location("X", "91", "2"))
        assertNull(WeatherLogic.location("X", "1", "181"))
        assertNull(WeatherLogic.location("X", null, null))
    }

    @Test fun coordUsesDotAnd2Decimals() = assertEquals("-33.87", WeatherLogic.coord(-33.8688))

    @Test fun unit() {
        assertEquals("c", WeatherLogic.normalizeUnit(null))
        assertEquals("f", WeatherLogic.nextUnit("c"))
        assertEquals("c", WeatherLogic.nextUnit("f"))
        assertEquals("°F", WeatherLogic.unitLabel("f"))
        assertEquals("°C", WeatherLogic.unitLabel("c"))
    }

    @Test fun interval() {
        assertEquals(30, WeatherLogic.normalizeInterval(7))
        assertEquals(30, WeatherLogic.nextInterval(15))
        assertEquals(60, WeatherLogic.nextInterval(30))
        assertEquals(15, WeatherLogic.nextInterval(60))
        assertEquals("15 min", WeatherLogic.intervalLabel(15))
        assertEquals("30 min", WeatherLogic.intervalLabel(30))
        assertEquals("1 hour", WeatherLogic.intervalLabel(60))
    }

    @Test fun keyChangesWithUnitAndPlace() {
        val a = WeatherLoc("A", "1.00", "2.00")
        assertFalse(WeatherLogic.key(a, "c") == WeatherLogic.key(a, "f"))
        assertFalse(WeatherLogic.key(a, "c") == WeatherLogic.key(WeatherLoc("A", "1.00", "3.00"), "c"))
    }
}
