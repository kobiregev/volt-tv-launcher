package io.github.kobiregev.volt

import org.junit.Assert.assertEquals
import org.junit.Test

class CategorizerTest {
    private val streaming = Categories.STREAMING
    private val system = Categories.SYSTEM
    private val tools = Categories.TOOLS

    private fun cat(pkg: String, sys: Boolean = false, c: Int = -1) = Categorizer.defaultCategory(pkg, sys, c)

    @Test fun userAppIsStreaming() = assertEquals(streaming, cat("com.example.app"))

    @Test fun knownSystemPackages() {
        assertEquals(system, cat("com.android.tv.settings"))
        assertEquals(system, cat("com.android.settings"))
        assertEquals(system, cat("com.android.vending"))
    }

    @Test fun fileManagersAreTools() {
        assertEquals(tools, cat("com.android.documentsui.files"))
        assertEquals(tools, cat("com.mi.file"))
        assertEquals(tools, cat("com.example.filemanager"))
        assertEquals(tools, cat("com.example.FileExplorer"))
        assertEquals(tools, cat("com.example.filebrowser"))
        assertEquals(tools, cat("com.example.filecommander"))
        assertEquals(tools, cat("files"))
    }

    @Test fun profilePackageIsNotTools() {
        assertEquals(streaming, cat("com.example.profile"))
        assertEquals(streaming, cat("com.example.myprofiles"))
        assertEquals(streaming, cat("com.example.profile.app"))
    }

    @Test fun systemAppWithoutMediaHintIsSystem() = assertEquals(system, cat("com.mi.tv.update", sys = true))

    @Test fun systemFileManagerStaysSystem() = assertEquals(system, cat("com.android.documentsui.files", sys = true))

    @Test fun systemAppWithMediaCategoryIsMediaCategory() {
        assertEquals(streaming, cat("com.mi.player", sys = true, c = Categorizer.CATEGORY_VIDEO))
        assertEquals(Categories.MUSIC, cat("com.mi.music", sys = true, c = Categorizer.CATEGORY_AUDIO))
    }

    @Test fun systemAppWithMediaNameIsStreaming() {
        assertEquals(streaming, cat("com.google.android.youtube.tv", sys = true))
        assertEquals(streaming, cat("com.netflix.ninja", sys = true))
        assertEquals(streaming, cat("com.spotify.tv.android", sys = true))
    }

    @Test fun otherAppCategoryDoesNotMakeSystemAppMedia() = assertEquals(system, cat("com.mi.x", sys = true, c = 4))

    @Test fun systemAppWithGameCategoryIsSystem() = assertEquals(system, cat("com.mi.game", sys = true, c = Categorizer.CATEGORY_GAME))

    @Test fun systemPackageWinsOverMediaHint() = assertEquals(system, cat("com.android.vending", sys = true, c = 2))

    @Test fun emptyPackageIsStreaming() = assertEquals(streaming, cat(""))

    @Test fun gameCategoryIsGames() = assertEquals(Categories.GAMES, cat("com.example.fun", c = Categorizer.CATEGORY_GAME))

    @Test fun audioCategoryIsMusic() = assertEquals(Categories.MUSIC, cat("com.example.radio", c = Categorizer.CATEGORY_AUDIO))

    @Test fun videoCategoryIsStreaming() = assertEquals(streaming, cat("com.example.clips", c = Categorizer.CATEGORY_VIDEO))

    @Test fun appCategoryWinsOverToolsName() = assertEquals(Categories.GAMES, cat("com.example.vpngame", c = Categorizer.CATEGORY_GAME))

    @Test fun toolPackages() {
        assertEquals(tools, cat("com.android.chrome"))
        assertEquals(tools, cat("org.mozilla.firefox"))
        assertEquals(tools, cat("com.example.tvbrowser"))
        assertEquals(tools, cat("com.nordvpn.android"))
        assertEquals(tools, cat("org.zwanoo.android.speedtest"))
        assertEquals(tools, cat("com.esaba.downloader"))
        assertEquals(tools, cat("com.termux"))
        assertEquals(tools, cat("com.example.adblink"))
        assertEquals(tools, cat("com.google.android.apps.tv.remote"))
    }

    @Test fun mediaPackageIsStreaming() {
        assertEquals(streaming, cat("com.netflix.ninja"))
        assertEquals(streaming, cat("com.amazon.amazonvideo.livingroom"))
    }
}
