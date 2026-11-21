package app.vertune.desktop.ui

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.vertune.desktop.data.Library
import app.vertune.desktop.data.Track
import app.vertune.desktop.data.toTrack
import app.vertune.desktop.player.PlayerController
import app.vertune.desktop.player.SongCache
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.MusicShelfRenderer
import app.vitune.providers.innertube.models.bodies.BrowseBody
import app.vitune.providers.innertube.models.bodies.ContinuationBody
import app.vitune.providers.innertube.models.bodies.SearchBody
import app.vitune.providers.innertube.models.bodies.SearchSuggestionsBody
import app.vitune.providers.innertube.requests.albumPage
import app.vitune.providers.innertube.requests.artistPage
import app.vitune.providers.innertube.requests.playlistPage
import app.vitune.providers.innertube.requests.searchPage
import app.vitune.providers.innertube.requests.searchSuggestions
import app.vitune.providers.innertube.utils.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.vertune.desktop.data.Settings
import androidx.compose.ui.draw.rotate
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- Shared layout

/**
 * The phone's landscape album/playlist layout (LayoutWithAdaptiveThumbnail): the big cover on
 * the left, the title and songs on the right.
 */
@Composable
private fun ThumbnailLayout(
    image: String?,
    round: Boolean = false,
    list: LazyListScope.() -> Unit
) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(0.42f).fillMaxHeight().padding(32.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.BoxWithConstraints {
                val size = minOf(maxWidth, maxHeight, 420.dp)
                Thumbnail(image, size, if (round) CircleShape else RoundedCornerShape(12.dp))
            }
        }
        LazyColumn(Modifier.weight(0.58f).fillMaxHeight(), contentPadding = PaddingValues(end = 16.dp, bottom = 120.dp)) {
            list()
        }
    }
}

@Composable
private fun FloatingShuffle(tracks: List<Track>, scope: BoxScope) = with(scope) {
    if (tracks.isNotEmpty()) FloatingButton(
        "shuffle",
        { PlayerController.play(tracks.shuffled()) },
        Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 24.dp)
    )
}

private fun LazyListScope.trackList(tracks: List<Track>, nav: Navigator, numbered: Boolean = false, extraMenu: (Int, Track) -> List<ContextMenuItem> = { _, _ -> emptyList() }) {
    itemsIndexed(tracks) { i, t ->
        val number: @Composable () -> Unit = {
            BasicText(
                "${i + 1}",
                style = Type.xs.semiBold.color(LocalPalette.current.textDisabled).copy(textAlign = TextAlign.Center),
                modifier = Modifier.width(24.dp)
            )
        }
        SongItem(
            t, nav, queue = tracks, index = i,
            leading = if (numbered) number else null,
            extraMenu = extraMenu(i, t)
        )
    }
}

/**
 * The phone's download button on playlist and album pages: saves every song for offline.
 * Shows how many are done while it works, and disappears once everything is saved.
 */
@Composable
private fun CacheButton(tracks: List<Track>) {
    val palette = LocalPalette.current
    val cachedIds by SongCache.ids.collectAsState()
    val pending by SongCache.pending.collectAsState()
    if (tracks.isEmpty()) return
    val ids = tracks.map { it.id }.toSet()
    val saved = ids.count { it in cachedIds }
    val working = ids.any { it in pending }
    when {
        working -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.CircularProgressIndicator(color = palette.text, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            BasicText("$saved/${ids.size} saved", style = Type.xxs.medium.color(palette.textSecondary))
        }
        saved < ids.size -> HeaderIconButton("download", {
            SongCache.precacheAll(tracks)
            app.vertune.desktop.player.LyricsLoader.prefetchAll(tracks)
        }, selected = true)
        // Everything's saved already: a lyrics button saves any missing lyrics instead
        else -> HeaderIconButton("text", { app.vertune.desktop.player.LyricsLoader.prefetchAll(tracks) })
    }
}

// ---------------------------------------------------------------- Album / YouTube playlist

