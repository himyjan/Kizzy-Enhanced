package com.my.kizzy.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a Baseline Profile for Kizzy's critical startup path.
 *
 * Captures:
 *   - Cold app launch (MainActivity, Hilt DI, KizzyTheme)
 *   - DiscordWebSocketImpl initialization and gateway connection
 *   - KizzyRPC / MediaRpcService / ExperimentalRpc service startup
 *   - ProfileInstaller baseline profile installation
 *
 * Run on a rooted physical device via:
 *   ./gradlew :app:generateBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    private val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val packageName = "com.my.kizzy.enhanced"

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = packageName,
        maxIterations = 5,
        stableIterations = 3
    ) {
        // Cold start: force-stop, then launch the app from a clean state
        device.executeShellCommand("am force-stop $packageName")
        device.waitForIdle()

        // Launch the app (cold start) — captures MainActivity, Hilt, KizzyTheme,
        // DiscordWebSocketImpl, KizzyRPC, and all RPC service initialization
        device.executeShellCommand("am start -W -n ${packageName}/com.my.kizzy.MainActivity")
        device.waitForIdle()

        // Dismiss any startup permission dialogs that might block the critical path
        dismissStartupDialogs()

        // Wait for background services (DiscordWebSocketImpl, RPC services) to initialize
        device.waitForIdle()
        Thread.sleep(3000L)
        device.waitForIdle()
    }

    /** Dismiss startup/setup dialogs that may appear during the benchmark run. */
    private fun dismissStartupDialogs() {
        // Try to click common dialog buttons to clear permission/startup dialogs
        val labels = listOf("Allow", "OK", "Got it", "Continue", "Skip")
        for (label in labels) {
            val btn = device.findObject(By.text(label))
            if (btn != null && btn.isClickable) {
                btn.click()
                device.waitForIdle()
            }
        }
    }
}
