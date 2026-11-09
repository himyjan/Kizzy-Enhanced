package com.my.kizzy

import android.app.Application
import android.content.Intent
import android.os.Build
import com.google.android.material.color.DynamicColors
import com.my.kizzy.feature_logs.LoggerProvider
import com.my.kizzy.preference.PreferenceConfig
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.data.work.WorkManagerHelper
import androidx.work.Configuration
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.Prefs.USE_RPC_BUTTONS
import com.my.kizzy.preference.Prefs.EXPERIMENTAL_RPC_USE_APPS_RPC
import com.my.kizzy.preference.Prefs.EXPERIMENTAL_RPC_USE_MEDIA_RPC
import dagger.hilt.android.HiltAndroidApp
import androidx.hilt.work.HiltWorkerFactory
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Initialize components with error handling to prevent startup crashes
        // on emulators and OEM devices with restricted background access

        // 1. Dynamic Colors (API 31+ only - safe on older devices)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                DynamicColors.applyToActivitiesIfAvailable(this)
            }
        } catch (e: Exception) {
            // Dynamic colors not available on this device - non-critical
        }

        // 3. Preferences (MMKV) - wrap in try-catch for storage issues on emulators
        try {
            PreferenceConfig.apply(this)
        } catch (e: Exception) {
            // Preferences initialization failed - non-critical
        }

        // 4. Logger (Hilt EntryPoint) - wrap in try-catch for Hilt issues
        try {
            LoggerProvider.init(this)
        } catch (e: Exception) {
            // Logger not available - non-critical
        }

        // 5. AppUtils (ActivityManager) - should always work but wrap for safety
        try {
            AppUtils.init(this)
        } catch (e: Exception) {
            // AppUtils not available - non-critical
        }

        // 6. WorkManager - schedule cleanup work (non-blocking)
        try {
            WorkManagerHelper.scheduleDailyCleanup(this)
        } catch (e: Exception) {
            // WorkManager not available - non-critical
        }

        // 7. Auto-start RPC service if it was previously enabled
        try {
            val rpcEnabled = Prefs[Prefs.EXPERIMENTAL_RPC_USE_MEDIA_RPC, false] || Prefs[Prefs.EXPERIMENTAL_RPC_USE_APPS_RPC, false] || Prefs[Prefs.USE_RPC_BUTTONS, false]
            if (rpcEnabled) {
                val intent = Intent(this, com.my.kizzy.feature_rpc_base.services.MediaRpcService::class.java)
                startService(intent)
                android.util.Log.d("App", "Auto-started MediaRpcService - RPC was previously enabled")
            }
        } catch (e: Exception) {
            // RPC auto-start failed - non-critical
        }

        // Install native uncaught exception handler for crash reporting
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stringWriter = StringWriter()
            throwable.printStackTrace(PrintWriter(stringWriter))
            val traceText = stringWriter.toString()

            val intent = Intent(this, CrashReportActivity::class.java).apply {
                putExtra("KEY_ERROR_LOG", "Thread: [${thread.name}]\n\n$traceText")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)

            // Terminate the crashed process so it doesn't leak memory
            exitProcess(2)
        }
    }
}
