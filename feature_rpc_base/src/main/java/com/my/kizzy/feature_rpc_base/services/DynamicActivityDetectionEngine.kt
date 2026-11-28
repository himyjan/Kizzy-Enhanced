package com.my.kizzy.feature_rpc_base.services

import android.app.role.RoleManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject

/**
 * Dynamically detects AI/LLM applications and games on the device without hardcoded package lists.
 */
class DynamicActivityDetectionEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val packageManager: PackageManager = context.packageManager
    private val labelCache = HashMap<String, String>()

    /** Semantic keywords found in AI app names, developer names, or descriptions. */
    private val aiSemanticKeywords = listOf(
        "ai", "gpt", "chat", "bot", "gemini", "claude", "llm", "copilot",
        "perplexity", "deepseek", "llama", "ollama", "assistant", "intelligence",
        "openai", "x.ai", "bard"
    )

    private fun getCachedLabel(packageName: String, appInfo: ApplicationInfo): String {
        return labelCache[packageName] ?: run {
            val label = packageManager.getApplicationLabel(appInfo).toString()
            labelCache[packageName] = label
            label
        }
    }

    fun identifyAiApp(packageName: String): DetectedAiApp? {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val appLabel = getCachedLabel(packageName, appInfo)
            val appLabelLower = appLabel.lowercase()

            // Require semantic AI match so non-AI productivity or undefined-category apps are not misclassified.
            val isSemanticMatch = aiSemanticKeywords.any { appLabelLower.contains(it) }

            if (isSemanticMatch) {
                DetectedAiApp(
                    displayName = appLabel,
                    packageName = packageName,
                    iconUrl = "https://faviconkit.com/${packageName}/144"
                )
            } else {
                null
            }
        } catch (_: PackageManager.NameNotFoundException) {
            labelCache.remove(packageName)
            null
        }
    }

    fun getActiveAssistantPackage(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
                if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                    Settings.Secure.getString(context.contentResolver, "assistant")
                        ?.let { it.split("/").firstOrNull() }
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        } else {
            Settings.Secure.getString(context.contentResolver, "assistant")
                ?.let { it.split("/").firstOrNull() }
        }
    }

    fun isAiAssistantActive(): Boolean {
        val assistantPkg = getActiveAssistantPackage() ?: return false
        return identifyAiApp(assistantPkg) != null
    }

    fun identifyGame(packageName: String): DetectedGameApp? {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val appLabel = getCachedLabel(packageName, appInfo)

            val isGameCategory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appInfo.category == ApplicationInfo.CATEGORY_GAME
            } else false

            @Suppress("DEPRECATION")
            val isGameFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            } else false

            if (isGameCategory || isGameFlag) {
                DetectedGameApp(
                    displayName = appLabel,
                    packageName = packageName,
                    iconUrl = "https://faviconkit.com/${packageName}/144"
                )
            } else {
                null
            }
        } catch (_: PackageManager.NameNotFoundException) {
            labelCache.remove(packageName)
            null
        }
    }

    fun isCurrentAppAGame(packageName: String): Boolean {
        return identifyGame(packageName) != null
    }
}

data class DetectedAiApp(
    val displayName: String,
    val packageName: String,
    val iconUrl: String
)

data class DetectedGameApp(
    val displayName: String,
    val packageName: String,
    val iconUrl: String
)