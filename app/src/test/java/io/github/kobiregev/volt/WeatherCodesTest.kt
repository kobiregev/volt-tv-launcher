package io.github.kobiregev.volt

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherCodesTest {
    @Test fun clear() = assertEquals("Clear", WeatherCodes.condition(0))

    @Test fun partly() {
        assertEquals("Partly cloudy", WeatherCodes.condition(1))
        assertEquals("Partly cloudy", WeatherCodes.condition(2))
    }

    @Test fun cloudy() = assertEquals("Cloudy", WeatherCodes.condition(3))

    @Test fun fog() {
        assertEquals("Fog", WeatherCodes.condition(45))
        assertEquals("Fog", WeatherCodes.condition(48))
    }

    @Test fun rain() {
        for (c in listOf(51, 55, 61, 65, 67, 80, 81, 82)) assertEquals("code $c", "Rain", WeatherCodes.condition(c))
    }

    @Test fun snow() {
        for (c in listOf(71, 73, 75, 77, 85, 86)) assertEquals("code $c", "Snow", WeatherCodes.condition(c))
    }

    @Test fun storm() {
        for (c in listOf(95, 96, 99)) assertEquals("code $c", "Storm", WeatherCodes.condition(c))
    }

    @Test fun unknownCodesFallBackToCloudy() {
        for (c in listOf(-1, 4, 50, 68, 78, 83, 84, 87, 97, 100, Int.MAX_VALUE)) {
            assertEquals("code $c", "Cloudy", WeatherCodes.condition(c))
        }
    }
}