@Composable
fun AlbumScreen(browseId: String, nav: Navigator) {
    val page = rememberLoad(browseId) { Innertube.albumPage(BrowseBody(browseId = browseId))?.getOrNull() }
    page.Show { album ->
        val tracks = album.songsPage?.items?.mapNotNull { it.toTrack() }.orEmpty()
        Box(Modifier.fillMaxSize()) {
            ThumbnailLayout(album.thumbnail?.size(1000)) {
                item {
                    Header(album.title.orEmpty()) {
                        BookmarkButton(
                            Library.Bookmark(
                                browseId, Library.BOOKMARK_ALBUM, album.title,
                                album.authors?.joinToString("") { it.name.orEmpty() }?.ifBlank { null } ?: album.year,
                                album.thumbnail?.size(544)
                            )
                        )
                        SecondaryTextButton("Enqueue", { tracks.forEach(PlayerController::addToQueue) }, enabled = tracks.isNotEmpty())
                    CacheButton(tracks)
                        Spacer(Modifier.weight(1f))
                        SecondaryTextButton("Play", { PlayerController.play(tracks) }, enabled = tracks.isNotEmpty())
                    }
                }
                item {
                    val artistId = album.authors?.firstNotNullOfOrNull { it.endpoint?.browseId }
                    BasicText(
                        listOfNotNull(album.authors?.joinToString("") { it.name.orEmpty() }?.ifBlank { null }, album.year).joinToString(" • "),
                        style = Type.xs.semiBold.color(LocalPalette.current.textSecondary).copy(textAlign = TextAlign.End),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            .let { m -> if (artistId != null) m.clickable { nav.go(Screen.Artist(artistId)) } else m }
                    )
                }
                trackList(tracks, nav, numbered = true)
                album.otherVersions?.takeIf { it.isNotEmpty() }?.let { others ->
                    item { SectionTitle("Other versions", Modifier.padding(start = 8.dp)) }
                    item { TileRow(others) { AlbumTile(it, nav) } }
                }
            }
            FloatingShuffle(tracks, this)
        }
    }
}

