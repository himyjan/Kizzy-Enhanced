package com.my.kizzy.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile shipped with the app.
 *
 * A baseline profile lists the hot methods Android should AOT-compile at
 * install time. Kizzy starts several heavy things on launch (Hilt graph,
 * DataStore priming, Compose first composition, OkHttp/Discord gateway), so
 * without a profile that work is JIT-compiled on the critical path of every
 * cold start after the first few.
 *
 * This test module is a `com.android.test` module: its sources live in
 * `src/main/java`, NOT `src/androidTest/java`. The generator previously sat in
 * `src/androidTest`, which is not part of any source set, so it was never
 * compiled or executed and no profile was ever produced.
 *
 * Run with:
 *   ./gradlew :app:generateReleaseBaselineProfile
 *
 * or directly:
 *   ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
 *
 * The resulting profile is written to
 * app/src/release/generated/baselineProfiles/baseline-prof.txt
 * and is consumed by `androidx.profileinstaller` at runtime.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun baselineProfile() = baselineProfileRule.collect(
        packageName = TARGET_PACKAGE,
    ) {
        // 1. Cold start up to the home screen. This covers Application.onCreate,
        //    Hilt graph creation, DataStore priming and the first Compose
        //    composition of the home screen.
        startActivityAndWait()
        device.waitForIdle()

        // 2. Each RPC screen. Opening and leaving a screen forces the Compose
        //    runtime, navigation and the screen's ViewModel path to run, and
        //    those are exactly the paths hit when a user switches RPC on.
        openCardAndReturn("Media RPC")
        openCardAndReturn("Manual RPC")
        openCardAndReturn("Custom RPC")

        // 3. The navigation drawer and the settings it opens. The drawer is
        //    built on every home recomposition, so having it compiled matters
        //    for scroll/jank as well as startup.
        openDrawerEntryAndReturn("RPC Settings")
        openDrawerEntryAndReturn("Logs")

        // 4. Return home so the profile ends on the screen the app really
        //    resumes to.
        device.pressBack()
        device.waitForIdle()
        device.wait(Until.hasObject(By.text(FEATURES_HEADER)), 10_000)
    }

    private fun openCardAndReturn(title: String) {
        val card = device.findObject(By.text(title)) ?: return
        card.click()
        device.waitForIdle()
        // Let the destination compose and run its first frame.
        device.waitForIdle(2_000)
        device.pressBack()
        device.waitForIdle()
        device.wait(Until.hasObject(By.text(FEATURES_HEADER)), 5_000)
    }

    private fun openDrawerEntryAndReturn(label: String) {
        val menu = device.findObject(By.desc(MENU_CONTENT_DESC)) ?: return
        menu.click()
        device.waitForIdle()
        val entry = device.findObject(By.text(label))
        if (entry != null) {
            entry.click()
            device.waitForIdle()
            device.waitForIdle(2_000)
            device.pressBack()
            device.waitForIdle()
        } else {
            // Close the drawer again so the next flow starts from home.
            device.pressBack()
            device.waitForIdle()
        }
        device.wait(Until.hasObject(By.text(FEATURES_HEADER)), 5_000)
    }

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private companion object {
        const val TARGET_PACKAGE = "com.my.kizzy.enhanced"
        const val FEATURES_HEADER = "Features"
        const val MENU_CONTENT_DESC = "Outlined.Menu"
    }
}
