package io.github.kobiregev.volt

import org.junit.Assert.assertEquals
import org.junit.Test

class CategorizerTest {
    private fun cat(pkg: String, sys: Boolean = false, c: Int = -1) = Categorizer.defaultCategory(pkg, sys, c)

    @Test fun userAppIsStreaming() = assertEquals(CAT_STREAMING, cat("com.example.game"))

    @Test fun knownSystemPackages() {
        assertEquals(CAT_SYSTEM, cat("com.android.tv.settings"))
        assertEquals(CAT_SYSTEM, cat("com.android.settings"))
        assertEquals(CAT_SYSTEM, cat("com.android.vending"))
    }

    @Test fun fileManagersAreSystem() {
        assertEquals(CAT_SYSTEM, cat("com.android.documentsui.files"))
        assertEquals(CAT_SYSTEM, cat("com.mi.file"))
        assertEquals(CAT_SYSTEM, cat("com.example.filemanager"))
        assertEquals(CAT_SYSTEM, cat("com.example.FileExplorer"))
        assertEquals(CAT_SYSTEM, cat("com.example.filebrowser"))
        assertEquals(CAT_SYSTEM, cat("com.example.filecommander"))
        assertEquals(CAT_SYSTEM, cat("files"))
    }

    @Test fun profilePackageIsNotSystem() {
        assertEquals(CAT_STREAMING, cat("com.example.profile"))
        assertEquals(CAT_STREAMING, cat("com.example.myprofiles"))
        assertEquals(CAT_STREAMING, cat("com.example.profile.app"))
    }

    @Test fun systemAppWithoutMediaHintIsSystem() = assertEquals(CAT_SYSTEM, cat("com.mi.tv.update", sys = true))

    @Test fun systemAppWithMediaCategoryIsStreaming() {
        assertEquals(CAT_STREAMING, cat("com.mi.player", sys = true, c = Categorizer.CATEGORY_VIDEO))
        assertEquals(CAT_STREAMING, cat("com.mi.music", sys = true, c = Categorizer.CATEGORY_AUDIO))
    }

    @Test fun systemAppWithMediaNameIsStreaming() {
        assertEquals(CAT_STREAMING, cat("com.google.android.youtube.tv", sys = true))
        assertEquals(CAT_STREAMING, cat("com.netflix.ninja", sys = true))
        assertEquals(CAT_STREAMING, cat("com.spotify.tv.android", sys = true))
    }

    @Test fun otherAppCategoryDoesNotMakeSystemAppMedia() = assertEquals(CAT_SYSTEM, cat("com.mi.x", sys = true, c = 4))

    @Test fun systemPackageWinsOverMediaHint() = assertEquals(CAT_SYSTEM, cat("com.android.vending", sys = true, c = 2))

    @Test fun emptyPackageIsStreaming() = assertEquals(CAT_STREAMING, cat(""))
}
