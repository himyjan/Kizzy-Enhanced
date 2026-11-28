package com.my.kizzy.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val baselineProfileCompilation =
        CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require)
    private val noCompilation = CompilationMode.None()

    @Test
    fun coldStartBaselineProfile() = measureStartup(baselineProfileCompilation, StartupMode.COLD)

    @Test
    fun coldStartNone() = measureStartup(noCompilation, StartupMode.COLD)

    @Test
    fun warmStartBaselineProfile() = measureStartup(baselineProfileCompilation, StartupMode.WARM)

    @Test
    fun warmStartNone() = measureStartup(noCompilation, StartupMode.WARM)

    @Test
    fun hotStartBaselineProfile() = measureStartup(baselineProfileCompilation, StartupMode.HOT)

    @Test
    fun hotStartNone() = measureStartup(noCompilation, StartupMode.HOT)

    @Test
    fun openMediaRpcBaselineProfile() = measureScreenOpen("Media RPC", baselineProfileCompilation)

    @Test
    fun openMediaRpcNone() = measureScreenOpen("Media RPC", noCompilation)

    @Test
    fun openManualRpcBaselineProfile() = measureScreenOpen("Manual RPC", baselineProfileCompilation)

    @Test
    fun openManualRpcNone() = measureScreenOpen("Manual RPC", noCompilation)

    @Test
    fun openCustomRpcBaselineProfile() = measureScreenOpen("Custom RPC", baselineProfileCompilation)

    @Test
    fun openCustomRpcNone() = measureScreenOpen("Custom RPC", noCompilation)

    @Test
    fun homeScrollFrameTimingBaselineProfile() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = baselineProfileCompilation,
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.text(FEATURES_HEADER)), LONG_TIMEOUT_MS)
        },
    ) {
        val scrollable = device.findObject(By.scrollable(true))
        scrollable?.scroll(Direction.DOWN, 0.75f)
        device.waitForIdle()
        scrollable?.scroll(Direction.UP, 0.75f)
        device.waitForIdle()
    }

    private fun measureStartup(
        compilationMode: CompilationMode,
        startupMode: StartupMode,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        iterations = ITERATIONS,
        startupMode = startupMode,
        setupBlock = {
            if (startupMode != StartupMode.HOT) {
                pressHome()
            }
        },
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.text(FEATURES_HEADER)), LONG_TIMEOUT_MS)
    }

    private fun measureScreenOpen(
        screenTitle: String,
        compilationMode: CompilationMode,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        setupBlock = {
            pressHome()
        },
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.text(FEATURES_HEADER)), LONG_TIMEOUT_MS)
        device.wait(Until.findObject(By.text(screenTitle)), SHORT_TIMEOUT_MS)?.click()
        device.waitForIdle()
    }

    private companion object {
        const val TARGET_PACKAGE = "com.my.kizzy.enhanced"
        const val FEATURES_HEADER = "Features"
        const val LONG_TIMEOUT_MS = 10_000L
        const val SHORT_TIMEOUT_MS = 5_000L
        const val ITERATIONS = 5
    }
}