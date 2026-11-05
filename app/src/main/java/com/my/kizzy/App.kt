package com.my.kizzy

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.my.kizzy.feature_crash_handler.CrashHandlerConfig
import com.my.kizzy.feature_logs.LoggerProvider
import com.my.kizzy.preference.PreferenceConfig
import com.my.kizzy.feature_rpc_base.AppUtils
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import androidx.hilt.work.HiltWorkerFactory
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        CrashHandlerConfig.apply()
        PreferenceConfig.apply(this)
        LoggerProvider.init(this)
        AppUtils.init(this)
    }
}