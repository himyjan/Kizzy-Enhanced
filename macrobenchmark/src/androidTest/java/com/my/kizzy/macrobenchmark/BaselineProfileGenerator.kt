package com.my.kizzy.macrobenchmark

import androidx.benchmark.junit.macro.BaselineProfileMode
import androidx.benchmark.junit.macro.RepeatMode
import androidx.benchmark.junit.macro.SamplingMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates baseline profiles for key user flows.
 *
 * Baseline profiles pre-compile code paths that users hit on startup and
 * common navigation, reducing JIT compilation during startup and while
 * interacting with the app. This improves launch time and reduces CPU
 * time (and thus battery) spent on compilation.
 *
 * Run with: ./gradlew :macrobenchmark:generateBaselineProfile
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
        iterations = 3,
        setupBlock = {
            device.pressHome()
            device.waitForIdle()
        },
        blocks = listOf(
            // Block 1: Cold start - launch app and wait for home screen
            {
                val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .targetContext
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                device.waitForIdle()
                // Wait for the home screen to be ready
                device.wait(Until.hasObject(By.text("Kizzy")), 10_000)
            },
            // Block 2: Open Media RPC screen
            {
                val mediaRpcButton = device.findObject(By.res("com.my.kizzy.enhanced:id/mediaRpcButton"))
                if (mediaRpcButton != null) {
                    mediaRpcButton.click()
                    device.waitForIdle()
                }
            },
            // Block 3: Open Profile screen (triggers RPC preview composition)
            {
                val profileButton = device.findObject(By.res("com.my.kizzy.enhanced:id/profileButton"))
                if (profileButton != null) {
                    profileButton.click()
                    device.waitForIdle()
                }
            }
        )
    )
}
