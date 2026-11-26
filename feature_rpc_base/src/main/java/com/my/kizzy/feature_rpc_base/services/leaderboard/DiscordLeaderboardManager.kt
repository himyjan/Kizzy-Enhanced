package com.my.kizzy.feature_rpc_base.services.leaderboard

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.my.kizzy.preference.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

private val Context.leaderboardStoreDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "leaderboard_store"
)

private const val DEFAULT_INTERVAL_MINUTES = 5L
private const val DEFAULT_MAX_ENTRIES = 10

class DiscordLeaderboardManager private constructor(
    private val appContext: Context
) {
    private val logger: Logger = LoggerFactory.getLogger("DiscordLeaderboard")
    private val serializer = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val client = OkHttpClient.Builder().build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var periodicJob: Job? = null

    private val prefs: LeaderboardPrefs by lazy {
        LeaderboardPrefs(appContext)
    }

    companion object {
        @Volatile
        private var instance: DiscordLeaderboardManager? = null

        fun getInstance(context: Context): DiscordLeaderboardManager {
            return instance ?: synchronized(this) {
                instance ?: DiscordLeaderboardManager(context.applicationContext).also { instance = it }
            }
        }
    }

    suspend fun updateLeaderboard(entries: List<LeaderboardEntry>) {
        val token = prefs.getBotToken()
        val channelId = prefs.getChannelId()
        if (token.isBlank() || channelId.isBlank()) {
            logger.warn("Leaderboard not configured (missing bot token or channel ID)")
            return
        }
        if (entries.isEmpty()) {
            logger.debug("No entries to post")
            return
        }

        val payload = serializer.encodeToString(buildLeaderboardPayload(entries))
        val url = "https://discord.com/api/v10/channels/$channelId/messages"
        val jsonBody = payload.toRequestBody("application/json; charset=utf-8".toMediaType())

        var attempt = 0
        val maxAttempts = 5
        var delayMs = 1000L

        while (attempt < maxAttempts) {
            attempt++
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bot $token")
                    .header("User-Agent", "DiscordBot (https://github.com/dead8309/Kizzy, 1.0)")
                    .post(jsonBody)
                    .build()

                val outcome = withContext(Dispatchers.IO) {
                    client.newCall(request).execute().use { response ->
                        val code = response.code
                        val body = response.body.string()
                        code to body
                    }
                }

                val (code, body) = outcome
                when (code) {
                    200, 201, 204 -> {
                        logger.debug("Leaderboard posted successfully")
                        return
                    }
                    429 -> {
                        // Discord returns retry_after in seconds (float), convert to ms
                        val retryAfterMs = try {
                            val retrySeconds = serializer.decodeFromString<JsonObject>(body)["retry_after"]
                                ?.jsonPrimitive
                                ?.doubleOrNull
                            if (retrySeconds != null) (retrySeconds * 1000.0).toLong() else delayMs
                        } catch (_: Exception) {
                            delayMs
                        }
                        logger.warn("Rate limited by Discord, retrying in ${retryAfterMs}ms")
                        delay(retryAfterMs.coerceAtLeast(1000L).milliseconds)
                        continue
                    }
                    else -> {
                        logger.error("Failed to post leaderboard: $code $body")
                        return
                    }
                }
            } catch (e: Exception) {
                logger.error("Leaderboard update failed (attempt $attempt/$maxAttempts): ${e.message}")
                if (attempt < maxAttempts) {
                    delay(delayMs.milliseconds)
                    delayMs *= 2L
                }
            }
        }
        logger.error("Leaderboard update failed after $maxAttempts attempts")
    }

    private suspend fun buildLeaderboardPayload(entries: List<LeaderboardEntry>): JsonObject {
        return buildJsonObject {
            put("embeds", JsonArray(listOf(buildLeaderboardEmbedObject(entries))))
        }
    }

    private suspend fun buildLeaderboardEmbedObject(entries: List<LeaderboardEntry>): JsonObject {
        val title = prefs.getTitle().ifBlank { "🏆 Kizzy Leaderboard" }
        val description = prefs.getDescription()
        val nowIso = Instant.now().toString()

        val fields = entries.take(25).mapIndexed { index, entry ->
            buildJsonObject {
                put("name", JsonPrimitive("#${index + 1}  ${entry.name.ifBlank { "Unknown" }}"))
                put("value", JsonPrimitive(entry.score.toString()))
                put("inline", JsonPrimitive(true))
            }
        }

        return buildJsonObject {
            put("title", JsonPrimitive(title))
            if (description.isNotBlank()) {
                put("description", JsonPrimitive(description))
            }
            put("color", JsonPrimitive(0x5865F2))
            put("fields", JsonArray(fields))
            put("footer", buildJsonObject {
                put("text", JsonPrimitive("Kizzy RPC • Updated $nowIso"))
            })
            put("timestamp", JsonPrimitive(nowIso))
        }
    }

    fun startPeriodicUpdates(intervalMinutes: Long? = null) {
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (isActive) {
                val interval = (intervalMinutes ?: prefs.getIntervalMinutes()).coerceAtLeast(1L)
                try {
                    val entries = LeaderboardStore.getInstance(appContext)
                        .getTopEntries(prefs.getMaxEntries())
                    if (entries.isNotEmpty()) {
                        updateLeaderboard(entries)
                    }
                } catch (e: Exception) {
                    logger.error("Periodic update failed: ${e.message}")
                }
                delay((interval * 60_000L).milliseconds)
            }
        }
    }

    fun stopPeriodicUpdates() {
        periodicJob?.cancel()
        periodicJob = null
    }

    suspend fun submitScore(playerName: String, score: Int, gameId: String = "default") {
        LeaderboardStore.getInstance(appContext).submitScore(playerName, score, gameId)
    }

    /** Returns the top leaderboard entries, sorted by score descending. */
    suspend fun getTopEntries(limit: Int): List<LeaderboardEntry> =
        prefs.getTopEntries(limit)
}

