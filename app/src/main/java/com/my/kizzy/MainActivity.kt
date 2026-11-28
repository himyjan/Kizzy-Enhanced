package com.my.kizzy

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.browser.auth.AuthTabIntent
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import com.my.kizzy.preference.getLanguageConfig
import com.my.kizzy.ui.theme.KizzyTheme
import com.my.kizzy.ui.theme.LocalDarkTheme
import com.my.kizzy.ui.theme.LocalDynamicColorSwitch
import com.my.kizzy.ui.theme.SettingsProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var usageAccessStatus: MutableState<Boolean>
    private lateinit var notificationListenerAccess: MutableState<Boolean>
    private lateinit var batteryOptimizationStatus: MutableState<Boolean>

    // Holds the currently active screen's callback: (resultCode, callbackIntent) -> Unit.
    // Screens (RpcSettings / UserScreen) register via Kizzy(onBindAuthTabCallback).
    private var activeAuthTabCallback: ((Int, Intent?) -> Unit)? = null

    // A deep link can arrive before the composable has bound its callback (cold start).
    // Hold it here and replay it as soon as a callback is registered.
    private var pendingOAuthCallbackIntent: Intent? = null

    // Registered as a field so it is ready before the activity reaches STARTED.
    // AuthTabIntent results are normalized into (resultCode, ACTION_VIEW Intent) so the
    // active screen's DiscordOAuth2Auth.onActivityResult handles both browser paths.
    private val authTabLauncher: ActivityResultLauncher<Intent> =
        AuthTabIntent.registerActivityResultLauncher(this) { result ->
            Log.d(TAG, "AuthTab result: code=${result.resultCode}, uri=${result.resultUri}")
            val callbackIntent = result.resultUri?.let { uri -> Intent(Intent.ACTION_VIEW, uri) }
            deliverOAuthResult(result.resultCode, callbackIntent)
        }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enables edge-to-edge rendering automatically for API 35+ and gracefully for older versions
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        usageAccessStatus = mutableStateOf(this.hasUsageAccess())
        notificationListenerAccess = mutableStateOf(this.hasNotificationAccess())
        batteryOptimizationStatus = mutableStateOf(this.isIgnoringBatteryOptimizations())
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }
        // Apply locales off-main thread to avoid blocking UI
        if (Build.VERSION.SDK_INT < 33) {
            lifecycleScope.launch(Dispatchers.IO) {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(getLanguageConfig())
                )
            }
        }
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            SettingsProvider(windowSizeClass.widthSizeClass) {
                KizzyTheme(
                    darkTheme = LocalDarkTheme.current.isDarkTheme(),
                    isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                    isDynamicColorEnabled = LocalDynamicColorSwitch.current,
                ) {
                    Kizzy(
                        usageAccessStatus = usageAccessStatus,
                        notificationListenerAccess = notificationListenerAccess,
                        batteryOptimizationStatus = batteryOptimizationStatus,
                        authTabLauncher = authTabLauncher,
                        onBindAuthTabCallback = { callback -> bindAuthTabCallback(callback) },
                    )
                }
            }
        }
        // Cold start via the kizzy://oauth2/callback deep link (no onNewIntent call).
        handleOAuthCallbackIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Warm start / existing singleTask activity: route the fallback deep link
        // through the same normalized callback used by AuthTabIntent results.
        handleOAuthCallbackIntent(intent)
    }

    private fun handleOAuthCallbackIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "kizzy" && uri.host == "oauth2") {
            Log.d(TAG, "Received OAuth2 deep link intent: $uri")
            deliverOAuthResult(Activity.RESULT_OK, Intent(Intent.ACTION_VIEW, uri))
        }
    }

    private fun deliverOAuthResult(resultCode: Int, callbackIntent: Intent?) {
        val callback = activeAuthTabCallback
        if (callback != null) {
            callback(resultCode, callbackIntent)
        } else if (resultCode == Activity.RESULT_OK && callbackIntent != null) {
            pendingOAuthCallbackIntent = callbackIntent
        }
    }

    private fun bindAuthTabCallback(callback: (Int, Intent?) -> Unit) {
        activeAuthTabCallback = callback
        pendingOAuthCallbackIntent?.let { pending ->
            pendingOAuthCallbackIntent = null
            callback(Activity.RESULT_OK, pending)
        }
    }

    override fun onResume() {
        super.onResume()
        notificationListenerAccess.value = hasNotificationAccess()
        usageAccessStatus.value = hasUsageAccess()
        batteryOptimizationStatus.value = isIgnoringBatteryOptimizations()
    }

    override fun onDestroy() {
        activeAuthTabCallback = null
        pendingOAuthCallbackIntent = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun Context.hasUsageAccess(): Boolean {
        return try {
            val packageManager: PackageManager = this.packageManager
            val applicationInfo = packageManager.getApplicationInfo(this.packageName, 0)
            val appOpsManager = this.getSystemService(APP_OPS_SERVICE) as AppOpsManager
            val mode = appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                applicationInfo.uid,
                applicationInfo.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun Context.hasNotificationAccess(): Boolean {
        val enabledNotificationListeners = Settings.Secure.getString(
            this.contentResolver, "enabled_notification_listeners"
        )
        return enabledNotificationListeners != null && enabledNotificationListeners.contains(this.packageName)
    }

    private fun Context.isIgnoringBatteryOptimizations(): Boolean {
        return (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(packageName)
    }

    companion object {
        private const val TAG = "MainActivity"

        fun setLanguage(locale: String) {
            val localeListCompat =
                if (locale.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(locale)
            AppCompatDelegate.setApplicationLocales(localeListCompat)
        }
    }
}