@Composable
fun PlaylistScreen(browseId: String, nav: Navigator) {
    val page = rememberLoad(browseId) {
        val first = Innertube.playlistPage(BrowseBody(browseId = browseId))?.getOrNull() ?: return@rememberLoad null
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
        var saved by remember { mutableStateOf<String?>(null) }
        Box(Modifier.fillMaxSize()) {
            ThumbnailLayout(playlist.thumbnail?.size(1000)) {
                item {
                    Header(playlist.title.orEmpty()) {
                        SecondaryTextButton("Enqueue", { tracks.forEach(PlayerController::addToQueue) }, enabled = tracks.isNotEmpty())
                    CacheButton(tracks)
                        Spacer(Modifier.weight(1f))
                        SecondaryTextButton(saved ?: "Save to my playlists", {
                            val id = Library.createPlaylist(playlist.title ?: "Playlist")
                            tracks.forEach { Library.addToPlaylist(id, it) }
                            saved = "Saved"
                        }, enabled = saved == null && tracks.isNotEmpty())
                    }
                }
                item {
                    BasicText(
                        listOfNotNull(playlist.authors?.joinToString("") { it.name.orEmpty() }?.ifBlank { null }, "${tracks.size} songs").joinToString(" • "),
                        style = Type.xs.semiBold.color(LocalPalette.current.textSecondary).copy(textAlign = TextAlign.End),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                trackList(tracks, nav)
            }
            FloatingShuffle(tracks, this)
        }
    }
}

// ---------------------------------------------------------------- Local playlist

@Composable
fun LocalPlaylistScreen(id: Long, nav: Navigator) {
    val version by Library.version.collectAsState()
    val data by produceState<Pair<String, List<Track>>?>(null, id, version) {
        value = withContext(Dispatchers.IO) {
            Library.playlistName(id)?.let { it to Library.playlistSongs(id) }
        }
    }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val (name, tracks) = data ?: run {
        Loading()
        return
    }

    if (renaming) TextFieldDialog("Rename playlist", "Enter the playlist name", name, { renaming = false }) {
        Library.renamePlaylist(id, it)
    }
    if (confirmDelete) ConfirmDialog("Delete the playlist “$name”?", { confirmDelete = false }) {
        Library.deletePlaylist(id)
        nav.back()
    }

    Box(Modifier.fillMaxSize()) {
        ThumbnailLayout(tracks.firstOrNull()?.largeThumbnail) {
            item {
                Header(name) {
                    SecondaryTextButton("Enqueue", { tracks.forEach(PlayerController::addToQueue) }, enabled = tracks.isNotEmpty())
                    CacheButton(tracks)
                    Spacer(Modifier.weight(1f))
                    SecondaryTextButton("Rename", { renaming = true })
                    SecondaryTextButton("Delete", { confirmDelete = true })
                }
            }
            if (tracks.isEmpty()) item { Message("Add songs here with right-click → Add to playlist…") }
            trackList(tracks, nav, numbered = true) { i, t ->
                buildList {
                    add(ContextMenuItem("Remove from this playlist") { Library.removeFromPlaylist(id, t.id) })
                    if (i > 0) add(ContextMenuItem("Move up") { Library.movePlaylistSong(id, i, i - 1) })
                    if (i < tracks.size - 1) add(ContextMenuItem("Move down") { Library.movePlaylistSong(id, i, i + 1) })
                }
            }
        }
        FloatingShuffle(tracks, this)
    }
}

/** The phone's bookmark icon on album and artist pages; bookmarked ones show up in the Albums and Artists tabs. */
@Composable
private fun BookmarkButton(bookmark: Library.Bookmark) {
    val version by Library.version.collectAsState()
    val on by produceState(false, bookmark.id, version) {
        value = withContext(Dispatchers.IO) { runCatching { Library.isBookmarked(bookmark.id) }.getOrDefault(false) }
    }
    AppIconButton(
        if (on) "bookmark" else "bookmark_outline",
        LocalPalette.current.accent,
        { Library.setBookmarked(bookmark, !on) },
        size = 18.dp
    )
}

// ---------------------------------------------------------------- Artist

@Composable
fun ArtistScreen(browseId: String, nav: Navigator) {
    val page = rememberLoad(browseId) { Innertube.artistPage(BrowseBody(browseId = browseId))?.getOrNull() }
    page.Show { artist ->
        val songs = artist.songs?.mapNotNull { it.toTrack() }.orEmpty()
        Box(Modifier.fillMaxSize()) {
            ThumbnailLayout(artist.thumbnail?.size(1000), round = true) {
                item {
                    Header(artist.name.orEmpty()) {
                        BookmarkButton(Library.Bookmark(browseId, Library.BOOKMARK_ARTIST, artist.name, null, artist.thumbnail?.size(544)))
                        artist.subscribersCountText?.let {
                            BasicText(it, style = Type.xs.semiBold.color(LocalPalette.current.textSecondary))
                        }
                        Spacer(Modifier.weight(1f))
                        songs.firstOrNull()?.let { first -> SecondaryTextButton("Start radio", { PlayerController.playWithRadio(first) }) }
                        SecondaryTextButton("Play", { PlayerController.play(songs) }, enabled = songs.isNotEmpty())
                    }
                }
                if (songs.isNotEmpty()) {
                    item { SectionTitle("Songs", Modifier.padding(start = 8.dp)) }
                    trackList(songs, nav)
                }
                artist.albums?.takeIf { it.isNotEmpty() }?.let { albums ->
                    item { SectionTitle("Albums", Modifier.padding(start = 8.dp)) }
                    item { TileRow(albums) { AlbumTile(it, nav) } }
                }
                artist.singles?.takeIf { it.isNotEmpty() }?.let { singles ->
                    item { SectionTitle("Singles", Modifier.padding(start = 8.dp)) }
                    item { TileRow(singles) { AlbumTile(it, nav) } }
                }
                artist.description?.takeIf { it.isNotBlank() }?.let { d ->
                    item { SectionTitle("Information", Modifier.padding(start = 8.dp)) }
                    item {
                        BasicText(
                            d,
                            style = Type.xs.medium.color(LocalPalette.current.textSecondary),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
            FloatingShuffle(songs, this)
        }
    }
}

// ---------------------------------------------------------------- Built-in playlists

@Composable
private fun BuiltInPage(title: String, tracks: List<Track>?, empty: String, nav: Navigator, info: String? = null, headerExtra: @Composable () -> Unit = {}, trailing: (Track) -> String? = { it.durationText }, subtitle: (Int, Track) -> String? = { _, t -> t.artists }) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp)) {
            item {
                Header(title, Modifier.padding(horizontal = 0.dp)) {
                    SecondaryTextButton("Enqueue", { tracks?.forEach(PlayerController::addToQueue) }, enabled = !tracks.isNullOrEmpty())
                    CacheButton(tracks.orEmpty())
                    Spacer(Modifier.weight(1f))
                    headerExtra()
                }
            }
            if (info != null) item {
                BasicText(
                    info,
                    style = Type.xs.semiBold.color(LocalPalette.current.textSecondary).copy(textAlign = TextAlign.End),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            when {
                tracks == null -> item { Loading() }
                tracks.isEmpty() -> item { Message(empty) }
                else -> itemsIndexed(tracks) { i, t ->
                    SongItem(t, nav, queue = tracks, index = i, trailingText = trailing(t), subtitle = subtitle(i, t))
                }
            }
        }
        FloatingShuffle(tracks.orEmpty(), this)
    }
}

@Composable
private fun <T> rememberLibrary(vararg keys: Any?, read: () -> T): T? {
    val version by Library.version.collectAsState()
    val value by produceState<T?>(null, version, *keys) {
        value = withContext(Dispatchers.IO) { runCatching(read).getOrNull() }
    }
    return value
}

@Composable
fun FavoritesScreen(nav: Navigator) = BuiltInPage(
    "Favorites", rememberLibrary { Library.favorites() }, "Songs you like show up here.", nav
)

@Composable
fun HistoryScreen(nav: Navigator) = BuiltInPage(
    "History", rememberLibrary { Library.history() }, "Songs you listen to show up here.", nav
)

/** Songs saved on this computer, which play without internet (the phone's Offline playlist). */
@Composable
fun OfflineScreen(nav: Navigator) {
    val ids by SongCache.ids.collectAsState()
    val tracks = rememberLibrary(ids) { Library.songsWithIds(SongCache.idsByRecent()) }
    val bytes by SongCache.bytes.collectAsState()
    BuiltInPage(
        "Offline", tracks,
        "Songs are saved here as you play them, so they start instantly and play without internet. " +
            "You can also right-click a song and choose Save for offline.",
        nav,
        info = if (tracks.isNullOrEmpty()) null else "${tracks.size} songs • ${formatBytes(bytes)} on this ${app.vertune.desktop.Paths.deviceName}"
    )
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "${bytes shr 20} MB"
    else -> "${bytes shr 10} KB"
}

private enum class Period(val label: String, val days: Long?) {
    Week("Past week", 7), Month("Past month", 30), Year("Past year", 365), All("All time", null)
}

/** My top 50: the songs you listened to the longest, with how long you listened to each. */
@Composable
fun TopScreen(nav: Navigator) {
    var period by remember { mutableStateOf(Period.All) }
    val since = period.days?.let { System.currentTimeMillis() - it * 24 * 60 * 60 * 1000 }
    val topLength by Settings.topListLength.flow.collectAsState()
    val top = rememberLibrary(period, topLength) { Library.top(topLength, since) }
    val total = rememberLibrary(period) { Library.totalListenedMs(since) } ?: 0L
    val byId = top?.associateBy { it.track.id }.orEmpty()

    BuiltInPage(
        title = str("format_my_top_playlist", "My top $topLength", topLength),
        tracks = top?.map { it.track },
        empty = "Listen to some music and your most played songs show up here.",
        nav = nav,
        info = if (top.isNullOrEmpty()) null
        else "Listened ${formatListened(total)} " + if (period == Period.All) "in total" else "in the ${period.label.lowercase()}",
        headerExtra = {
            Period.entries.forEach { p -> PeriodChip(p.label, p == period) { period = p } }
        },
        trailing = { t -> byId[t.id]?.let { formatListened(it.listenedMs) } },
        subtitle = { i, t ->
            val plays = byId[t.id]?.plays ?: 0
            "#${i + 1}  •  " + listOfNotNull(t.artists, "$plays ${if (plays == 1) "play" else "plays"}").joinToString(" • ")
        }
    )
}

@Composable
private fun PeriodChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    BasicText(
        text,
        style = Type.xxs.medium.color(if (selected) palette.onAccent else palette.text),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) palette.accent else palette.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

// ---------------------------------------------------------------- Search

private const val MAX_HISTORY_SHOWN = 20

private enum class SearchTab(val label: String) { Songs("Songs"), Albums("Albums"), Artists("Artists"), Playlists("Playlists") }

/** Like the phone: a big text field where the title goes, suggestions while typing, results below. */
@Composable
fun SearchScreen(initialQuery: String, nav: Navigator) {
    val palette = LocalPalette.current
    var text by remember(initialQuery) { mutableStateOf(initialQuery) }
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var tab by remember { mutableStateOf(SearchTab.Songs) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (initialQuery.isEmpty()) runCatching { focus.requestFocus() } }

    val suggestions by produceState(emptyList<String>(), text, query) {
        value = emptyList()
        if (text.isBlank() || text == query) return@produceState
        delay(SUGGESTION_DELAY_MS)
        value = withContext(Dispatchers.IO) {
            Innertube.searchSuggestions(SearchSuggestionsBody(input = text))?.getOrNull()?.filterNotNull().orEmpty()
        }
    }

    val pauseHistory by Settings.pauseSearchHistory.flow.collectAsState()
    val libraryVersion by Library.version.collectAsState()
    val history by produceState(emptyList<String>(), text, query, pauseHistory, libraryVersion) {
        value = if (pauseHistory || (query.isNotEmpty() && text == query)) emptyList()
        else withContext(Dispatchers.IO) { runCatching { Library.searchHistory(text.trim()) }.getOrDefault(emptyList()) }
    }
    val scope = rememberCoroutineScope()

    fun submit(q: String) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) return
        if (!Settings.pauseSearchHistory.value) scope.launch(Dispatchers.IO) { runCatching { Library.addSearch(trimmed) } }
        text = trimmed
        query = trimmed
        nav.replace(Screen.Search(trimmed))
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier.padding(horizontal = 16.dp).height(Dimens.headerHeight).fillMaxWidth()
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = Type.xxl.medium.color(palette.text).copy(textAlign = TextAlign.End),
                singleLine = true,
                cursorBrush = SolidColor(palette.text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                        submit(text)
                        true
                    } else false
                },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterEnd) {
                        if (text.isEmpty()) BasicText(
                            "Enter a name",
                            style = Type.xxl.medium.color(palette.textDisabled).copy(textAlign = TextAlign.End),
                            maxLines = 1
                        )
                        inner()
                    }
                }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.BottomEnd).fillMaxWidth().height(48.dp)
            ) {
                if (query.isNotEmpty()) SearchTab.entries.forEach { t -> PeriodChip(t.label, t == tab) { tab = t } }
                Spacer(Modifier.weight(1f))
                if (text.isNotEmpty()) SecondaryTextButton("Clear", { text = ""; query = ""; runCatching { focus.requestFocus() } })
            }
        }

        if (history.isNotEmpty() || suggestions.isNotEmpty()) {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
                itemsIndexed(history.take(MAX_HISTORY_SHOWN)) { _, h ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().hoverHighlight().clickable { submit(h) }.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        AppIcon("time", palette.textDisabled, size = 18.dp)
                        Spacer(Modifier.width(16.dp))
                        BasicText(h, style = Type.s.color(palette.text), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        AppIconButton("close", palette.textDisabled, { scope.launch(Dispatchers.IO) { runCatching { Library.removeSearch(h) } } }, size = 16.dp)
                        AppIconButton("arrow_forward", palette.textDisabled, { text = h }, Modifier.rotate(225f), size = 16.dp)
                    }
                }
                itemsIndexed(suggestions.filter { it !in history }) { _, s ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().hoverHighlight().clickable { submit(s) }.padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        AppIcon("search", palette.textDisabled, size = 18.dp)
                        Spacer(Modifier.width(16.dp))
                        BasicText(s, style = Type.s.color(palette.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            return@Column
        }

        if (query.isBlank()) return@Column
        when (tab) {
            SearchTab.Songs -> PagedList("songs/$query", { c -> searchItems(query, Innertube.SearchFilter.Song, c, Innertube.SongItem.Companion::from) }) { items ->
                val tracks = items.mapNotNull { it.toTrack() }
                itemsIndexed(tracks) { _, t -> SongItem(t, nav, modifier = pagePadding) }
            }
            SearchTab.Albums -> PagedList("albums/$query", { c -> searchItems(query, Innertube.SearchFilter.Album, c, Innertube.AlbumItem.Companion::from) }) { items ->
                itemsIndexed(items) { _, a ->
                    ResultRow(a.info?.name, listOfNotNull(a.authors?.joinToString("") { it.name.orEmpty() }, a.year).joinToString(" • "), a.thumbnail?.size(200)) {
                        a.info?.endpoint?.browseId?.let { nav.go(Screen.Album(it)) }
                    }
                }
            }
            SearchTab.Artists -> PagedList("artists/$query", { c -> searchItems(query, Innertube.SearchFilter.Artist, c, Innertube.ArtistItem.Companion::from) }) { items ->
                itemsIndexed(items) { _, a ->
                    ResultRow(a.info?.name, a.subscribersCountText, a.thumbnail?.size(200), round = true) {
                        a.info?.endpoint?.browseId?.let { nav.go(Screen.Artist(it)) }
                    }
                }
            }
            SearchTab.Playlists -> PagedList("playlists/$query", { c -> searchItems(query, Innertube.SearchFilter.CommunityPlaylist, c, Innertube.PlaylistItem.Companion::from) }) { items ->
                itemsIndexed(items) { _, p ->
                    ResultRow(p.info?.name, p.channel?.name ?: p.songCount?.let { "$it songs" }, p.thumbnail?.size(200)) {
                        p.info?.endpoint?.browseId?.let { nav.go(Screen.Playlist(it)) }
                    }
                }
            }
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
private fun ResultRow(title: String?, subtitle: String?, image: String?, round: Boolean = false, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = pagePadding.fillMaxWidth().hoverHighlight().clickable(onClick = onClick).padding(8.dp)
    ) {
        Thumbnail(image, Dimens.albumThumbnail * 0.75f, if (round) CircleShape else Dimens.thumbnailShape)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(title.orEmpty(), style = Type.xs.semiBold.color(palette.text), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) BasicText(subtitle, style = Type.xs.semiBold.color(palette.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

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

    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
        content(items)
        item {
            when {
                loading -> Loading()
                error != null && items.isEmpty() -> Message("An error has occurred.\n$error")
                items.isEmpty() -> Message("No results found")
                !finished -> LaunchedEffect(items.size) { loadMore() }
            }
        }
    }
}

// ---------------------------------------------------------------- Dialogs

/** The phone's dialog buttons; the main one ("Done") sits in an accent-coloured pill. */
@Composable
fun DialogTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = false) {
    val palette = LocalPalette.current
    BasicText(
        text,
        style = Type.xs.medium.color(
            when {
                !enabled -> palette.textDisabled
                primary -> palette.onAccent
                else -> palette.text
            }
        ),
        modifier = modifier
            .clip(RoundedCornerShape(36.dp))
            .background(if (primary) palette.accent else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    )
}

/** The phone's DefaultDialog: a rounded background1 card. */
@Composable
fun DialogFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(LocalPalette.current.background1)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) { content() }
    }
}

/** The phone's TextFieldDialog: a centred text field with Cancel and a highlighted Done. */
@Composable
fun TextFieldDialog(
    @Suppress("UNUSED_PARAMETER") title: String,
    hint: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onDone: (String) -> Unit
) {
    val palette = LocalPalette.current
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(FOCUS_DELAY_MS); runCatching { focus.requestFocus() } }
    fun done() {
        if (text.isNotBlank()) {
            onDismiss()
            onDone(text.trim())
        }
    }
    DialogFrame(onDismiss) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = Type.xs.semiBold.color(palette.text).copy(textAlign = TextAlign.Center),
            singleLine = true,
            cursorBrush = SolidColor(palette.text),
            modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus).onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) { done(); true } else false
            },
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (text.isEmpty()) BasicText(
                        hint,
                        style = Type.xs.semiBold.color(palette.textSecondary).copy(textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth()
                    )
                    inner()
                }
            }
        )
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            DialogTextButton(str("cancel", "Cancel"), onDismiss)
            DialogTextButton(str("done", "Done"), ::done, primary = true)
        }
    }
}

