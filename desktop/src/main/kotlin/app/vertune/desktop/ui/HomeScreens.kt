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
import androidx.compose.runtime.Composable
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
        modifier = Modifier.fillMaxWidth().height(4 * 70.dp)
    ) {
        items(tracks.size) { i ->
            Box(Modifier.width(380.dp)) { SongItem(tracks[i], nav, queue = tracks, index = i) }
        }
    }
}

// ---------------------------------------------------------------- Quick picks

@Composable
fun QuickPicksScreen(nav: Navigator) {
    val related = rememberLoad(Unit) {
        val seed = Library.top(1).firstOrNull()?.track ?: Library.history(1).firstOrNull()
        val trending = if (seed == null) Innertube.discoverPage()?.getOrNull()?.trending?.songs?.firstOrNull()?.toTrack() else null
        val id = seed?.id ?: trending?.id ?: return@rememberLoad Innertube.RelatedPage()
        Innertube.relatedPage(NextBody(videoId = id))?.getOrNull() ?: Innertube.RelatedPage()
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
        item { Header("Quick picks") }
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
        item { Header("Discover") }
        item {
            discover.Show { page ->
                Column(pagePadding) {
                    if (page.moods.isNotEmpty()) {
                        SectionTitle("Moods and genres", Modifier.padding(start = 8.dp))
                        LazyHorizontalGrid(
                            rows = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxWidth().height(4 * (64 + 8).dp)
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

// ---------------------------------------------------------------- Songs

@Composable
fun SongsScreen(nav: Navigator) {
    val version by Library.version.collectAsState()
    val songs by produceState<List<app.vertune.desktop.data.Track>?>(null, version) {
        value = withContext(Dispatchers.IO) { runCatching { Library.songs() }.getOrDefault(emptyList()) }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            Header("Songs") {
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
    val version by Library.version.collectAsState()
    val playlists by produceState(emptyList<Library.PlaylistPreview>(), version, sort, descending) {
        value = withContext(Dispatchers.IO) { runCatching { Library.playlists(sort, descending) }.getOrDefault(emptyList()) }
    }

    if (creating) TextFieldDialog(
        title = "New playlist",
        hint = "Enter the playlist name",
        onDismiss = { creating = false },
        onDone = { name -> Library.createPlaylist(name) }
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(Dimens.albumThumbnail + Dimens.alternativePadding * 2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Header("Playlists", Modifier.padding(horizontal = 0.dp)) {
                SecondaryTextButton("New playlist", { creating = true })
                Box(Modifier.weight(1f))
                HeaderIconButton("medical", { sort = Library.PlaylistSort.SongCount }, sort == Library.PlaylistSort.SongCount)
                HeaderIconButton("text", { sort = Library.PlaylistSort.Name }, sort == Library.PlaylistSort.Name)
                HeaderIconButton("time", { sort = Library.PlaylistSort.DateAdded }, sort == Library.PlaylistSort.DateAdded)
                AppIconButton(
                    "arrow_up",
                    palette.text,
                    { descending = !descending },
                    Modifier.rotate(if (descending) 180f else 0f),
                    size = 18.dp
                )
            }
        }
        item { IconTile("heart", palette.red, "Favorites") { nav.go(Screen.Favorites) } }
        item { IconTile("trending", palette.red, "My top 50") { nav.go(Screen.Top) } }
        item { IconTile("history", palette.textDisabled, "History") { nav.go(Screen.History) } }
        items(playlists, key = { it.id }) { p ->
            PlaylistTile(p.name, p.songCount, p.thumbnails) { nav.go(Screen.LocalPlaylist(p.id)) }
        }
    }
}
