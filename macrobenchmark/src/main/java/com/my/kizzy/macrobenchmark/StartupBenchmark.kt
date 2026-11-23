package com.my.kizzy.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start benchmark used to prove the baseline profile actually helps.
 *
 * Both tests measure exactly the same flow; the only difference is the
 * compilation mode. Running them back to back on the same device gives the
 * before/after numbers:
 *
 *   - startupCompilationNone            -> no AOT compilation, JIT only
 *   - startupCompilationBaselineProfile -> the shipped baseline profile is applied
 *
 * The delta between the two is the startup win the profile buys. If it is
 * close to zero, the profile does not cover the real startup path and the
 * generator needs new user flows.
 *
 * Run with:
 *   ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.my.kizzy.macrobenchmark.StartupBenchmark
 *
 * Compose composition tracing can be enabled on top of this by passing
 * `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.fullTracing.enable=true`;
 * the trace then contains `Recompose`/`Composition` slices for every composable
 * and can be opened at https://ui.perfetto.dev.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCompilationBaselineProfile() = startup(
        compilationMode = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Require,
        ),
    )

    @Test
    fun startupCompilationNone() = startup(
        compilationMode = CompilationMode.None(),
    )

    private fun startup(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        setupBlock = {
            // Force-stop so cold start really starts from a dead process.
            device.executeShellCommand("am force-stop $TARGET_PACKAGE")
            device.waitForIdle()
        },
    ) {
        startActivityAndWait()
    }

    private companion object {
        const val TARGET_PACKAGE = "com.my.kizzy.enhanced"
        const val ITERATIONS = 5
    }
}
