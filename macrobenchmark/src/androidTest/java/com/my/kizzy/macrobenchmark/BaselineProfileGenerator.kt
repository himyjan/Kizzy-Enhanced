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
 * Generates baseline profiles for key user flows.
 *
 * Baseline profiles pre-compile code paths that users hit on startup and
 * common navigation, reducing JIT compilation during startup and while
 * interacting with the app. This improves launch time and reduces CPU
 * time (and thus battery) spent on compilation.
 *
 * Run with: ./gradlew :macrobenchmark:generateBaselineProfile
 *
 * The test runs each user flow under three startup modes:
 *   COLD   — app process is dead (fresh install scenario)
 *   WARM   — app is in background, activity is recreated
 *   HOT    — app is already in foreground, activity resumes immediately
 *
 * Blocks are executed in order. Each block runs against a fresh UI state
 * where possible so the profile covers distinct code paths:
 *   1. Cold start (app launch to home screen)
 *   2. Open Media RPC screen (service binding + presence composition)
 *   3. Open Console RPC screen (game catalog + auto-detect UI)
 *   4. Open Profile screen (RPC preview + user profile composition)
 *   5. Open Settings screen (preference composition)
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = androidx.benchmark.junit.macro.BaselineProfileRule()

    /** User flows exercised during baseline profile generation. */
    private val flowBlocks = listOf(
        // Block 1: Cold start - launch app and wait for home screen
        { startupBlock() },
        // Block 2: Open Media RPC screen (triggers media RPC service + presence composition)
        { openScreen("Media RPC", "Enable Media RPC") },
        // Block 3: Open Console RPC screen (game catalog + auto-detect UI)
        { openScreen("Console RPC", "Console Auto-Detect") },
        // Block 4: Open Profile screen (RPC preview + user profile composition)
        { openScreenByDesc("Person", "RPC Preview") },
        // Block 5: Open Settings screen (preference composition)
        { openScreen("Settings", null) }
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
}
