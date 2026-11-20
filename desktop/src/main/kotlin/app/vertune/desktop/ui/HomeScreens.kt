package app.vertune.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import app.vertune.desktop.data.Settings
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import app.vertune.desktop.data.Library
import app.vertune.desktop.data.toTrack
import app.vertune.desktop.player.PlayerController
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.bodies.BrowseBody
import app.vitune.providers.innertube.models.bodies.NextBody
import app.vitune.providers.innertube.requests.browse
import app.vitune.providers.innertube.requests.discoverPage
import app.vitune.providers.innertube.requests.relatedPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

val pagePadding = Modifier.padding(horizontal = 16.dp)

@Composable
fun AlbumTile(album: Innertube.AlbumItem, nav: Navigator) = ItemTile(
    title = album.info?.name.orEmpty(),
    subtitle = album.authors?.joinToString("") { it.name.orEmpty() }?.ifBlank { null } ?: album.year,
    image = album.thumbnail?.size(300)
) { album.info?.endpoint?.browseId?.let { nav.go(Screen.Album(it)) } }

@Composable
fun ArtistTile(artist: Innertube.ArtistItem, nav: Navigator) = ItemTile(
    title = artist.info?.name.orEmpty(),
    subtitle = artist.subscribersCountText,
    image = artist.thumbnail?.size(300),
    round = true,
    size = Dimens.artistThumbnail
) { artist.info?.endpoint?.browseId?.let { nav.go(Screen.Artist(it)) } }

@Composable
fun YtPlaylistTile(playlist: Innertube.PlaylistItem, nav: Navigator) = ItemTile(
    title = playlist.info?.name.orEmpty(),
    subtitle = playlist.channel?.name ?: playlist.songCount?.let { "$it songs" },
    image = playlist.thumbnail?.size(300)
) { playlist.info?.endpoint?.browseId?.let { nav.go(Screen.Playlist(it)) } }

/** Songs laid out in a 4-row horizontal grid, as on the phone's Quick picks and Discover tabs. */
@Composable
private fun SongGrid(tracks: List<app.vertune.desktop.data.Track>, nav: Navigator) {
    LazyHorizontalGrid(
        rows = GridCells.Fixed(4),
        modifier = Modifier.fillMaxWidth().height(70.dp * 4)
    ) {
        items(tracks.size) { i ->
            Box(Modifier.width(380.dp)) { SongItem(tracks[i], nav, queue = tracks, index = i) }
        }
    }
}

// ---------------------------------------------------------------- Quick picks

