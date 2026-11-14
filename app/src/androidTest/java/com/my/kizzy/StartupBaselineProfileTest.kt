package com.my.kizzy

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiObjectNotFoundException
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBaselineProfileTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startupBaselineProfile() {
        val device = InstrumentationRegistry.getInstrumentation().uiDevice
        device.pressHome()
        device.waitForIdle()

        // Launch the app from home screen
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)

        // Wait for the app to be idle and the main screen to appear
        device.waitForIdle()
        waitForElement(By.res("com.my.kizzy.enhanced:id/features_recycler"), 5000)

        // Navigate to Console RPC screen to trace console RPC initialization
        val consoleRpc = device.findObject(By.text("Console RPC"))
        if (consoleRpc.exists()) {
            consoleRpc.click()
            device.waitForIdle()
            waitForElement(By.text("Auto-detect DroidDeck / Eden"), 3000)

            // Scroll the games list to trigger composition of list items
            val recycler = device.findObject(By.res("com.my.kizzy.enhanced:id/games_recycler"))
            if (recycler.exists()) {
                recycler.swipe(UiDevice.Direction.DOWN, 0.5f, 0.5f, 0.2f, 10)
                device.waitForIdle()
            }

            // Go back to home
            device.pressBack()
            device.waitForIdle()
        }

        // Navigate to Media RPC
        val mediaRpc = device.findObject(By.text("Media RPC"))
        if (mediaRpc.exists()) {
            mediaRpc.click()
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()
        }

        // Navigate to Custom RPC
        val customRpc = device.findObject(By.text("Custom RPC"))
        if (customRpc.exists()) {
            customRpc.click()
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()
        }
    }

    private fun waitForElement(by: By, timeoutMs: Long): UiObject? {
        val obj = device.findObject(by)
        return if (obj.waitForExists(timeoutMs)) obj else null
    }

    companion object {
        private val device: UiDevice by lazy {
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        }
    }
}
