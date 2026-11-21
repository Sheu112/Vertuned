package app.vertune.desktop.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

/** Small settings, kept in the system's preferences store (~/Library/Preferences on a Mac, the registry on Windows). */
object Settings {
    private val prefs: Preferences = Preferences.userRoot().node("app/vertune/desktop")

    class Value<T>(
        private val key: String,
        default: T,
        private val read: Preferences.(String, T) -> T,
        private val write: Preferences.(String, T) -> Unit
    ) {
        private val state = MutableStateFlow(prefs.read(key, default))
        val flow: StateFlow<T> = state.asStateFlow()
        var value: T
            get() = state.value
            set(v) {
                state.value = v
                prefs.write(key, v)
                runCatching { prefs.flush() }
            }
    }

    private fun string(key: String, default: String = "") =
        Value(key, default, { k, d -> get(k, d) }, { k, v -> put(k, v) })

    private fun boolean(key: String, default: Boolean) =
        Value(key, default, { k, d -> getBoolean(k, d) }, { k, v -> putBoolean(k, v) })

    private fun long(key: String, default: Long) =
        Value(key, default, { k, d -> getLong(k, d) }, { k, v -> putLong(k, v) })

    private fun int(key: String, default: Int) =
        Value(key, default, { k, d -> getInt(k, d) }, { k, v -> putInt(k, v) })

    private inline fun <reified E : Enum<E>> enum(key: String, default: E) = Value(
        key, default,
        { k, d -> runCatching { enumValueOf<E>(get(k, d.name)) }.getOrDefault(d) },
        { k, v -> put(k, v.name) }
    )

    private fun float(key: String, default: Float) =
        Value(key, default, { k, d -> getFloat(k, d) }, { k, v -> putFloat(k, v) })

    val volume = float("volume", 0.8f)

    /** Song cache size in MB (0 = no limit). The phone's default is 2 GB. */
    val songCacheMb = long("songCache.mb", 2048L)

    /** Stops new songs being saved, like the phone's "Pause song cache". */
    val pauseSongCache = boolean("songCache.paused", false)

    val discordEnabled = boolean("discord.enabled", false)

    /** The Discord account token. Treat it like a password. */
    val discordToken = string("discord.token")

    /** Optional Discord application id, needed for album art in the presence. */
    val discordApplicationId = string("discord.applicationId")

    // ---- Appearance (same choices as the phone's Settings → Appearance)

    enum class ColorSource { Default, Dynamic }
    enum class ColorMode { System, Light, Dark }
    enum class Darkness { Normal, AMOLED, PureBlack }
    enum class ThumbnailRoundness(val dp: Float) { None(0f), Light(2f), Medium(8f), Heavy(12f), Heavier(16f), Heaviest(18f) }
    enum class FontFamily { Poppins, Roboto, Montserrat, Nunito, Rubik, System }
    enum class SeekBarStyle { Static, Wavy }
    enum class WavySeekBarQuality(val quality: Float) { Poor(50f), Low(25f), Medium(15f), High(5f), Great(1f), Subpixel(0.5f) }
    enum class PlayerLayout { Classic, New }
    enum class Reverb { None, SmallRoom, MediumRoom, LargeRoom, MediumHall, LargeHall, Plate }
    enum class QuickPicksSource { Trending, LastInteraction }

    val colorSource = enum("appearance.colorSource", ColorSource.Dynamic)
    val colorMode = enum("appearance.colorMode", ColorMode.Dark)
    val darkness = enum("appearance.darkness", Darkness.AMOLED)
    val thumbnailRoundness = enum("appearance.thumbnailRoundness", ThumbnailRoundness.Medium)
    val fontFamily = enum("appearance.fontFamily", FontFamily.Poppins)
    val applyFontPadding = boolean("appearance.applyFontPadding", false)
    /** "" follows the Mac's language; otherwise a language tag like "de" or "pt-BR". */
    val language = string("appearance.language")
    val hideExplicit = boolean("appearance.hideExplicit", false)
    val autoOpenPlayer = boolean("appearance.openPlayer", true)
    val maxThumbnailSize = int("appearance.maxThumbnailSize", 1920)

    // ---- Player

    val showPrevButtonCollapsed = boolean("player.prevCollapsed", false)
    val playerLayout = enum("player.layout", PlayerLayout.New)
    val showLike = boolean("player.showLike", false)
    val seekBarStyle = enum("player.seekBarStyle", SeekBarStyle.Wavy)
    val wavySeekBarQuality = enum("player.wavyQuality", WavySeekBarQuality.Great)
    val showRemaining = boolean("player.showRemaining", false)
    val showLyrics = boolean("player.showLyrics", false)
    val showSyncedLyrics = boolean("player.showSyncedLyrics", false)
    val showWordLyrics = boolean("player.showWordLyrics", true)
    /** Synced lyrics keep the current line in view (true) or can be scrolled freely (false). */
    val lyricsFollowLine = boolean("player.lyricsFollowLine", true)
    /** Lyrics text size in points (picked in the lyrics menu). */
    /** Extra lead for synced lyrics in ms, set from the lyrics menu (positive shows them earlier). */
    val lyricsTimingMs = long("lyricsTimingMs", 0L)
    /** How long each word takes to fade in, as a % of the time it's sung (0 = lights up at once). */
    val lyricsWordFade = int("lyricsWordFade", 100)
    val lyricsTextSize = int("player.lyricsTextSize", 16)
    val persistentQueue = boolean("player.persistentQueue", true)
    val skipOnError = boolean("player.skipOnError", false)
    val skipSilence = boolean("player.skipSilence", false)
    val minimumSilenceMs = long("player.minimumSilenceMs", 2000L)
    val loudnessNormalization = boolean("player.loudnessNormalization", false)
    val loudnessBaseGain = float("player.loudnessBaseGain", 5f)
    val bassBoost = boolean("player.bassBoost", false)
    /** 0…1, shown as 0–1000 like the phone. */
    val bassBoostAmount = float("player.bassBoostAmount", 0.5f)
    val reverb = enum("player.reverb", Reverb.None)
    val sponsorBlock = boolean("player.sponsorBlock", false)
    val savedQueue = string("player.savedQueue")
    val trackLoop = boolean("player.trackLoop", false)
    val queueLoop = boolean("player.queueLoop", true)
    val speed = float("player.speed", 1f)
    val pitch = float("player.pitch", 1f)

    // ---- Cache

    val imageCacheMb = long("cache.imageMb", 128L)

    // ---- Database

    val pausePlaybackHistory = boolean("database.pauseHistory", false)
    val pausePlaytime = boolean("database.pausePlaytime", false)

    // ---- Other

    val pauseSearchHistory = boolean("other.pauseSearchHistory", false)
    val topListLength = int("other.topListLength", 50)
    val quickPicksSource = enum("other.quickPicksSource", QuickPicksSource.Trending)
    val quickPicksCache = boolean("other.quickPicksCache", true)
    val cachedQuickPicks = string("other.cachedQuickPicks")

    // ---- UI state

    val playlistsAsGrid = boolean("ui.playlistsAsGrid", true)

    /** Home tabs hidden from the rail (long-press a tab to change), as comma-separated names. */
    val hiddenHomeTabs = string("ui.hiddenHomeTabs")
}
