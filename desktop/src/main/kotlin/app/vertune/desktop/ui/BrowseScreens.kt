package app.vertune.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vertune.desktop.data.Library
import app.vertune.desktop.data.Track
import app.vertune.desktop.data.toTrack
import app.vertune.desktop.player.PlayerController
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.MusicShelfRenderer
import app.vitune.providers.innertube.models.bodies.BrowseBody
import app.vitune.providers.innertube.models.bodies.ContinuationBody
import app.vitune.providers.innertube.models.bodies.NextBody
import app.vitune.providers.innertube.models.bodies.SearchBody
import app.vitune.providers.innertube.requests.albumPage
import app.vitune.providers.innertube.requests.artistPage
import app.vitune.providers.innertube.requests.discoverPage
import app.vitune.providers.innertube.requests.playlistPage
import app.vitune.providers.innertube.requests.relatedPage
import app.vitune.providers.innertube.requests.searchPage
import app.vitune.providers.innertube.utils.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val pagePadding = Modifier.padding(horizontal = 24.dp)

private fun Innertube.AlbumItem.subtitle() =
    listOfNotNull(authors?.joinToString("") { it.name.orEmpty() }?.ifBlank { null }, year).joinToString(" • ")
        .ifBlank { null }

@Composable
private fun AlbumCard(album: Innertube.AlbumItem, nav: Navigator) = Card(
    title = album.info?.name.orEmpty(),
    subtitle = album.subtitle(),
    image = album.thumbnail?.size(300)
) { album.info?.endpoint?.browseId?.let { nav.go(Screen.Album(it)) } }

@Composable
private fun ArtistCard(artist: Innertube.ArtistItem, nav: Navigator) = Card(
    title = artist.info?.name.orEmpty(),
    subtitle = artist.subscribersCountText,
    image = artist.thumbnail?.size(300),
    round = true
) { artist.info?.endpoint?.browseId?.let { nav.go(Screen.Artist(it)) } }

@Composable
private fun PlaylistCard(playlist: Innertube.PlaylistItem, nav: Navigator) = Card(
    title = playlist.info?.name.orEmpty(),
    subtitle = playlist.channel?.name ?: playlist.songCount?.let { "$it songs" },
    image = playlist.thumbnail?.size(300)
) { playlist.info?.endpoint?.browseId?.let { nav.go(Screen.Playlist(it)) } }

// ---------------------------------------------------------------- Home

