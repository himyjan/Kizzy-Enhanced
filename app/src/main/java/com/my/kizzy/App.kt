package com.my.kizzy

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.StrictMode
import android.util.Log
import androidx.compose.runtime.Composer
import androidx.compose.runtime.tooling.ComposeStackTraceMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.android.material.color.DynamicColors
import com.my.kizzy.data.rpc.DynamicActivityDetectionEngine
import com.my.kizzy.data.work.WorkManagerHelper
import com.my.kizzy.feature_logs.LoggerProvider
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.RpcManager
import com.my.kizzy.preference.PreferenceConfig
import com.my.kizzy.preference.Prefs
import dagger.hilt.android.HiltAndroidApp
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var dynamicActivityDetectionEngine: DynamicActivityDetectionEngine

    private val scope = CoroutineScope(
        SupervisorJob() +
                CoroutineExceptionHandler { _, exception ->
                    Log.e("AppCoroutineScope", "Uncaught coroutine exception", exception)
                } +
                Dispatchers.Main.immediate
    )

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }

        try {
            Composer.setDiagnosticStackTraceMode(
                if (BuildConfig.DEBUG) {
                    ComposeStackTraceMode.SourceInformation
                } else {
                    ComposeStackTraceMode.GroupKeys
                }
            )
        } catch (_: Exception) {
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                DynamicColors.applyToActivitiesIfAvailable(this)
            }
        } catch (_: Exception) {
        }

        try {
            PreferenceConfig.apply(this)
        } catch (_: Exception) {
        }

        try {
            LoggerProvider.init(this)
        } catch (_: Exception) {
        }

        try {
            AppUtils.init(this)
        } catch (_: Exception) {
        }

        try {
            WorkManagerHelper.scheduleDailyCleanup(this)
        } catch (_: Exception) {
        }

        try {
            val appContext = this@App
            scope.launch {
                Prefs.awaitInitialized()
                withContext(Dispatchers.IO) {
                    runCatching { dynamicActivityDetectionEngine.refreshPresets(force = false) }
                }
                withContext(Dispatchers.Main) {
                    RpcManager.getInstance(appContext).restoreActiveRpc()
                    Log.d("App", "Restored RPC state after cold start")
                }
            }
        } catch (_: Exception) {
        }

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stringWriter = StringWriter()
            throwable.printStackTrace(PrintWriter(stringWriter))
            val traceText = stringWriter.toString()

            val intent = Intent(this, CrashReportActivity::class.java).apply {
                putExtra("KEY_ERROR_LOG", "Thread: [${thread.name}]\n\n$traceText")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)

            exitProcess(2)
        }
    }
}