package io.github.kobiregev.volt

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiTextTest {
    @Test fun bandBoundary() {
        assertEquals("2.4 GHz", WifiText.band(2412))
        assertEquals("2.4 GHz", WifiText.band(4900))
        assertEquals("5 GHz", WifiText.band(4901))
        assertEquals("5 GHz", WifiText.band(5180))
        assertEquals("2.4 GHz", WifiText.band(0))
    }

    @Test fun qualityBoundaries() {
        assertEquals("strong", WifiText.quality(-30))
        assertEquals("good", WifiText.quality(-60))
        assertEquals("good", WifiText.quality(-69))
        assertEquals("weak", WifiText.quality(-70))
        assertEquals("weak", WifiText.quality(-95))
    }

    @Test fun describeCombines() {
        assertEquals("5 GHz · strong", WifiText.describe(5200, -50))
        assertEquals("2.4 GHz · weak", WifiText.describe(2437, -80))
    }
}
