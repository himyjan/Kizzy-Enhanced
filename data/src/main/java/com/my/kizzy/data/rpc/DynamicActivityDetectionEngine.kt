package com.my.kizzy.data.rpc

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GamesApiUrl

data class DetectedActivityApp(
    val displayName: String,
    val packageName: String,
    val iconUrl: String? = null,
    val applicationId: String? = null,
)

@Serializable
data class GamePresetDto(
    val label: String = "",
    val title: String = "",
    val link: String = "",
)

data class GamePresetEntry(
    val label: String,
    val title: String,
    val iconUrl: String?,
)

interface GamePresetProvider {
    suspend fun fetchGamePresets(): List<GamePresetEntry>
}

@Singleton
class ApiGamePresetProvider @Inject constructor(
    private val httpClient: HttpClient,
    @GamesApiUrl private val gamesApiUrl: String,
) : GamePresetProvider {

    private val ignoredTitles = setOf("updatecheck", "broadcastobj")

    override suspend fun fetchGamePresets(): List<GamePresetEntry> {
        if (gamesApiUrl.isBlank()) return emptyList()
        val response: List<GamePresetDto> = httpClient.get(gamesApiUrl).body()
        return response.mapNotNull { dto ->
            val title = dto.title.trim()
            val link = dto.link.trim()
            if (title.isEmpty() || title.lowercase() in ignoredTitles) return@mapNotNull null
            val validIconUrl = link.takeIf {
                it.startsWith("https://", ignoreCase = true) ||
                        it.startsWith("http://", ignoreCase = true)
            }
            GamePresetEntry(
                label = dto.label.trim(),
                title = title,
                iconUrl = validIconUrl,
            )
        }
    }
}

@Singleton
class DynamicActivityDetectionEngine @Inject constructor(
    private val gamePresetProvider: GamePresetProvider,
) {
    private val cacheMutex = Mutex()
    private var cachedGamesByNormalizedTitle: Map<String, GamePresetEntry> = emptyMap()
    private var lastFetchMs: Long = 0L
    private val cacheTtlMs = 6 * 60 * 60 * 1000L

    private val knownAiApps = mapOf(
        "com.openai.chatgpt" to "ChatGPT",
        "com.google.android.apps.bard" to "Gemini",
        "com.anthropic.claude" to "Claude",
        "com.microsoft.copilot" to "Microsoft Copilot",
        "ai.perplexity.app.android" to "Perplexity",
    )

    suspend fun identifyGame(queryOrPackageName: String, appName: String? = null): DetectedActivityApp? {
        if (queryOrPackageName.isBlank() && appName.isNullOrBlank()) return null
        val presetsByTitle = getGamePresetsCached()

        val candidates = listOfNotNull(appName, queryOrPackageName)
            .map { normalizeTitle(it) }
            .filter { it.isNotEmpty() }

        for (candidate in candidates) {
            presetsByTitle[candidate]?.let { match ->
                return DetectedActivityApp(
                    displayName = match.title,
                    packageName = queryOrPackageName,
                    iconUrl = match.iconUrl,
                )
            }
        }
        return null
    }

    fun identifyAiApp(packageName: String): DetectedActivityApp? =
        knownAiApps[packageName]?.let {
            DetectedActivityApp(displayName = it, packageName = packageName)
        }

    suspend fun refreshPresets(force: Boolean = true) {
        getGamePresetsCached(force = force)
    }

    private suspend fun getGamePresetsCached(force: Boolean = false): Map<String, GamePresetEntry> =
        cacheMutex.withLock {
            val now = System.currentTimeMillis()
            if (!force && cachedGamesByNormalizedTitle.isNotEmpty() && (now - lastFetchMs) < cacheTtlMs) {
                return@withLock cachedGamesByNormalizedTitle
            }

            val fetched = runCatching { gamePresetProvider.fetchGamePresets() }.getOrNull()
            if (!fetched.isNullOrEmpty()) {
                val map = LinkedHashMap<String, GamePresetEntry>()
                for (entry in fetched) {
                    val key = normalizeTitle(entry.title)
                    if (key.isNotEmpty()) {
                        map.putIfAbsent(key, entry)
                    }
                }
                cachedGamesByNormalizedTitle = map
                lastFetchMs = now
            }
            cachedGamesByNormalizedTitle
        }

    private fun normalizeTitle(raw: String): String =
        raw.trim().lowercase().replace(Regex("\\s+"), " ")
}