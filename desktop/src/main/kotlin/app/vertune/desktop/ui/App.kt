package app.vertune.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.DefaultContextMenuRepresentation
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.vertune.desktop.player.PlayerController

@Composable
fun App(nav: Navigator, @Suppress("UNUSED_PARAMETER") logo: Painter) {
    LaunchedEffect(Unit) { PlayerController.openPlayerRequests.collect { nav.showPlayer = true } }
    VertuneTheme {
        val palette = LocalPalette.current
        CompositionLocalProvider(
            LocalContextMenuRepresentation provides DefaultContextMenuRepresentation(
                backgroundColor = palette.surface,
                textColor = palette.text,
                itemHoverColor = palette.text.copy(alpha = 0.1f)
            )
        ) {
          Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().background(palette.background0)) {
                Row(Modifier.weight(1f)) {
                    Rail(nav)
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Content(nav)
                    }
                    if (nav.showQueue) CompositionLocalProvider(LocalPalette provides palette.forPlayer()) { QueuePanel(nav) }
                }
                CompositionLocalProvider(LocalPalette provides palette.forPlayer()) { PlayerBar(nav) }
            }
            AnimatedVisibility(
                visible = nav.showPlayer && PlayerController.current.collectAsState().value != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) { NowPlaying(nav) }
            nav.addToPlaylist?.let { track -> AddToPlaylistDialog(track) { nav.addToPlaylist = null } }
          }
        }
    }
}

/** What the content area shows: a screen, plus the settings tab when Settings is open. */
private data class Route(val screen: Screen, val settingsTab: SettingsTab?)

/**
 * The page area, animated like the phone: switching tabs slides the page up or down, opening a
 * page fades it in while the old one shrinks away, and going back grows the old page as it fades.
 */
