package app.vertune.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed interface Screen {
    data object Home : Screen
    data class Search(val query: String = "") : Screen
    data class Album(val browseId: String) : Screen
    data class Artist(val browseId: String) : Screen
    data class Playlist(val browseId: String) : Screen
    data object Favorites : Screen
    data object History : Screen
    data object Top : Screen
    data object Settings : Screen
}

/** A simple back stack, like the phone app's navigation. */
class Navigator {
    private val stack = mutableStateListOf<Screen>(Screen.Home)

    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    var showQueue by mutableStateOf(false)

    fun go(screen: Screen) {
        if (stack.last() != screen) stack.add(screen)
    }

    /** Opens a top-level section from the sidebar. */
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
