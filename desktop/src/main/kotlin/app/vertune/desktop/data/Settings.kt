package app.vertune.desktop.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

/** Small settings, kept in the macOS preferences system (~/Library/Preferences). */
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
}
