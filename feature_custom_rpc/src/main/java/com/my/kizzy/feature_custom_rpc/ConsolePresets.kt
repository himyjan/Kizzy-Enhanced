package com.my.kizzy.feature_custom_rpc

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.vector.ImageVector
import com.my.kizzy.preference.Prefs
import com.my.kizzy.resources.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One entry of the Console RPC preset list.
 *
 * A preset bundles every value Console RPC needs, so a single tap sets up the
 * presence for a console. Built-in presets are compiled into the app; the user
 * can also create their own, which are stored as JSON in [Prefs.CONSOLE_RPC_PRESETS].
 *
 * @param nameRes string resource with the display name, used by built-in presets
 *        so their names follow the app language. 0 for user presets.
 * @param name display name of a user preset. Ignored when [nameRes] is set.
 */
data class ConsolePreset(
    val id: String,
    val nameRes: Int,
    val name: String,
    val platform: String,
    val activityType: String,
    val status: String,
    val timestamps: Boolean,
    val coverArt: Boolean,
    val largeCoverArtUrl: String = "",
    val smallCoverArtUrl: String = "",
    val builtIn: Boolean,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("platform", platform)
        put("activityType", activityType)
        put("status", status)
        put("timestamps", timestamps)
        put("coverArt", coverArt)
        put("largeCoverArtUrl", largeCoverArtUrl)
        put("smallCoverArtUrl", smallCoverArtUrl)
    }

    companion object {
        fun fromJson(json: JSONObject): ConsolePreset = ConsolePreset(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            nameRes = 0,
            name = json.optString("name"),
            platform = json.optString("platform", "desktop"),
            activityType = json.optString("activityType", "0"),
            status = json.optString("status", "online"),
            timestamps = json.optBoolean("timestamps", false),
            coverArt = json.optBoolean("coverArt", true),
            largeCoverArtUrl = json.optString("largeCoverArtUrl", ""),
            smallCoverArtUrl = json.optString("smallCoverArtUrl", ""),
            builtIn = false,
        )
    }
}

/**
 * The built-in Console RPC presets and the persistence of the user presets.
 *
 * The list covers every platform Discord accepts plus the two launchers the
 * automatic detection knows (DroidDeck and Eden), so nothing that Console RPC
 * can report is missing from the tab.
 */
object ConsolePresetStore {

    /** Every preset that ships with the app. */
    fun builtIns(): List<ConsolePreset> = listOf(
        builtIn("xbox", R.string.console_preset_xbox, "xbox"),
        builtIn("ps5", R.string.console_preset_ps5, "ps5"),
        builtIn("ps4", R.string.console_preset_ps4, "ps4"),
        builtIn("nintendo", R.string.console_preset_nintendo, "nintendo"),
        builtIn("desktop", R.string.console_preset_desktop, "desktop"),
        builtIn("android", R.string.console_preset_android, "android"),
        builtIn("ios", R.string.console_preset_ios, "ios"),
        builtIn("samsung", R.string.console_preset_samsung, "samsung"),
        builtIn("embedded", R.string.console_preset_embedded, "embedded"),
        // DroidDeck runs PC games in a Linux container, Eden emulates a Switch.
        builtIn("droideck", R.string.console_preset_droideck, "desktop"),
        builtIn("eden", R.string.console_preset_eden, "nintendo"),
    )

    private fun builtIn(id: String, nameRes: Int, platform: String) = ConsolePreset(
        id = id,
        nameRes = nameRes,
        name = "",
        platform = platform,
        activityType = "0",
        status = "online",
        timestamps = false,
        coverArt = true,
        builtIn = true,
    )

    /** Presets created by the user, newest last. */
    fun custom(): List<ConsolePreset> {
        val raw = Prefs[Prefs.CONSOLE_RPC_PRESETS, ""]
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { ConsolePreset.fromJson(it) }
            }
        }.getOrDefault(emptyList())
    }

    /** Built-in presets followed by the user presets. */
    fun all(): List<ConsolePreset> = builtIns() + custom()

    fun saveAll(presets: List<ConsolePreset>) {
        val array = JSONArray()
        presets.forEach { array.put(it.toJson()) }
        Prefs[Prefs.CONSOLE_RPC_PRESETS] = array.toString()
    }

    /** Adds [preset] or replaces the user preset with the same id. */
    fun upsert(preset: ConsolePreset) {
        val current = custom()
        val index = current.indexOfFirst { it.id == preset.id }
        val updated = if (index >= 0) {
            current.toMutableList().also { it[index] = preset }
        } else {
            current + preset
        }
        saveAll(updated)
    }

    fun delete(id: String) {
        saveAll(custom().filterNot { it.id == id })
        if (selectedId() == id) Prefs[Prefs.CONSOLE_RPC_PRESET] = ""
    }

    fun find(id: String?): ConsolePreset? =
        if (id.isNullOrBlank()) null else all().firstOrNull { it.id == id }

    fun selectedId(): String = Prefs[Prefs.CONSOLE_RPC_PRESET, ""]

    fun select(id: String) {
        Prefs[Prefs.CONSOLE_RPC_PRESET] = id
    }

    /** A blank preset for the "new preset" dialog. */
    fun newPreset(): ConsolePreset = ConsolePreset(
        id = UUID.randomUUID().toString(),
        nameRes = 0,
        name = "",
        platform = "desktop",
        activityType = "0",
        status = "online",
        timestamps = false,
        coverArt = true,
        largeCoverArtUrl = "",
        smallCoverArtUrl = "",
        builtIn = false,
    )
}

/** Icon shown next to a preset. */
fun consolePresetIcon(preset: ConsolePreset): ImageVector = when (preset.platform) {
    "desktop", "embedded" -> Icons.Filled.DesktopWindows
    "android", "ios", "samsung" -> Icons.Filled.PhoneAndroid
    "ps4", "ps5" -> Icons.Filled.Tv
    else -> Icons.Filled.SportsEsports
}