@Composable
fun ConfirmDialog(text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    DialogFrame(onDismiss) {
        BasicText(
            text,
            style = Type.xs.medium.color(LocalPalette.current.text).copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            DialogTextButton(str("cancel", "Cancel"), onDismiss)
            DialogTextButton(str("confirm", "Confirm"), { onDismiss(); onConfirm() }, primary = true)
        }
    }
}

/** The phone's ValueSelectorDialog: a list with radio circles; picking one closes it. */
@Composable
fun <T> ValueSelectorDialog(title: String, selected: T, values: List<T>, label: @Composable (T) -> String, onDismiss: () -> Unit, onSelect: (T) -> Unit) {
    val palette = LocalPalette.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(420.dp).clip(RoundedCornerShape(8.dp)).background(palette.background1).padding(vertical = 16.dp)
        ) {
            BasicText(title, style = Type.s.semiBold.color(palette.text), modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp))
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                values.forEach { v ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onDismiss(); onSelect(v) }.padding(vertical = 12.dp, horizontal = 24.dp)
                    ) {
                        if (v == selected) Box(
                            Modifier.size(18.dp).background(palette.accent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Box(Modifier.size(8.dp).background(palette.onAccent, CircleShape)) }
                        else Box(Modifier.size(18.dp).border(1.dp, palette.textDisabled, CircleShape))
                        BasicText(label(v), style = Type.xs.medium.color(palette.text))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(end = 24.dp), contentAlignment = Alignment.CenterEnd) {
                DialogTextButton(str("cancel", "Cancel"), onDismiss)
            }
        }
    }
}

