package io.github.kobiregev.volt

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteKeymapTest {
    private val statements = File("src/main/res/raw/volt_remote.kcm").readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }

    @Test fun layoutIsAnOverlay() {
        assertEquals("type OVERLAY", statements.first())
    }

    @Test fun gridKeyMapsToHome() {
        assertTrue(statements.contains("map key 580 HOME"))
    }

    @Test fun manifestDeclaresReceiver() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("RemoteKeymapReceiver"))
        assertTrue(manifest.contains("android.hardware.input.action.QUERY_KEYBOARD_LAYOUTS"))
    }
}