@Composable
fun HomeScreen(nav: Navigator) {
    val discover = rememberLoad(Unit) { Innertube.discoverPage()?.getOrNull() }
    // "Quick picks": songs related to what you listen to most, like the phone app's home screen.
    val related = rememberLoad(Unit) {
        val seed = Library.top(1).firstOrNull()?.track ?: Library.history(1).firstOrNull()
        if (seed == null) Innertube.RelatedPage()
        else Innertube.relatedPage(NextBody(videoId = seed.id))?.getOrNull() ?: Innertube.RelatedPage()
    }

    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                "Home",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = pagePadding.padding(top = 16.dp)
            )
        }
        when (related) {
            is LoadState.Done -> {
                val r = related.value
                val songs = r.songs?.mapNotNull { it.toTrack() }.orEmpty()
                if (songs.isNotEmpty()) {
                    item { SectionTitle("Quick picks", pagePadding) }
                    itemsIndexed(songs.take(QUICK_PICKS)) { i, t ->
                        Row(pagePadding) { SongRow(t, nav, queue = songs, index = i) }
                    }
                }
                r.albums?.takeIf { it.isNotEmpty() }?.let { albums ->
                    item { SectionTitle("Related albums", pagePadding) }
                    item { Row(pagePadding) { CardRow(albums) { AlbumCard(it, nav) } } }
                }
                r.artists?.takeIf { it.isNotEmpty() }?.let { artists ->
                    item { SectionTitle("Similar artists", pagePadding) }
                    item { Row(pagePadding) { CardRow(artists) { ArtistCard(it, nav) } } }
                }
                r.playlists?.takeIf { it.isNotEmpty() }?.let { playlists ->
                    item { SectionTitle("Playlists you might like", pagePadding) }
                    item { Row(pagePadding) { CardRow(playlists) { PlaylistCard(it, nav) } } }
                }
            }
            else -> {}
        }
        when (discover) {
            LoadState.Loading -> item { Loading() }
            is LoadState.Error -> item { Message("Couldn't load: ${discover.message}") }
            is LoadState.Done -> {
                val d = discover.value
                if (d.newReleaseAlbums.isNotEmpty()) {
                    item { SectionTitle("New releases", pagePadding) }
                    item { Row(pagePadding) { CardRow(d.newReleaseAlbums) { AlbumCard(it, nav) } } }
                }
                val trending = d.trending.songs.mapNotNull { it.toTrack() }
                if (trending.isNotEmpty()) {
                    item { SectionTitle("Trending", pagePadding) }
                    itemsIndexed(trending) { i, t -> Row(pagePadding) { SongRow(t, nav, queue = trending, index = i) } }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Search

private enum class SearchTab(val label: String) { Songs("Songs"), Albums("Albums"), Artists("Artists"), Playlists("Playlists") }

@Composable
fun SearchScreen(query: String, nav: Navigator) {
    if (query.isBlank()) {
        Message("Type in the search box above and press Return.")
        return
    }
    var tab by remember(query) { mutableStateOf(SearchTab.Songs) }
    Column {
        Text(
            "Results for “$query”",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = pagePadding.padding(top = 16.dp, bottom = 8.dp)
        )
        TabRow(selectedTabIndex = tab.ordinal, modifier = pagePadding.width(480.dp), containerColor = MaterialTheme.colorScheme.background) {
            SearchTab.entries.forEach { t ->
                Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) })
            }
        }
        when (tab) {
            SearchTab.Songs -> PagedList(
                key = "songs/$query",
                load = { c ->
                    if (c == null) Innertube.searchPage(
                        SearchBody(query = query, params = Innertube.SearchFilter.Song.value),
                        Innertube.SongItem.Companion::from
                    ) else Innertube.searchPage(ContinuationBody(continuation = c), Innertube.SongItem.Companion::from)
                }
            ) { items ->
                val tracks = items.mapNotNull { it.toTrack() }
                itemsIndexed(tracks) { _, t -> Row(pagePadding) { SongRow(t, nav) } }
            }
            SearchTab.Albums -> PagedList(
                key = "albums/$query",
                load = { c -> searchItems(query, Innertube.SearchFilter.Album, c, Innertube.AlbumItem.Companion::from) }
            ) { items -> itemsIndexed(items) { _, a -> ItemRow(a.info?.name, a.subtitle(), a.thumbnail?.size(120)) { a.info?.endpoint?.browseId?.let { nav.go(Screen.Album(it)) } } } }
            SearchTab.Artists -> PagedList(
                key = "artists/$query",
                load = { c -> searchItems(query, Innertube.SearchFilter.Artist, c, Innertube.ArtistItem.Companion::from) }
            ) { items -> itemsIndexed(items) { _, a -> ItemRow(a.info?.name, a.subscribersCountText, a.thumbnail?.size(120), round = true) { a.info?.endpoint?.browseId?.let { nav.go(Screen.Artist(it)) } } } }
            SearchTab.Playlists -> PagedList(
                key = "playlists/$query",
                load = { c -> searchItems(query, Innertube.SearchFilter.CommunityPlaylist, c, Innertube.PlaylistItem.Companion::from) }
            ) { items -> itemsIndexed(items) { _, p -> ItemRow(p.info?.name, p.channel?.name ?: p.songCount?.let { "$it songs" }, p.thumbnail?.size(120)) { p.info?.endpoint?.browseId?.let { nav.go(Screen.Playlist(it)) } } } }
        }
    }
}