/** The "Add to playlist" picker from the song menu, with a "New playlist" option. */
@Composable
fun AddToPlaylistDialog(track: Track, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    var creating by remember { mutableStateOf(false) }
    val version by Library.version.collectAsState()
    val playlists by produceState(emptyList<Library.PlaylistPreview>(), version) {
        value = withContext(Dispatchers.IO) { Library.playlists(Library.PlaylistSort.Name, false) }
    }
    if (creating) {
        TextFieldDialog("New playlist", "Enter the playlist name", onDismiss = onDismiss) { name ->
            Library.addToPlaylist(Library.createPlaylist(name), track)
        }
        return
    }
    DialogFrame(onDismiss) {
        BasicText("Add “${track.title}” to…", style = Type.s.semiBold.color(palette.text), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().hoverHighlight().clickable { creating = true }.padding(12.dp)
            ) {
                AppIcon("add", palette.accent, size = 18.dp)
                Spacer(Modifier.width(12.dp))
                BasicText("New playlist", style = Type.xs.semiBold.color(palette.accent))
            }
            playlists.forEach { p ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().hoverHighlight().clickable {
                        Library.addToPlaylist(p.id, track)
                        onDismiss()
                    }.padding(12.dp)
                ) {
                    AppIcon("playlist", palette.textSecondary, size = 18.dp)
                    Spacer(Modifier.width(12.dp))
                    BasicText(p.name, style = Type.xs.semiBold.color(palette.text), modifier = Modifier.weight(1f))
                    BasicText("${p.songCount}", style = Type.xxs.medium.color(palette.textSecondary))
                }
            }
        }
        SecondaryTextButton("Cancel", onDismiss, Modifier.align(Alignment.End))
    }
}

private const val MAX_PLAYLIST_PAGES = 10
private const val SUGGESTION_DELAY_MS = 250L
private const val FOCUS_DELAY_MS = 300L
