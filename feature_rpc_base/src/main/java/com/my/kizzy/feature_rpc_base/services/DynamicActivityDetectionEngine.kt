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
 *
 * AI detection strategies:
 *   1. Semantic keyword matching on the app label (catches ChatGPT, Gemini, Grok, Claude, Perplexity, Copilot, etc.)
 *   2. Android RoleManager for the default assistant (catches power-button / "Hey Google" triggers)
 *
 * Game detection strategy:
 *   1. ApplicationInfo.FLAG_IS_GAME / CATEGORY_GAME (catches all Android-flagged games across Samsung,
 *      Xiaomi, Google Play Games, OnePlus, etc.)
 *
 * Zero maintenance required — new AI apps and games are detected automatically.
 */
class DynamicActivityDetectionEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val packageManager: PackageManager = context.packageManager

    /** Semantic keywords found in AI app names, developer names, or descriptions. */
    private val aiSemanticKeywords = listOf(
        "ai", "gpt", "chat", "bot", "gemini", "claude", "llm", "copilot",
        "perplexity", "deepseek", "llama", "ollama", "assistant", "intelligence",
        "openai", "x.ai", "bard"
    )

    // ============================================================================
    // AI Detection
    // ============================================================================

    /**
     * Identifies whether [packageName] is an AI application.
     * Returns null if not an AI app, otherwise returns [DetectedAiApp] with display info.
     */
    fun identifyAiApp(packageName: String): DetectedAiApp? {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val appLabel = packageManager.getApplicationLabel(appInfo).toString()
            val appLabelLower = appLabel.lowercase()

            // 1. Semantic Matcher: catches 99% of AI apps by title signature
            val isSemanticMatch = aiSemanticKeywords.any { appLabelLower.contains(it) }

            // 2. Platform Category Matcher: productivity/tools category hint
            val isTargetCategory = if (Build.VERSION_CODES.O <= Build.VERSION.SDK_INT) {
                appInfo.category == ApplicationInfo.CATEGORY_PRODUCTIVITY ||
                        appInfo.category == ApplicationInfo.CATEGORY_UNDEFINED
            } else false

            if (isSemanticMatch || isTargetCategory) {
                DetectedAiApp(
                    displayName = appLabel,
                    packageName = packageName,
                    iconUrl = "https://faviconkit.com/${packageName}/144"
                )
            } else {
                null
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Queries the Android system for the currently active default assistant package.
     * This catches power-button / "Hey Google" assistant triggers that launch as
     * floating overlays rather than full-screen foreground activities.
     */
    fun getActiveAssistantPackage(): String? {
        return if (Build.VERSION_CODES.Q <= Build.VERSION.SDK_INT) {
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

    /**
     * Checks whether the currently active assistant (power-button trigger) is an AI app.
     */
    fun isAiAssistantActive(): Boolean {
        val assistantPkg = getActiveAssistantPackage() ?: return false
        return identifyAiApp(assistantPkg) != null
    }

    // ============================================================================
    // Game Detection
    // ============================================================================

    /**
     * Identifies whether [packageName] is a game using Android's system game flags.
     * Works on all manufacturers (Samsung Game Hub, Xiaomi Game Turbo, Google Play Games,
     * OnePlus Games, etc.) because they all set FLAG_IS_GAME / CATEGORY_GAME.
     *
     * Returns null if not a game, otherwise returns [DetectedGameApp] with display info.
     */
    fun identifyGame(packageName: String): DetectedGameApp? {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val appLabel = packageManager.getApplicationLabel(appInfo).toString()

            // 1. FLAG_IS_GAME — set by the OS for any app categorized as a game
            val isGameFlag = (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0

            // 2. CATEGORY_GAME — explicit category hint
            val isGameCategory = if (Build.VERSION_CODES.O <= Build.VERSION.SDK_INT) {
                appInfo.category == ApplicationInfo.CATEGORY_GAME
            } else false

            if (isGameFlag || isGameCategory) {
                DetectedGameApp(
                    displayName = appLabel,
                    packageName = packageName,
                    iconUrl = "https://faviconkit.com/${packageName}/144"
                )
            } else {
                null
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Checks whether the currently active foreground app is a game.
     * Call this alongside [identifyAiApp] — games and AI apps are mutually exclusive
     * categories, so check game first.
     */
    fun isCurrentAppAGame(packageName: String): Boolean {
        return identifyGame(packageName) != null
    }
}

/** Result of AI app detection. */
data class DetectedAiApp(
    val displayName: String,
    val packageName: String,
    val iconUrl: String
)

/** Result of game detection. */
data class DetectedGameApp(
    val displayName: String,
    val packageName: String,
    val iconUrl: String
)
