package com.my.kizzy.data.rpc

import com.my.kizzy.domain.model.Game

/**
 * The console / PC launchers that Console RPC can follow automatically.
 */
enum class ConsolePlatform(val displayName: String) {
    /** DroidDeck - Steam/PC games running in an ARM64 Linux container. */
    DROIDDECK("DroidDeck"),

    /** Eden / Ruzu - Yuzu-based Nintendo Switch emulators. */
    EDEN("Eden (Switch)"),
}

/**
 * A resolved console launcher package.
 *
 * @param spoofed true when the package is a benchmark/game package that the
 *        APK uses to bypass thermal throttling or to be whitelisted for a GPU
 *        driver. Those packages collide with real games, so they are only
 *        resolved when the user enables spoofed-build detection.
 */
data class ConsoleAppInfo(
    val platform: ConsolePlatform,
    val packageName: String,
    val spoofed: Boolean,
)

/**
 * Package names used by DroidDeck and the Eden/Ruzu Switch emulators.
 *
 * Both projects ship several APKs, and some of them use the package name of a
 * benchmark or a popular game. A plain PackageManager lookup therefore shows
 * "AnTuTu Benchmark" or "Genshin Impact" instead of the launcher, and the
 * normal game detection never sees DroidDeck/Eden at all.
 */
object ConsoleAppRegistry {

    /** Builds that can be mapped without any risk of a collision. */
    private val standard: Map<String, ConsolePlatform> = mapOf(
        // DroidDeck
        "com.droiddeck.launcher" to ConsolePlatform.DROIDDECK,
        // Eden - standard, ChromeOS, legacy and nightly builds
        "dev.eden.eden_emulator" to ConsolePlatform.EDEN,
        "dev.legacy.eden_emulator" to ConsolePlatform.EDEN,
        "dev.eden.eden_nightly" to ConsolePlatform.EDEN,
        // Play Store / Ruzu variants
        "com.myeden.edenemulator" to ConsolePlatform.EDEN,
        "org.ruzu.ruzu_emu" to ConsolePlatform.EDEN,
    )

    /**
     * Spoofed builds. These names belong to real apps too, so they are only
     * mapped when the user explicitly opts in.
     */
    private val spoofed: Map<String, ConsolePlatform> = mapOf(
        "com.tencent.ig" to ConsolePlatform.DROIDDECK,
        "com.antutu.benchmark.full" to ConsolePlatform.DROIDDECK,
        "com.ludashi.benchmark" to ConsolePlatform.DROIDDECK,
        "com.miHoYo.Yuanshen" to ConsolePlatform.EDEN,
    )

    /** Every package that may belong to DroidDeck or Eden. */
    val allPackages: Set<String> by lazy { standard.keys + spoofed.keys }

    /**
     * Resolves [packageName] to a console launcher.
     *
     * @param includeSpoofed whether the benchmark/game package names should
     *        also be mapped. Off by default because the same package can be a
     *        real game the user plays outside of the launcher.
     */
    fun resolve(packageName: String?, includeSpoofed: Boolean): ConsoleAppInfo? {
        val pkg = packageName?.takeIf { it.isNotBlank() } ?: return null
        standard[pkg]?.let { return ConsoleAppInfo(it, pkg, spoofed = false) }
        if (!includeSpoofed) return null
        spoofed[pkg]?.let { return ConsoleAppInfo(it, pkg, spoofed = true) }
        return null
    }

    fun isKnown(packageName: String?, includeSpoofed: Boolean): Boolean =
        resolve(packageName, includeSpoofed) != null
}

/**
 * The game catalog loaded by the Console RPC screen. The service reuses it to
 * attach the correct cover art to an automatically detected game without
 * asking the network again.
 */
object ConsoleGameCatalog {

    @Volatile
    private var games: List<Game> = emptyList()

    fun update(newGames: List<Game>) {
        games = newGames
    }

    fun size(): Int = games.size

    /**
     * Finds the catalog entry for a game title reported by DroidDeck/Eden.
     *
     * The notification title is often shortened or carries a suffix
     * ("Hollow Knight: Voidheart Edition"), so both directions of `contains`
     * are accepted and the closest (longest) match wins.
     */
    fun match(title: String?): Game? {
        val needle = title?.trim()?.lowercase() ?: return null
        if (needle.length < 3) return null
        return games
            .mapNotNull { game ->
                val haystack = game.game_title.trim().lowercase()
                if (haystack.length < 3) return@mapNotNull null
                val score = when {
                    haystack == needle -> 1000
                    haystack.contains(needle) -> 500 + needle.length
                    needle.contains(haystack) -> 400 + haystack.length
                    else -> 0
                }
                if (score > 0) game to score else null
            }
            .maxByOrNull { it.second }
            ?.first
    }
}