@Composable
fun QuickPicksScreen(nav: Navigator) {
    val source by Settings.quickPicksSource.flow.collectAsState()
    val related = rememberLoad(source) { withContext(Dispatchers.IO) { loadQuickPicks(source) } }
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
        item { Header(str("quick_picks", "Quick picks")) }
        item {
            related.Show { r ->
                Column(pagePadding) {
                    val songs = r.songs?.mapNotNull { it.toTrack() }.orEmpty()
                    if (songs.isNotEmpty()) SongGrid(songs, nav)
                    r.albums?.takeIf { it.isNotEmpty() }?.let { albums ->
                        SectionTitle("Related albums", Modifier.padding(start = 8.dp))
                        TileRow(albums) { AlbumTile(it, nav) }
                    }
                    r.artists?.takeIf { it.isNotEmpty() }?.let { artists ->
                        SectionTitle("Similar artists", Modifier.padding(start = 8.dp))
                        TileRow(artists) { ArtistTile(it, nav) }
                    }
                    r.playlists?.takeIf { it.isNotEmpty() }?.let { playlists ->
                        SectionTitle("Recommended playlists", Modifier.padding(start = 8.dp))
                        TileRow(playlists) { YtPlaylistTile(it, nav) }
                    }
                    if (songs.isEmpty() && r.albums.isNullOrEmpty()) Message("Play some music and your quick picks show up here.")
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Discover

@Composable
fun DiscoverScreen(nav: Navigator) {
    val discover = rememberLoad(Unit) { Innertube.discoverPage()?.getOrNull() }
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
        item { Header(str("discover", "Discover")) }
        item {
            discover.Show { page ->
                Column(pagePadding) {
                    if (page.moods.isNotEmpty()) {
                        SectionTitle("Moods and genres", Modifier.padding(start = 8.dp))
                        LazyHorizontalGrid(
                            rows = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxWidth().height(72.dp * 4)
                        ) {
                            items(page.moods.sortedBy { it.title }) { mood ->
                                MoodItem(mood) {
                                    mood.endpoint.browseId?.let {
                                        nav.go(Screen.Mood(mood.title, it, mood.endpoint.params))
                                    }
                                }
                            }
                        }
                    }
                    if (page.newReleaseAlbums.isNotEmpty()) {
                        SectionTitle("New released albums", Modifier.padding(start = 8.dp))
                        TileRow(page.newReleaseAlbums) { AlbumTile(it, nav) }
                    }
                    val trending = page.trending.songs.mapNotNull { it.toTrack() }
                    if (trending.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle("Trending", Modifier.padding(start = 8.dp).weight(1f))
                            page.trending.endpoint?.browseId?.let { id ->
                                SecondaryTextButton("More", { nav.go(Screen.Playlist(id)) }, Modifier.padding(top = 16.dp))
                            }
                        }
                        SongGrid(trending, nav)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodItem(mood: Innertube.Mood.Item, onClick: () -> Unit) {
    val color = Color(mood.stripeColor)
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .padding(4.dp)
            .width(220.dp)
            .height(64.dp)
            .clip(Dimens.thumbnailShape)
            .background(color)
            .clickable(onClick = onClick)
    ) {
        BasicText(
            mood.title,
            style = Type.xs.semiBold.color(if (color.luminance() >= 0.5f) Color.Black else Color.White),
            modifier = Modifier.padding(start = 24.dp)
        )
    }
}

/** A mood or genre page: rows of playlists and albums. */
@Composable
fun MoodScreen(title: String, browseId: String, params: String?, nav: Navigator) {
    val page = rememberLoad(browseId, params) { Innertube.browse(BrowseBody(browseId = browseId, params = params))?.getOrNull() }
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
        item { Header(title) }
        item {
            page.Show { result ->
                Column(pagePadding) {
                    result.items.forEach { section ->
                        if (section.items.isEmpty()) return@forEach
                        section.title?.let { SectionTitle(it, Modifier.padding(start = 8.dp)) }
                        TileRow(section.items) { item ->
                            when (item) {
                                is Innertube.AlbumItem -> AlbumTile(item, nav)
                                is Innertube.ArtistItem -> ArtistTile(item, nav)
                                is Innertube.PlaylistItem -> YtPlaylistTile(item, nav)
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}

private val quickPicksJson = Json { ignoreUnknownKeys = true }

/**
 * The phone's quick picks: songs related to your most played song of the past week ("Trending")
 * or the last song you played ("Last interaction"). With "Cache quick picks" on, the last result
 * is saved so it shows up even offline.
 */
private suspend fun loadQuickPicks(source: Settings.QuickPicksSource): Innertube.RelatedPage {
    val weekAgo = System.currentTimeMillis() - WEEK_MS
    val seed = runCatching {
        when (source) {
            Settings.QuickPicksSource.Trending -> Library.top(1, weekAgo).firstOrNull()?.track ?: Library.top(1).firstOrNull()?.track
            Settings.QuickPicksSource.LastInteraction -> Library.history(1).firstOrNull()
        } ?: Library.history(1).firstOrNull()
    }.getOrNull()
    val id = seed?.id ?: Innertube.discoverPage()?.getOrNull()?.trending?.songs?.firstOrNull()?.toTrack()?.id
    val page = id?.let { Innertube.relatedPage(NextBody(videoId = it))?.getOrNull() }
    val file = app.vertune.desktop.Paths.quickPicksFile
    if (page != null) {
        if (Settings.quickPicksCache.value) runCatching { file.writeText(quickPicksJson.encodeToString(Innertube.RelatedPage.serializer(), page)) }
        return page
    }
    if (Settings.quickPicksCache.value && file.exists()) {
        runCatching { return quickPicksJson.decodeFromString(Innertube.RelatedPage.serializer(), file.readText()) }
    }
    return Innertube.RelatedPage()
}

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

// ---------------------------------------------------------------- Songs

@Composable
fun SongsScreen(nav: Navigator) {
    val version by Library.version.collectAsState()
    val songs by produceState<List<app.vertune.desktop.data.Track>?>(null, version) {
        value = withContext(Dispatchers.IO) { runCatching { Library.songs() }.getOrDefault(emptyList()) }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            Header(str("songs", "Songs")) {
                SecondaryTextButton("Shuffle", { songs?.takeIf { it.isNotEmpty() }?.let { PlayerController.play(it.shuffled()) } })
            }
        }
        val list = songs
        when {
            list == null -> item { Loading() }
            list.isEmpty() -> item { Message("Songs you play, like or add to playlists show up here.") }
            else -> itemsIndexed(list) { i, t -> SongItem(t, nav, queue = list, index = i, modifier = pagePadding) }
        }
    }
}

// ---------------------------------------------------------------- Playlists

@Composable
fun PlaylistsScreen(nav: Navigator) {
    val palette = LocalPalette.current
    var sort by remember { mutableStateOf(Library.PlaylistSort.DateAdded) }
    var descending by remember { mutableStateOf(true) }
    var creating by remember { mutableStateOf(false) }
    val asGrid by Settings.playlistsAsGrid.flow.collectAsState()
    val topLength by Settings.topListLength.flow.collectAsState()
    val version by Library.version.collectAsState()
    val playlists by produceState(emptyList<Library.PlaylistPreview>(), version, sort, descending) {
        value = withContext(Dispatchers.IO) { runCatching { Library.playlists(sort, descending) }.getOrDefault(emptyList()) }
    }
    val arrowRotation by animateFloatAsState(if (descending) 180f else 0f)

    if (creating) TextFieldDialog(
        title = str("new_playlist", "New playlist"),
        hint = str("enter_playlist_name_prompt", "Enter the playlist name"),
        onDismiss = { creating = false },
        onDone = { name -> Library.createPlaylist(name) }
    )

    LazyVerticalGrid(
        columns = if (asGrid) GridCells.Adaptive(PLAYLIST_TILE_MIN) else GridCells.Fixed(1),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Header(str("playlists", "Playlists"), Modifier.padding(horizontal = 0.dp)) {
                SecondaryTextButton(str("new_playlist", "New playlist"), { creating = true })
                Box(Modifier.weight(1f))
                HeaderIconButton(if (asGrid) "grid" else "list", { Settings.playlistsAsGrid.value = !asGrid })
                Box(Modifier.width(1.dp).height(8.dp).background(palette.textDisabled))
                HeaderIconButton("medical", { sort = Library.PlaylistSort.SongCount }, sort == Library.PlaylistSort.SongCount)
                HeaderIconButton("text", { sort = Library.PlaylistSort.Name }, sort == Library.PlaylistSort.Name)
                HeaderIconButton("time", { sort = Library.PlaylistSort.DateAdded }, sort == Library.PlaylistSort.DateAdded)
                AppIconButton(
                    "arrow_up",
                    palette.text,
                    { descending = !descending },
                    Modifier.rotate(arrowRotation),
                    size = 18.dp
                )
            }
        }
        item(key = "favorites") { IconTile("heart", palette.red, str("favorites", "Favorites"), asGrid) { nav.go(Screen.Favorites) } }
        item(key = "offline") { IconTile("airplane", palette.blue, str("offline", "Offline"), asGrid) { nav.go(Screen.Offline) } }
        item(key = "top") {
            IconTile("trending", palette.red, str("format_my_top_playlist", "My top $topLength", topLength), asGrid) { nav.go(Screen.Top) }
        }
        item(key = "history") { IconTile("history", palette.textDisabled, str("history", "History"), asGrid) { nav.go(Screen.History) } }
        items(playlists, key = { it.id }) { p ->
            PlaylistTile(p.name, p.songCount, p.thumbnails, asGrid) { nav.go(Screen.LocalPlaylist(p.id)) }
        }
    }
}

private val PLAYLIST_TILE_MIN = 190.dp

// ---------------------------------------------------------------- Artists and albums (bookmarked)

@Composable
private fun rememberBookmarks(kind: String): List<Library.Bookmark>? {
    val version by Library.version.collectAsState()
    val list by produceState<List<Library.Bookmark>?>(null, version) {
        value = withContext(Dispatchers.IO) { runCatching { Library.bookmarks(kind) }.getOrDefault(emptyList()) }
    }
    return list
}

/** The phone's Artists tab: the artists you've bookmarked, as round pictures. */
@Composable
fun ArtistsScreen(nav: Navigator) {
    val artists = rememberBookmarks(Library.BOOKMARK_ARTIST)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(Dimens.artistThumbnail + Dimens.alternativePadding * 2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Header(str("artists", "Artists"), Modifier.padding(horizontal = 0.dp)) }
        when {
            artists == null -> item(span = { GridItemSpan(maxLineSpan) }) { Loading() }
            artists.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                Message("Bookmark artists with the bookmark button on their page and they show up here.")
            }
            else -> items(artists, key = { it.id }) { a ->
                ItemTile(a.title.orEmpty(), a.subtitle, a.thumbnail, round = true, size = Dimens.artistThumbnail) {
                    nav.go(Screen.Artist(a.id))
                }
            }
        }
    }
}

/** The phone's Albums tab: the albums you've bookmarked. */
@Composable
fun AlbumsScreen(nav: Navigator) {
    val albums = rememberBookmarks(Library.BOOKMARK_ALBUM)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(Dimens.albumThumbnail + Dimens.alternativePadding * 2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Header(str("albums", "Albums"), Modifier.padding(horizontal = 0.dp)) }
        when {
            albums == null -> item(span = { GridItemSpan(maxLineSpan) }) { Loading() }
            albums.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                Message("Bookmark albums with the bookmark button on their page and they show up here.")
            }
            else -> items(albums, key = { it.id }) { a ->
                ItemTile(a.title.orEmpty(), a.subtitle, a.thumbnail) { nav.go(Screen.Album(a.id)) }
            }
        }
    }
}