private suspend fun <T : Innertube.Item> searchItems(
    query: String,
    filter: Innertube.SearchFilter,
    continuation: String?,
    from: (MusicShelfRenderer.Content) -> T?
) = if (continuation == null) Innertube.searchPage(SearchBody(query = query, params = filter.value), from)
else Innertube.searchPage(ContinuationBody(continuation = continuation), from)

@Composable
private fun ItemRow(title: String?, subtitle: String?, image: String?, round: Boolean = false, onClick: () -> Unit) {
    Row(pagePadding) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .androidxClickable(onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Artwork(image, 56.dp, if (round) CircleShape else androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title.orEmpty(), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

private fun Modifier.androidxClickable(onClick: () -> Unit) =
    this.clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).clickable(onClick = onClick)

/** A list that loads more items when you reach the end, like the phone app's search results. */
@Composable
private fun <T : Innertube.Item> PagedList(
    key: String,
    load: suspend (continuation: String?) -> Result<Innertube.ItemsPage<T>?>?,
    content: LazyListScope.(List<T>) -> Unit
) {
    val items = remember(key) { mutableStateListOf<T>() }
    var continuation by remember(key) { mutableStateOf<String?>(null) }
    var loading by remember(key) { mutableStateOf(true) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var finished by remember(key) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun fetch() {
        loading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { load(continuation) }
            val page = result?.getOrNull()
            if (result == null || result.isFailure) error = result?.exceptionOrNull()?.message ?: "Couldn't load results"
            page?.items?.let { new -> items.addAll(new.filter { n -> items.none { it.key == n.key } }) }
            continuation = page?.continuation
            if (continuation == null) finished = true
            loading = false
        }
    }

    fun loadMore() {
        if (!finished && !loading) fetch()
    }

    LaunchedEffect(key) { fetch() }

    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
        content(items)
        item {
            when {
                loading -> Loading()
                error != null && items.isEmpty() -> Message("Couldn't load: $error")
                items.isEmpty() -> Message("No results")
                !finished -> LaunchedEffect(items.size) { loadMore() }
            }
        }
    }
}

// ---------------------------------------------------------------- Album / Playlist

@Composable
fun AlbumScreen(browseId: String, nav: Navigator) {
    val page = rememberLoad(browseId) { Innertube.albumPage(BrowseBody(browseId = browseId))?.getOrNull() }
    page.Show { album ->
        val tracks = album.songsPage?.items?.mapNotNull { it.toTrack() }.orEmpty()
        CollectionPage(
            title = album.title.orEmpty(),
            subtitle = listOfNotNull(album.authors?.joinToString("") { it.name.orEmpty() }, album.year)
                .joinToString(" • "),
            info = album.otherInfo,
            image = album.thumbnail?.size(600),
            tracks = tracks,
            nav = nav,
            onSubtitleClick = album.authors?.firstNotNullOfOrNull { it.endpoint?.browseId }?.let { id -> { nav.go(Screen.Artist(id)) } },
            extra = {
                album.otherVersions?.takeIf { it.isNotEmpty() }?.let { others ->
                    item { SectionTitle("Other versions", pagePadding) }
                    item { Row(pagePadding) { CardRow(others) { AlbumCard(it, nav) } } }
                }
            }
        )
    }
}

@Composable
fun PlaylistScreen(browseId: String, nav: Navigator) {
    val page = rememberLoad(browseId) {
        val first = Innertube.playlistPage(BrowseBody(browseId = browseId))?.getOrNull() ?: return@rememberLoad null
        // Load the whole playlist (up to a limit) so "Play" includes every song.
        var songs = first.songsPage?.items.orEmpty()
        var c = first.songsPage?.continuation
        var pages = 0
        while (c != null && pages < MAX_PLAYLIST_PAGES) {
            val more = Innertube.playlistPage(ContinuationBody(continuation = c))?.getOrNull() ?: break
            songs = songs + more.items.orEmpty()
            c = more.continuation
            pages++
        }
        first to songs.mapNotNull { it.toTrack() }.distinctBy { it.id }
    }
    page.Show { (playlist, tracks) ->
        CollectionPage(
            title = playlist.title.orEmpty(),
            subtitle = listOfNotNull(
                playlist.authors?.joinToString("") { it.name.orEmpty() }?.ifBlank { null },
                "${tracks.size} songs"
            ).joinToString(" • "),
            info = playlist.description,
            image = playlist.thumbnail?.size(600),
            tracks = tracks,
            nav = nav
        )
    }
}

@Composable
fun CollectionPage(
    title: String,
    subtitle: String?,
    info: String?,
    image: String?,
    tracks: List<Track>,
    nav: Navigator,
    onSubtitleClick: (() -> Unit)? = null,
    extra: LazyListScope.() -> Unit = {}
) {
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            Row(pagePadding.padding(top = 24.dp, bottom = 16.dp), verticalAlignment = Alignment.Bottom) {
                Artwork(image, 200.dp)
                Spacer(Modifier.width(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    if (!subtitle.isNullOrBlank()) Text(
                        subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (onSubtitleClick != null) Modifier.clickable(onClick = onSubtitleClick) else Modifier
                    )
                    if (!info.isNullOrBlank()) Text(
                        info,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Play") { PlayerController.play(tracks, 0) }
                        PillButton("Shuffle", primary = false) { PlayerController.play(tracks.shuffled(), 0) }
                    }
                }
            }
        }
        if (tracks.isEmpty()) item { Message("No songs") }
        itemsIndexed(tracks) { i, t ->
            Row(pagePadding) {
                SongRow(t, nav, queue = tracks, index = i, leading = {
                    Text(
                        "${i + 1}",
                        modifier = Modifier.width(32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                })
            }
        }
        extra()
    }
}

// ---------------------------------------------------------------- Artist

@Composable
fun ArtistScreen(browseId: String, nav: Navigator) {
    val page = rememberLoad(browseId) { Innertube.artistPage(BrowseBody(browseId = browseId))?.getOrNull() }
    page.Show { artist ->
        val songs = artist.songs?.mapNotNull { it.toTrack() }.orEmpty()
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            item {
                Row(pagePadding.padding(top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(artist.thumbnail?.size(400), 180.dp, CircleShape)
                    Spacer(Modifier.width(24.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(artist.name.orEmpty(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        artist.subscribersCountText?.let {
                            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Play") { PlayerController.play(songs, 0) }
                            songs.firstOrNull()?.let { first ->
                                PillButton("Radio", primary = false) { PlayerController.playWithRadio(first) }
                            }
                        }
                    }
                }
            }
            if (songs.isNotEmpty()) {
                item { SectionTitle("Songs", pagePadding) }
                itemsIndexed(songs) { i, t -> Row(pagePadding) { SongRow(t, nav, queue = songs, index = i) } }
            }
            artist.albums?.takeIf { it.isNotEmpty() }?.let { albums ->
                item { SectionTitle("Albums", pagePadding) }
                item { Row(pagePadding) { CardRow(albums) { AlbumCard(it, nav) } } }
            }
            artist.singles?.takeIf { it.isNotEmpty() }?.let { singles ->
                item { SectionTitle("Singles", pagePadding) }
                item { Row(pagePadding) { CardRow(singles) { AlbumCard(it, nav) } } }
            }
            artist.description?.takeIf { it.isNotBlank() }?.let { d ->
                item { SectionTitle("About", pagePadding) }
                item { Text(d, modifier = pagePadding, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

private const val QUICK_PICKS = 12
private const val MAX_PLAYLIST_PAGES = 10
