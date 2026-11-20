package app.vertune.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.vertune.desktop.data.Track

/** The phone app's home tabs, shown on the vertical rail. */
enum class HomeTab(val title: String, val icon: String) {
    QuickPicks("Quick picks", "sparkles"),
    Discover("Discover", "globe"),
    Songs("Songs", "musical_notes"),
    Playlists("Playlists", "playlist")
}

sealed interface Screen {
    data class Home(val tab: HomeTab) : Screen
    data class Search(val query: String = "") : Screen
    data class Album(val browseId: String) : Screen
    data class Artist(val browseId: String) : Screen
    data class Playlist(val browseId: String) : Screen
    data class LocalPlaylist(val id: Long) : Screen
    data class Mood(val title: String, val browseId: String, val params: String?) : Screen
    data object Favorites : Screen
    data object History : Screen
    data object Top : Screen
    data object Offline : Screen
    data object Settings : Screen
}

/** A simple back stack, like the phone app's navigation. */
class Navigator {
    private val stack = mutableStateListOf<Screen>(Screen.Home(HomeTab.QuickPicks))

    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    /** The home tab that's selected on the rail (the last one opened). */
    var homeTab by mutableStateOf(HomeTab.QuickPicks)
        private set

    var showQueue by mutableStateOf(false)

    /** The full "now playing" screen, opened from the player bar. */
    var showPlayer by mutableStateOf(false)

    /** When set, the "Add to playlist" picker is shown for this song. */
    var addToPlaylist by mutableStateOf<Track?>(null)

    fun go(screen: Screen) {
        if (stack.last() != screen) stack.add(screen)
    }

    fun home(tab: HomeTab) {
        homeTab = tab
        stack.clear()
        stack.add(Screen.Home(tab))
    }

    fun root(screen: Screen) {
        stack.clear()
        stack.add(screen)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun replace(screen: Screen) {
        stack[stack.lastIndex] = screen
    }
}
