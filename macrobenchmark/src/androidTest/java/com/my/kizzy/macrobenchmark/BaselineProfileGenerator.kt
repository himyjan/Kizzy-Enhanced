package com.my.kizzy.macrobenchmark

import android.content.Intent
import androidx.benchmark.junit.macro.BaselineProfileMode
import androidx.benchmark.junit.macro.RepeatMode
import androidx.benchmark.junit.macro.SamplingMode
import androidx.benchmark.junit.macro.StartupMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Generates baseline profiles for key user flows in Kizzy-Enhanced.
 *
 * Baseline profiles pre-compile code paths that users hit on startup and
 * common navigation, reducing JIT compilation during startup and while
 * interacting with the app. This improves launch time and reduces CPU
 * time (and thus battery) spent on compilation.
 *
 * Run with: ./gradlew :macrobenchmark:connectedAndroidTest
 *
 * The test runs each user flow under three startup modes:
 *   COLD   — app process is dead (fresh install scenario)
 *   WARM   — app is in background, activity is recreated
 *   HOT    — app is already in foreground, activity resumes immediately
 *
 * Blocks are executed in order. Each block runs against a fresh UI state
 * where possible so the profile covers distinct code paths:
 *
 *   1. Cold start — app launch to home screen
 *   2. Media RPC — media service binding + presence composition
 *   3. Console RPC — game catalog + auto-detect UI
 *   4. Custom RPC — template editing + presence configuration
 *   5. Experimental RPC — apps detection + media detection
 *   6. Profile screen — Discord OAuth2 + RPC preview
 *   7. Logs screen — log viewing + search
 *   8. Settings — RPC preferences + cover art config
 *   9. App Selection — enabling/disabling apps for RPC
 *   10. Console Preset Editor — creating/editing console presets
 *   11. Last.fm Scrobbling — API credentials + enable scrobbling
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = androidx.benchmark.junit.macro.BaselineProfileRule()

    /** User flows exercised during baseline profile generation. */
    private val flowBlocks = listOf(
        // Block 1: Cold start - launch app and wait for home screen
        { startupBlock() },
        // Block 2: Media RPC - triggers media service + presence composition
        { openScreen("Media RPC", "Enable Media RPC") },
        // Block 3: Console RPC - game catalog + auto-detect UI
        { openScreen("Console RPC", "Console Auto-Detect") },
        // Block 4: Custom RPC - template editing + presence configuration
        { openScreen("Custom RPC", null) },
        // Block 5: Experimental RPC - apps detection + media detection
        { openScreen("Experimental RPC", null) },
        // Block 6: Profile - Discord OAuth2 + RPC preview
        { openProfile() },
        // Block 7: Logs screen - log viewing + search
        { openScreen("Logs", null) },
        // Block 8: Settings - RPC preferences + cover art config
        { openSettings() },
        // Block 9: App Selection - enabling/disabling apps for RPC
        { openAppSelection() },
        // Block 10: Console Preset Editor - creating/editing console presets
        { openConsolePresetEditor() },
        // Block 11: Last.fm Scrobbling - API credentials + enable scrobbling
        { openLastFmSettings() }
    )

    @Test
    fun baselineProfileCold() = runProfile(StartupMode.COLD)

    @Test
    fun baselineProfileWarm() = runProfile(StartupMode.WARM)

    @Test
    fun baselineProfileHot() = runProfile(StartupMode.HOT)

    private fun runProfile(startupMode: StartupMode) = baselineProfileRule.collect(
        packageName = "com.my.kizzy.enhanced",
        profileId = "BaselineProfile_${startupMode.name.lowercase()}",
        mode = BaselineProfileMode.XCompilation,
        samplingMode = SamplingMode.Sampling,
        repeatMode = RepeatMode.RepeatMultiple,
        iterations = 5,
        startupMode = startupMode,
        setupBlock = {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.pressHome()
            device.waitForIdle()
        },
        blocks = flowBlocks,
        blockDurationMs = TimeUnit.SECONDS.toMillis(60)
    )

    private fun startupBlock() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        device.waitForIdle()
        // Wait for the home screen "Features" heading to appear
        device.wait(Until.hasObject(By.text("Features")), 15_000)
    }

    private fun openScreen(title: String, waitForText: String?) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val card = device.findObject(By.text(title))
        if (card != null) {
            card.click()
            device.waitForIdle()
            if (waitForText != null) {
                device.wait(Until.hasObject(By.text(waitForText)), 10_000)
            }
        }
    }

    private fun openScreenByDesc(desc: String, waitForText: String?) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val button = device.findObject(By.desc(desc))
        if (button != null) {
            button.click()
            device.waitForIdle()
            if (waitForText != null) {
                device.wait(Until.hasObject(By.text(waitForText)), 10_000)
            }
        }
    }

    /** Open Console RPC preset editor - covers preset creation/editing code paths */
    private fun openConsolePresetEditor() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // First open Console RPC
        openScreen("Console RPC", "Console Auto-Detect")
        device.waitForIdle()
        // Look for preset edit button or create new preset
        val editBtn = device.findObject(By.text("Edit"))
        if (editBtn != null) {
            editBtn.click()
            device.waitForIdle()
            device.wait(Until.hasObject(By.text("Preset name")), 10_000)
        }
    }

    /** Open profile screen - covers Discord OAuth2 + RPC preview */
    private fun openProfile() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val profileBtn = device.findObject(By.desc("Person"))
        if (profileBtn != null) {
            profileBtn.click()
            device.waitForIdle()
            device.wait(Until.hasObject(By.text("RPC Preview")), 10_000)
        }
    }

    /** Open settings - covers RPC preferences + cover art config */
    private fun openSettings() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val settingsBtn = device.findObject(By.text("Settings"))
        if (settingsBtn != null) {
            settingsBtn.click()
            device.waitForIdle()
            device.wait(Until.hasObject(By.text("RPC")), 10_000)
        }
    }

    /** Open app selection - covers enabling/disabling apps for RPC */
    private fun openAppSelection() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // Open Experimental RPC first, then navigate to apps
        openScreen("Experimental RPC", null)
        device.waitForIdle()
        // Look for apps list or app selection button
        val appsBtn = device.findObject(By.text("Apps"))
        if (appsBtn != null) {
            appsBtn.click()
            device.waitForIdle()
            device.wait(Until.hasObject(By.text("Turn on all apps")), 10_000)
        }
    }

    /** Open Last.fm scrobbling settings - covers API credentials + enable scrobbling */
    private fun openLastFmSettings() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // First open Settings
        openSettings()
        device.waitForIdle()
        // Look for Last.fm settings entry
        val lastFmBtn = device.findObject(By.text("Last.fm"))
        if (lastFmBtn != null) {
            lastFmBtn.click()
            device.waitForIdle()
            device.wait(Until.hasObject(By.text("Last.fm API Key")), 10_000)
        }
    }
}