@Serializable
data class LeaderboardEntry(
    val playerId: String,
    val name: String,
    val score: Int,
    val gameId: String = "default",
    val updatedAt: Long = System.currentTimeMillis()
)

class LeaderboardStore private constructor(
    private val context: Context
) {
    private val dataStore = context.leaderboardStoreDataStore
    private val serializer = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    companion object {
        private val SCORES_KEY = stringPreferencesKey("leaderboard_scores")

        @Volatile
        private var instance: LeaderboardStore? = null

        fun getInstance(context: Context): LeaderboardStore {
            return instance ?: synchronized(this) {
                instance ?: LeaderboardStore(context.applicationContext).also { instance = it }
            }
        }
    }

    suspend fun submitScore(playerName: String, score: Int, gameId: String) {
        val current = getTopEntries(Int.MAX_VALUE).toMutableList()
        val existingIndex = current.indexOfFirst { it.playerId == playerName && it.gameId == gameId }
        val newEntry = LeaderboardEntry(
            playerId = playerName,
            name = playerName,
            score = score,
            gameId = gameId
        )

        if (existingIndex != -1) {
            val existing = current[existingIndex]
            if (score > existing.score) {
                current[existingIndex] = newEntry
            }
        } else {
            current.add(newEntry)
        }

        val sorted = current.sortedByDescending { it.score }
        dataStore.edit { preferences ->
            preferences[SCORES_KEY] = serializer.encodeToString(sorted)
        }
    }

    suspend fun getTopEntries(limit: Int): List<LeaderboardEntry> {
        val jsonString = dataStore.data.first()[SCORES_KEY].orEmpty()
        if (jsonString.isBlank()) return emptyList()
        return try {
            serializer.decodeFromString<List<LeaderboardEntry>>(jsonString)
                .sortedByDescending { it.score }
                .take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

class LeaderboardPrefs(private val context: Context) {

    suspend fun getBotToken(): String =
        Prefs[Prefs.DISCORD_LEADERBOARD_BOT_TOKEN, ""]

    suspend fun setBotToken(value: String) {
        Prefs[Prefs.DISCORD_LEADERBOARD_BOT_TOKEN] = value
    }

    suspend fun getChannelId(): String =
        Prefs[Prefs.DISCORD_LEADERBOARD_CHANNEL_ID, ""]

    suspend fun setChannelId(value: String) {
        Prefs[Prefs.DISCORD_LEADERBOARD_CHANNEL_ID] = value
    }

    suspend fun getIntervalMinutes(): Long =
        Prefs[Prefs.DISCORD_LEADERBOARD_UPDATE_INTERVAL_MIN, DEFAULT_INTERVAL_MINUTES]

    suspend fun setIntervalMinutes(value: Long) {
        Prefs[Prefs.DISCORD_LEADERBOARD_UPDATE_INTERVAL_MIN] = value
    }

    suspend fun getMaxEntries(): Int =
        Prefs[Prefs.DISCORD_LEADERBOARD_MAX_ENTRIES, DEFAULT_MAX_ENTRIES]

    suspend fun setMaxEntries(value: Int) {
        Prefs[Prefs.DISCORD_LEADERBOARD_MAX_ENTRIES] = value
    }

    suspend fun getTitle(): String =
        Prefs[Prefs.DISCORD_LEADERBOARD_TITLE, "🏆 Kizzy Leaderboard"]

    suspend fun setTitle(value: String) {
        Prefs[Prefs.DISCORD_LEADERBOARD_TITLE] = value
    }

    suspend fun getDescription(): String =
        Prefs[Prefs.DISCORD_LEADERBOARD_DESCRIPTION, ""]

    suspend fun setDescription(value: String) {
        Prefs[Prefs.DISCORD_LEADERBOARD_DESCRIPTION] = value
    }

    /** The top leaderboard entries, sorted by score descending. */
    suspend fun getTopEntries(limit: Int): List<LeaderboardEntry> =
        LeaderboardStore.getInstance(context).getTopEntries(limit)

    /** Submits or updates a score for a player in a game. */
    suspend fun submitScore(playerName: String, score: Int, gameId: String = "default") {
        LeaderboardStore.getInstance(context).submitScore(playerName, score, gameId)
    }
}