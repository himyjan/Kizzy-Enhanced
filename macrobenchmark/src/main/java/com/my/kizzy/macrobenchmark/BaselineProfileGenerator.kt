package com.my.kizzy.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun baselineProfile() = baselineProfileRule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text(FEATURES_HEADER)), LONG_TIMEOUT_MS)

        RPC_SCREENS.forEach { title ->
            openCardAndReturn(title)
        }

        DRAWER_DESTINATIONS.forEach { label ->
            openDrawerEntryAndReturn(label)
        }

        device.wait(Until.hasObject(By.text(FEATURES_HEADER)), LONG_TIMEOUT_MS)
    }

    private fun MacrobenchmarkScope.openCardAndReturn(title: String) {
        val card = device.wait(Until.findObject(By.text(title)), SHORT_TIMEOUT_MS) ?: return
        card.click()
        device.waitForIdle()
        device.pressBack()
        device.wait(Until.hasObject(By.text(FEATURES_HEADER)), SHORT_TIMEOUT_MS)
    }

    private fun MacrobenchmarkScope.openDrawerEntryAndReturn(label: String) {
        val menu = device.wait(Until.findObject(By.desc(MENU_CONTENT_DESC)), SHORT_TIMEOUT_MS) ?: return
        menu.click()
        device.waitForIdle()

        val entry = device.wait(Until.findObject(By.text(label)), SHORT_TIMEOUT_MS)
        if (entry != null) {
            entry.click()
            device.waitForIdle()
            device.pressBack()
        } else {
            device.pressBack()
        }
        device.waitForIdle()
        device.wait(Until.hasObject(By.text(FEATURES_HEADER)), SHORT_TIMEOUT_MS)
    }

    private companion object {
        const val TARGET_PACKAGE = "com.my.kizzy.enhanced"
        const val FEATURES_HEADER = "Features"
        const val MENU_CONTENT_DESC = "Outlined.Menu"
        const val LONG_TIMEOUT_MS = 10_000L
        const val SHORT_TIMEOUT_MS = 5_000L

        val RPC_SCREENS = listOf("Media RPC", "Manual RPC", "Custom RPC")
        val DRAWER_DESTINATIONS = listOf("RPC Settings", "Logs")
    }
}