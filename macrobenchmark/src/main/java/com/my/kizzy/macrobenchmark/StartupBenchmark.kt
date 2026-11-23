package com.my.kizzy.macrobenchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup benchmarks for Kizzy.
 *
 * Measures cold / warm / hot start plus the cost of opening each RPC screen,
 * both with and without the baseline profile applied:
 *
 *   - startupCompilationBaselineProfile (CompilationMode.Partial + BaselineProfileMode.Require)
 *   - startupCompilationNone             (CompilationMode.None)
 *
 * The delta between the two compilation modes is the startup / screen-open win
 * the baseline profile buys.
 *
 * Run all:
 *   ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.my.kizzy.macrobenchmark.StartupBenchmark
 *
 * Compose composition tracing (opens in https://ui.perfetto.dev):
 *   -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.fullTracing.enable=true
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /* ── Cold / warm / hot start ────────────────────────────────────────── */

    @Test
    fun coldStartBaselineProfile() = startup(
        compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
        startupMode = StartupMode.COLD,
    )

    @Test
    fun coldStartNone() = startup(
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
    )

    @Test
    fun warmStartBaselineProfile() = startup(
        compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
    )

    @Test
    fun warmStartNone() = startup(
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.WARM,
    )

    @Test
    fun hotStartBaselineProfile() = startup(
        compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
        startupMode = StartupMode.HOT,
    )

    @Test
    fun hotStartNone() = startup(
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.HOT,
    )

    /* ── RPC screen open / close ────────────────────────────────────────── */

    @Test
    fun openMediaRpcBaselineProfile() = openScreen(
        title = "Media RPC",
        compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
    )

    @Test
    fun openMediaRpcNone() = openScreen(
        title = "Media RPC",
        compilationMode = CompilationMode.None(),
    )

    @Test
    fun openManualRpcBaselineProfile() = openScreen(
        title = "Manual RPC",
        compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
    )

    @Test
    fun openManualRpcNone() = openScreen(
        title = "Manual RPC",
        compilationMode = CompilationMode.None(),
    )

    @Test
    fun openCustomRpcBaselineProfile() = openScreen(
        title = "Custom RPC",
        compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
    )

    @Test
    fun openCustomRpcNone() = openScreen(
        title = "Custom RPC",
        compilationMode = CompilationMode.None(),
    )

    /* ── Implementation ─────────────────────────────────────────────────── */

    private fun startup(
        compilationMode: CompilationMode,
        startupMode: StartupMode,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        iterations = ITERATIONS,
        startupMode = startupMode,
        setupBlock = {
            // Force-stop so a cold start really starts from a dead process.
            // Warm/hot starts let the framework bring the app forward instead.
            if (startupMode == StartupMode.COLD) {
                device.executeShellCommand("am force-stop $TARGET_PACKAGE")
            }
            device.waitForIdle()
        },
    ) {
        startActivityAndWait()
    }

    private fun openScreen(title: String, compilationMode: CompilationMode) {
        // Make sure the app is already in the foreground so we measure the
        // screen open, not a full app launch.
        device.pressHome()
        device.waitForIdle()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text(FEATURES_HEADER)), 10_000)

        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            iterations = ITERATIONS,
            startupMode = StartupMode.HOT, // app already running
            setupBlock = {
                // Leave the app on the home screen before each iteration.
                startActivityAndWait()
                device.wait(Until.hasObject(By.text(FEATURES_HEADER)), 10_000)
            },
        ) {
            val card = device.findObject(By.text(title)) ?: return@measureRepeated
            card.click()
            device.waitForIdle()
            // Wait for the destination to finish its first composition.
            device.waitForIdle(2_000)
            // Return to home for a clean next iteration.
            device.pressBack()
            device.waitForIdle()
            device.wait(Until.hasObject(By.text(FEATURES_HEADER)), 5_000)
        }
    }

    private fun startActivityAndWait() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent()
            .setComponent(ComponentName(TARGET_PACKAGE, "com.my.kizzy.MainActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        ctx.startActivity(intent)
        device.waitForIdle()
    }

    private companion object {
        const val TARGET_PACKAGE = "com.my.kizzy.enhanced"
        const val FEATURES_HEADER = "Features"
        const val ITERATIONS = 5
    }
}