@Composable
private fun Content(nav: Navigator) {
    val holder = rememberSaveableStateHolder()
    val current = nav.current
    val route = Route(
        screen = if (current is Screen.Search) Screen.Search() else current,
        settingsTab = if (current == Screen.Settings) nav.settingsTab else null
    )
    AnimatedContent(
        targetState = route,
        transitionSpec = {
            val from = initialState
            val to = targetState
            val tabOrder = when {
                from.screen is Screen.Home && to.screen is Screen.Home -> from.screen.tab.ordinal to to.screen.tab.ordinal
                from.settingsTab != null && to.settingsTab != null -> from.settingsTab.ordinal to to.settingsTab.ordinal
                else -> null
            }
            if (tabOrder != null) {
                val direction = if (tabOrder.second > tabOrder.first) SlideDirection.Up else SlideDirection.Down
                val spec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow, visibilityThreshold = IntOffset.VisibilityThreshold)
                ContentTransform(
                    targetContentEnter = slideIntoContainer(direction, spec),
                    initialContentExit = slideOutOfContainer(direction, spec),
                    sizeTransform = null
                )
            } else if (nav.popping) {
                ContentTransform(
                    targetContentEnter = fadeIn(tween(ROUTE_MS)),
                    initialContentExit = scaleOut(tween(ROUTE_MS), targetScale = 1.1f) + fadeOut(tween(ROUTE_MS)),
                    targetContentZIndex = -1f,
                    sizeTransform = null
                )
            } else {
                ContentTransform(
                    targetContentEnter = fadeIn(tween(ROUTE_MS)),
                    initialContentExit = scaleOut(tween(ROUTE_MS), targetScale = 0.9f) + fadeOut(tween(ROUTE_MS)),
                    sizeTransform = null
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { r ->
        holder.SaveableStateProvider(r.toString()) {
            Box(Modifier.fillMaxSize().background(LocalPalette.current.background0)) {
                when (val screen = r.screen) {
                    is Screen.Home -> when (screen.tab) {
                        HomeTab.QuickPicks -> QuickPicksScreen(nav)
                        HomeTab.Discover -> DiscoverScreen(nav)
                        HomeTab.Songs -> SongsScreen(nav)
                        HomeTab.Playlists -> PlaylistsScreen(nav)
                        HomeTab.Artists -> ArtistsScreen(nav)
                        HomeTab.Albums -> AlbumsScreen(nav)
                    }
                    is Screen.Search -> SearchScreen((nav.current as? Screen.Search)?.query.orEmpty(), nav)
                    is Screen.Album -> AlbumScreen(screen.browseId, nav)
                    is Screen.Artist -> ArtistScreen(screen.browseId, nav)
                    is Screen.Playlist -> PlaylistScreen(screen.browseId, nav)
                    is Screen.LocalPlaylist -> LocalPlaylistScreen(screen.id, nav)
                    is Screen.Mood -> MoodScreen(screen.title, screen.browseId, screen.params, nav)
                    Screen.Favorites -> FavoritesScreen(nav)
                    Screen.History -> HistoryScreen(nav)
                    Screen.Top -> TopScreen(nav)
                    Screen.Offline -> OfflineScreen(nav)
                    Screen.Settings -> SettingsScreen(r.settingsTab ?: SettingsTab.Appearance)
                }
            }
        }
    }
}

private class RailTab(val title: String, val icon: String)

/**
 * The phone app's navigation rail: a back or settings button at the top, then the tabs written
 * sideways. The selected tab is bright and its icon slides in from the edge; the icon of the tab
 * you leave slides back out. While Settings is open, the rail shows the settings tabs instead.
 */
@Composable
private fun Rail(nav: Navigator) {
    val palette = LocalPalette.current
    val screen = nav.current
    val inSettings = screen == Screen.Settings

    val tabs = if (inSettings) SettingsTab.entries.map { RailTab(str(it.key, it.fallback), it.icon) }
    else HomeTab.entries.map { RailTab(str(it.key, it.title), it.icon) }
    val selected = when {
        inSettings -> nav.settingsTab.ordinal
        screen is Screen.Home -> screen.tab.ordinal
        else -> -1
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(RAIL_WIDTH).fillMaxHeight()
    ) {
        Box(Modifier.height(Dimens.headerHeight).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val back = nav.canGoBack
            AppIcon(
                if (back) "chevron_back" else "settings",
                palette.textSecondary,
                modifier = Modifier
                    .offset(x = ICON_OFFSET, y = 48.dp)
                    .clip(CircleShape)
                    .clickable { if (back) nav.back() else nav.go(Screen.Settings) }
                    .padding(12.dp),
                size = 22.dp
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
        ) {
            val transition = updateTransition(selected)
            tabs.forEachIndexed { index, tab ->
                val dotAlpha by transition.animateFloat { if (it == index) 1f else 0f }
                val textColor by transition.animateColor { if (it == index) palette.text else palette.textDisabled }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .clickable {
                            if (inSettings) nav.settingsTab = SettingsTab.entries[index]
                            else nav.home(HomeTab.entries[index])
                        }
                        .padding(horizontal = 8.dp)
                ) {
                    AppIcon(
                        tab.icon,
                        palette.text,
                        modifier = Modifier
                            .sideways()
                            .graphicsLayer {
                                alpha = dotAlpha
                                translationX = (1f - dotAlpha) * -48.dp.toPx()
                                rotationZ = -90f
                            },
                        size = ICON_OFFSET * 2
                    )
                    BasicText(
                        tab.title,
                        style = Type.xs.semiBold.color(textColor).copy(textAlign = TextAlign.Center),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .sideways()
                            .rotate(-90f)
                            .padding(horizontal = 16.dp)
                    )
                }
            }
        }
        AppIconButton(
            "search",
            if (screen is Screen.Search) palette.text else palette.textSecondary,
            { if (screen !is Screen.Search) nav.go(Screen.Search()) },
            Modifier.padding(bottom = 16.dp)
        )
    }
}

/** Lays a child out turned on its side: its width and height swap (the phone's Modifier.vertical). */
private fun Modifier.sideways() = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(maxWidth = Constraints.Infinity, maxHeight = Constraints.Infinity))
    layout(p.height, p.width) {
        p.place(x = -(p.width / 2 - p.height / 2), y = -(p.height / 2 - p.width / 2))
    }
}

private val RAIL_WIDTH = 72.dp
private val ICON_OFFSET = 6.dp
private const val ROUTE_MS = 300
