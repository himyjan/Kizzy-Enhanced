package com.my.kizzy.data

import com.my.kizzy.data.rpc.ActiveGameTracker
import com.my.kizzy.data.rpc.ConsoleAppRegistry
import com.my.kizzy.data.rpc.ConsoleGameCatalog
import com.my.kizzy.data.rpc.ConsoleNotificationParser
import com.my.kizzy.data.rpc.ConsolePlatform
import com.my.kizzy.domain.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleAppsTest {

    @Test
    fun `standard builds are always resolved`() {
        val droidDeck = ConsoleAppRegistry.resolve("com.droiddeck.launcher", includeSpoofed = false)
        assertEquals(ConsolePlatform.DROIDDECK, droidDeck?.platform)
        assertFalse(droidDeck!!.spoofed)

        val eden = ConsoleAppRegistry.resolve("dev.eden.eden_nightly", includeSpoofed = false)
        assertEquals(ConsolePlatform.EDEN, eden?.platform)

        assertEquals(
            ConsolePlatform.EDEN,
            ConsoleAppRegistry.resolve("org.ruzu.ruzu_emu", includeSpoofed = false)?.platform
        )
    }

    @Test
    fun `spoofed builds are only resolved when enabled`() {
        assertNull(ConsoleAppRegistry.resolve("com.tencent.ig", includeSpoofed = false))
        val spoofed = ConsoleAppRegistry.resolve("com.tencent.ig", includeSpoofed = true)
        assertEquals(ConsolePlatform.DROIDDECK, spoofed?.platform)
        assertTrue(spoofed!!.spoofed)

        assertEquals(
            ConsolePlatform.EDEN,
            ConsoleAppRegistry.resolve("com.miHoYo.Yuanshen", includeSpoofed = true)?.platform
        )
    }

    @Test
    fun `unknown package is not a console`() {
        assertNull(ConsoleAppRegistry.resolve("com.spotify.music", includeSpoofed = true))
        assertNull(ConsoleAppRegistry.resolve(null, includeSpoofed = true))
    }

    @Test
    fun `notification parser extracts the game title`() {
        assertEquals(
            "Hollow Knight",
            ConsoleNotificationParser.pickTitle(
                title = "Hollow Knight",
                text = "DroidDeck",
                bigText = null,
                subText = null
            )
        )
        assertEquals(
            "Hollow Knight",
            ConsoleNotificationParser.pickTitle(
                title = "DroidDeck",
                text = "Playing Hollow Knight",
                bigText = null,
                subText = null
            )
        )
        assertEquals(
            "Super Mario Odyssey",
            ConsoleNotificationParser.pickTitle(
                title = "Eden",
                text = null,
                bigText = "Now playing: Super Mario Odyssey",
                subText = null
            )
        )
    }

    @Test
    fun `notification parser ignores launcher-only notifications`() {
        assertNull(
            ConsoleNotificationParser.pickTitle(
                title = "DroidDeck",
                text = "Running",
                bigText = null,
                subText = null
            )
        )
        assertNull(
            ConsoleNotificationParser.pickTitle(
                title = "Eden Emulator",
                text = "Tap to return to the game",
                bigText = null,
                subText = null
            )
        )
    }

    @Test
    fun `tracker is scoped to the console package`() {
        ActiveGameTracker.clear()
        ActiveGameTracker.update("dev.eden.eden_emulator", "Metroid Dread")
        assertEquals(
            "Metroid Dread",
            ActiveGameTracker.titleFor("dev.eden.eden_emulator")
        )
        assertNull(ActiveGameTracker.titleFor("com.droiddeck.launcher"))
        ActiveGameTracker.clearIfPackage("dev.eden.eden_emulator")
        assertNull(ActiveGameTracker.titleFor("dev.eden.eden_emulator"))
    }

    @Test
    fun `catalog matches a shortened notification title`() {
        ConsoleGameCatalog.update(
            listOf(
                Game(
                    platform = "nintendo",
                    small_image = "switch.png",
                    large_image = "hollow.png",
                    game_title = "Hollow Knight: Voidheart Edition"
                )
            )
        )
        assertEquals(
            "Hollow Knight: Voidheart Edition",
            ConsoleGameCatalog.match("Hollow Knight")?.game_title
        )
    }
}
