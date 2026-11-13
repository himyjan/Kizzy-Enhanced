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

    @Test
    fun baselineProfile() = baselineProfileRule.collect(
        packageName = "com.my.kizzy.enhanced",
        profileId = "BaselineProfile",
        mode = BaselineProfileMode.XCompilation,
        samplingMode = SamplingMode.Sampling,
        repeatMode = RepeatMode.RepeatMultiple,
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.pressHome()
            device.waitForIdle()
        },
        blocks = listOf(
            // Block 1: Cold start - launch app and wait for home screen
            {
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                device.waitForIdle()
                // Wait for the home screen "Features" heading to appear
                device.wait(Until.hasObject(By.text("Features")), 15_000)
            },
            // Block 2: Open Media RPC screen (triggers media RPC service + presence composition)
            {
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                val mediaRpcCard = device.findObject(By.text("Media RPC"))
                if (mediaRpcCard != null) {
                    mediaRpcCard.click()
                    device.waitForIdle()
                    device.wait(Until.hasObject(By.text("Enable Media RPC")), 10_000)
                }
            },
            // Block 3: Open Console RPC screen (game catalog + auto-detect UI)
            {
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                device.pressHome()
                device.waitForIdle()
                val consoleRpcCard = device.findObject(By.text("Console RPC"))
                if (consoleRpcCard != null) {
                    consoleRpcCard.click()
                    device.waitForIdle()
                    device.wait(Until.hasObject(By.text("Console Auto-Detect")), 10_000)
                }
            },
            // Block 4: Open Profile screen (RPC preview + user profile composition)
            {
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                device.pressHome()
                device.waitForIdle()
                val profileButton = device.findObject(By.desc("Person"))
                if (profileButton != null) {
                    profileButton.click()
                    device.waitForIdle()
                    device.wait(Until.hasObject(By.text("RPC Preview")), 10_000)
                }
            },
            // Block 5: Open Settings screen (preference composition)
            {
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                device.pressHome()
                device.waitForIdle()
                val settingsCard = device.findObject(By.text("Settings"))
                if (settingsCard != null) {
                    settingsCard.click()
                    device.waitForIdle()
                }
            }
        ),
        // Collect profile for up to 60 seconds per block
        blockDurationMs = TimeUnit.SECONDS.toMillis(60)
    )
}
