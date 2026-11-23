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
            if (startupMode == StartupMode.COLD) {
                // Force-stop so a cold start really starts from a dead process.
                device.executeShellCommand("am force-stop $TARGET_PACKAGE")
            }
            device.waitForIdle()
        },
    ) {
        if (startupMode == StartupMode.HOT) {
            // HOT mode: app is already running; just bring the existing task
            // forward without restarting it.
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val intent = Intent()
                .setComponent(ComponentName(TARGET_PACKAGE, "com.my.kizzy.MainActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ctx.startActivity(intent)
        } else {
            startActivityAndWait()
        }
    }

    private fun openScreen(title: String, compilationMode: CompilationMode) {
        // Each iteration cold-launches MainActivity, taps the card, and measures
        // startup to that screen. RPC screens are Compose composables inside
        // MainActivity (no separate activity), so a cold start + tap is the only
        // way to get StartupTimingMetric slices (activityResume / doFrame).
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            iterations = ITERATIONS,
            startupMode = StartupMode.COLD,
            setupBlock = {
                device.executeShellCommand("am force-stop $TARGET_PACKAGE")
                device.waitForIdle()
            },
        ) {
            startActivityAndWait()
            device.wait(Until.hasObject(By.text(FEATURES_HEADER)), 10_000)
            val card = device.findObject(By.text(title)) ?: return@measureRepeated
            card.click()
            device.waitForIdle()
            // Wait for the destination composable to finish its first frame.
            device.waitForIdle(2_000)
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