/** The game that DroidDeck/Eden is currently running, as seen in its notification. */
data class ActiveGame(
    val packageName: String,
    val title: String,
    val updatedAt: Long,
)

/**
 * Holds the game title parsed from the last DroidDeck/Eden notification.
 *
 * The notification listener and the Console RPC service run in the same
 * process, so a small in-memory holder is enough. Entries are keyed by package
 * so a notification from a launcher does not leak into a different one, and
 * they expire so a game that was closed hours ago is not reported.
 */
object ActiveGameTracker {

    private const val MAX_AGE_MS = 15L * 60L * 1000L

    @Volatile
    private var entry: ActiveGame? = null

    fun update(packageName: String, title: String?) {
        if (packageName.isBlank()) return
        val cleaned = title?.trim()?.takeIf { it.isNotEmpty() }
        // A generic update (for example an ongoing "DroidDeck is running"
        // notification) must not wipe a game title that is already known.
        // The title is cleared when the notification is removed instead.
        if (cleaned == null) return
        entry = ActiveGame(packageName, cleaned, System.currentTimeMillis())
    }

    fun clearIfPackage(packageName: String) {
        if (entry?.packageName == packageName) entry = null
    }

    fun clear() {
        entry = null
    }

    /** The package of the most recent game notification, if still fresh. */
    fun currentPackage(now: Long = System.currentTimeMillis()): String? {
        val current = entry ?: return null
        if (now - current.updatedAt > MAX_AGE_MS) return null
        return current.packageName
    }

    fun titleFor(packageName: String, now: Long = System.currentTimeMillis()): String? {
        val current = entry ?: return null
        if (current.packageName != packageName) return null
        if (now - current.updatedAt > MAX_AGE_MS) return null
        return current.title
    }
}

/**
 * Turns the title/text of a launcher notification into a game title.
 *
 * DroidDeck and Eden do not agree on where the game name is placed: sometimes
 * it is the title, sometimes the text, and sometimes the text is prefixed with
 * "Now playing". Everything that is clearly the launcher itself is ignored.
 */
object ConsoleNotificationParser {

    private val generic = setOf(
        "droiddeck", "droid deck", "droiddeck launcher",
        "eden", "eden emulator", "eden (switch)", "ruzu", "ruzu emu",
        "in game", "now playing", "playing", "running", "game running",
        "tap to return to the game", "tap to return",
    )

    private val prefixes = listOf(
        "now playing:", "now playing", "playing:", "playing",
        "running:", "running",
    )

    private val suffixes = listOf(
        " is running", " is playing", " - running",
    )

    /**
     * Picks the most plausible game title from the notification fields.
     * Returns null when the notification only talks about the launcher itself.
     */
    fun pickTitle(
        title: String?,
        text: String?,
        bigText: String?,
        subText: String?,
    ): String? {
        // The title is usually the game in Android notifications, but some
        // launchers put their own name there and the game in the text. Try the
        // title first, then the longer text fields.
        val candidates = listOf(title, bigText, text, subText)
        for (candidate in candidates) {
            clean(candidate)?.let { return it }
        }
        return null
    }

    private fun clean(raw: String?): String? {
        var value = raw?.trim()?.replace(Regex("\\s+"), " ") ?: return null
        if (value.isEmpty()) return null

        val lower = value.lowercase()
        if (lower in generic) return null

        for (prefix in prefixes) {
            if (lower.startsWith(prefix)) {
                value = value.substring(prefix.length)
                    .trimStart(':', ' ', '-', '–', '—')
                    .trim()
                break
            }
        }

        for (suffix in suffixes) {
            if (value.lowercase().endsWith(suffix)) {
                value = value.dropLast(suffix.length).trim()
                break
            }
        }

        if (value.isEmpty()) return null
        if (value.lowercase() in generic) return null
        if (value.length < 2) return null
        // A version number or a single word like "Ready" is not a game title.
        if (value.all { it.isDigit() || it == '.' }) return null
        return value
    }
}
