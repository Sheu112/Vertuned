package app.vertune.desktop.ui

import androidx.compose.foundation.DefaultContextMenuRepresentation
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

@Composable
fun App(nav: Navigator, @Suppress("UNUSED_PARAMETER") logo: Painter) {
    VertuneTheme {
        val palette = LocalPalette.current
        CompositionLocalProvider(
            LocalContextMenuRepresentation provides DefaultContextMenuRepresentation(
                backgroundColor = palette.surface,
                textColor = palette.text,
                itemHoverColor = palette.text.copy(alpha = 0.1f)
            )
        ) {
            Column(Modifier.fillMaxSize().background(palette.background0)) {
                Row(Modifier.weight(1f)) {
                    Rail(nav)
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Content(nav)
                        if (nav.current is Screen.Home) FloatingButton(
                            "search",
                            { nav.go(Screen.Search()) },
                            Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 24.dp)
                        )
                    }
                    if (nav.showQueue) QueuePanel(nav)
                }
                PlayerBar(nav)
            }
            nav.addToPlaylist?.let { track -> AddToPlaylistDialog(track) { nav.addToPlaylist = null } }
        }
    }
}

@Composable
private fun Content(nav: Navigator) {
    val holder = rememberSaveableStateHolder()
    val screen = nav.current
    holder.SaveableStateProvider(screen.toString()) {
        when (screen) {
            is Screen.Home -> when (screen.tab) {
                HomeTab.QuickPicks -> QuickPicksScreen(nav)
                HomeTab.Discover -> DiscoverScreen(nav)
                HomeTab.Songs -> SongsScreen(nav)
                HomeTab.Playlists -> PlaylistsScreen(nav)
            }
            is Screen.Search -> SearchScreen(screen.query, nav)
            is Screen.Album -> AlbumScreen(screen.browseId, nav)
            is Screen.Artist -> ArtistScreen(screen.browseId, nav)
            is Screen.Playlist -> PlaylistScreen(screen.browseId, nav)
            is Screen.LocalPlaylist -> LocalPlaylistScreen(screen.id, nav)
            is Screen.Mood -> MoodScreen(screen.title, screen.browseId, screen.params, nav)
            Screen.Favorites -> FavoritesScreen(nav)
            Screen.History -> HistoryScreen(nav)
            Screen.Top -> TopScreen(nav)
            Screen.Settings -> SettingsScreen()
        }
    }
}

/**
 * The phone app's landscape navigation rail: a back or settings button at the top, then the
 * home tabs written sideways. The selected tab is bright, the others are dimmed.
 */
@Composable
private fun Rail(nav: Navigator) {
    val palette = LocalPalette.current
    val screen = nav.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(RAIL_WIDTH).fillMaxHeight()
    ) {
        Box(Modifier.height(Dimens.headerHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (nav.canGoBack) AppIconButton("chevron_back", palette.text, nav::back)
            else AppIconButton(
                "settings",
                if (screen == Screen.Settings) palette.text else palette.textSecondary,
                { if (screen == Screen.Settings) nav.home(nav.homeTab) else nav.go(Screen.Settings) }
            )
        }
        HomeTab.entries.forEach { tab ->
            val selected = screen is Screen.Home && screen.tab == tab
            BasicText(
                tab.title,
                style = Type.s.medium.color(if (selected) palette.text else palette.textDisabled),
                maxLines = 1,
                modifier = Modifier
                    .clickable { nav.home(tab) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .sideways()
                    .padding(horizontal = 12.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        AppIconButton(
            "search",
            if (screen is Screen.Search) palette.text else palette.textSecondary,
            { if (screen !is Screen.Search) nav.go(Screen.Search()) },
            Modifier.padding(bottom = 16.dp)
        )
    }
}

/** Writes text bottom-to-top, taking up the rotated space (like the phone's vertical tabs). */
private fun Modifier.sideways() = this
    .layout { measurable, _ ->
        val p = measurable.measure(Constraints())
        layout(p.height, p.width) {
            p.place(x = -(p.width - p.height) / 2, y = (p.width - p.height) / 2)
        }
    }
    .rotate(-90f)

private val RAIL_WIDTH = 72.dp

