package app.vertune.desktop.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vertune.desktop.data.Library
import app.vertune.desktop.data.Track
import app.vertune.desktop.player.PlayerController
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sizes from the phone app (core/ui Dimensions.kt). */
object Dimens {
    val songThumbnail = 54.dp
    val albumThumbnail = 108.dp
    val artistThumbnail = 92.dp
    val headerHeight = 140.dp
    val itemPadding = 8.dp
    val alternativePadding = 12.dp
    val thumbnailShape = RoundedCornerShape(8.dp)
}

/** The phone's "surface" colour on a pure black background (grey buttons and chips). */
val Palette.surface get() = Color(0xff272727)

/** Big right-aligned page title with a row of actions underneath, like the phone app. */
@Composable
fun Header(
    title: String,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = modifier.padding(horizontal = 16.dp).height(Dimens.headerHeight).fillMaxWidth()
    ) {
        BasicText(
            text = title,
            style = Type.xxl.medium.color(LocalPalette.current.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (actions != null) Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.BottomEnd).fillMaxWidth().heightIn(min = 48.dp),
            content = actions
        )
    }
}

@Composable
fun SecondaryTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val palette = LocalPalette.current
    BasicText(
        text = text,
        style = Type.xxs.medium.color(if (enabled) palette.text else palette.textDisabled).copy(textAlign = TextAlign.Center),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .background(palette.surface)
            .padding(8.dp)
            .padding(horizontal = 8.dp)
    )
}

/** Small icon in a page header; [selected] icons are shown in the accent colour. */
@Composable
fun HeaderIconButton(name: String, onClick: () -> Unit, selected: Boolean = true, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    AppIconButton(name, if (selected) palette.accent else palette.textDisabled, onClick, modifier, size = 18.dp)
}

@Composable
fun Thumbnail(url: String?, size: Dp, shape: Shape = Dimens.thumbnailShape, modifier: Modifier = Modifier, overlay: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.size(size).clip(shape).background(LocalPalette.current.background1)) {
        if (url != null) AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        overlay()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text,
        style = Type.m.semiBold.color(LocalPalette.current.text),
        modifier = modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalPalette.current.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun Message(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        BasicText(
            text,
            style = Type.xs.semiBold.color(LocalPalette.current.textSecondary).copy(textAlign = TextAlign.Center)
        )
    }
}

/** Whether a song is liked, kept up to date as the library changes. */
@Composable
fun rememberLiked(id: String): Boolean {
    val version by Library.version.collectAsState()
    val liked by produceState(false, id, version) {
        value = withContext(Dispatchers.IO) { runCatching { Library.isLiked(id) }.getOrDefault(false) }
    }
    return liked
}

@Composable
fun Modifier.hoverHighlight(shape: Shape = RoundedCornerShape(8.dp)): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    return this
        .clip(shape)
        .background(if (hovered) LocalPalette.current.background1 else Color.Transparent)
        .hoverable(interaction)
}

/**
 * One song, laid out like the phone's SongItem: 54 dp artwork, title and artist in semibold,
 * duration on the right. Click plays it (with [queue] after it, or a radio of similar songs);
 * right-click shows the menu.
 */
@Composable
fun SongItem(
    track: Track,
    nav: Navigator,
    queue: List<Track>? = null,
    index: Int = 0,
    trailingText: String? = track.durationText,
    subtitle: String? = track.artists,
    leading: (@Composable () -> Unit)? = null,
    extraMenu: List<ContextMenuItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val current by PlayerController.current.collectAsState()
    val playing by PlayerController.isPlaying.collectAsState()
    val isCurrent = current?.id == track.id
    val liked = rememberLiked(track.id)

    val play = { if (queue != null) PlayerController.play(queue, index) else PlayerController.playWithRadio(track) }

    ContextMenuArea(items = { songMenuItems(track, nav) + extraMenu }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier
                .fillMaxWidth()
                .hoverHighlight()
                .clickable(onClick = play)
                .padding(vertical = Dimens.itemPadding, horizontal = Dimens.itemPadding)
        ) {
            leading?.invoke()
            Thumbnail(track.thumbnail, Dimens.songThumbnail) {
                if (isCurrent) Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(if (playing) "equalizer" else "play", palette.accent, size = 22.dp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        track.title,
                        style = Type.xs.semiBold.color(if (isCurrent) palette.accent else palette.text),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (liked) AppIcon("heart", palette.red, Modifier.padding(start = 6.dp), size = 12.dp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (subtitle != null) BasicText(
                        subtitle,
                        style = Type.xs.semiBold.color(palette.textSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.explicit) AppIcon("explicit", palette.text, Modifier.padding(start = 4.dp), size = 15.dp)
                }
            }
            if (trailingText != null) BasicText(
                trailingText,
                style = Type.xxs.medium.color(palette.textSecondary),
                modifier = Modifier.padding(start = 8.dp, end = 4.dp)
            )
        }
    }
}

fun songMenuItems(track: Track, nav: Navigator): List<ContextMenuItem> = buildList {
    add(ContextMenuItem("Play next") { PlayerController.playNext(track) })
    add(ContextMenuItem("Add to queue") { PlayerController.addToQueue(track) })
    add(ContextMenuItem("Start radio") { PlayerController.playWithRadio(track) })
    add(
        ContextMenuItem(if (Library.isLiked(track.id)) "Remove from favorites" else "Add to favorites") {
            Library.setLiked(track, !Library.isLiked(track.id))
        }
    )
    add(ContextMenuItem("Add to playlist…") { nav.addToPlaylist = track })
    track.albumId?.let { id -> add(ContextMenuItem("Go to album") { nav.go(Screen.Album(id)) }) }
    track.artistId?.let { id -> add(ContextMenuItem("More of ${track.artists ?: "this artist"}") { nav.go(Screen.Artist(id)) }) }
    add(
        ContextMenuItem("Copy link") {
            val sel = java.awt.datatransfer.StringSelection("https://music.youtube.com/watch?v=${track.id}")
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, null)
        }
    )
}

/**
 * A grid/row tile for albums, artists and playlists, like the phone's "alternative" item layout:
 * 108 dp artwork with the name centred underneath.
 */
@Composable
fun ItemTile(
    title: String,
    subtitle: String?,
    image: String?,
    round: Boolean = false,
    size: Dp = Dimens.albumThumbnail,
    onClick: () -> Unit
) {
    val palette = LocalPalette.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .hoverHighlight(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(Dimens.alternativePadding)
            .width(size)
    ) {
        Thumbnail(image, size, if (round) CircleShape else Dimens.thumbnailShape)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(
                title,
                style = Type.xs.semiBold.color(palette.text).copy(textAlign = TextAlign.Center),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) BasicText(
                subtitle,
                style = Type.xs.semiBold.color(palette.textSecondary).copy(textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The phone's built-in playlist tiles (Favorites, My top, History): a coloured icon and a name. */
@Composable
fun IconTile(icon: String, tint: Color, title: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .hoverHighlight(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(Dimens.alternativePadding)
            .width(Dimens.albumThumbnail)
    ) {
        Box(Modifier.size(Dimens.albumThumbnail), contentAlignment = Alignment.Center) {
            AppIcon(icon, tint, size = 24.dp)
        }
        BasicText(title, style = Type.xs.semiBold.color(palette.text).copy(textAlign = TextAlign.Center), maxLines = 2)
    }
}

/** A collage of up to four covers, like the phone's playlist thumbnails. */
@Composable
fun PlaylistTile(name: String, songCount: Int, thumbnails: List<String>, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .hoverHighlight(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(Dimens.alternativePadding)
            .width(Dimens.albumThumbnail)
    ) {
        Box(Modifier.size(Dimens.albumThumbnail).clip(Dimens.thumbnailShape).background(palette.background1)) {
            val distinct = thumbnails.distinct()
            when {
                distinct.isEmpty() -> AppIcon("musical_notes", palette.textDisabled, Modifier.align(Alignment.Center))
                distinct.size < 4 -> Thumbnail(distinct.first(), Dimens.albumThumbnail)
                else -> Column {
                    Row { Thumbnail(distinct[0], 54.dp, RoundedCornerShape(0.dp)); Thumbnail(distinct[1], 54.dp, RoundedCornerShape(0.dp)) }
                    Row { Thumbnail(distinct[2], 54.dp, RoundedCornerShape(0.dp)); Thumbnail(distinct[3], 54.dp, RoundedCornerShape(0.dp)) }
                }
            }
            BasicText(
                "$songCount",
                style = Type.xxs.medium.color(Color.White),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        BasicText(
            name,
            style = Type.xs.semiBold.color(palette.text).copy(textAlign = TextAlign.Center),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun <T> TileRow(items: List<T>, tile: @Composable (T) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 4.dp)) {
        items(items) { tile(it) }
    }
}

/** Runs [block] once per key and keeps its result; shows loading/error states. */
@Composable
fun <T> rememberLoad(vararg key: Any?, block: suspend () -> T?): LoadState<T> {
    val state = produceState<LoadState<T>>(LoadState.Loading, *key) {
        value = LoadState.Loading
        value = runCatching { withContext(Dispatchers.IO) { block() } }
            .fold(
                onSuccess = { if (it == null) LoadState.Error("Nothing found") else LoadState.Done(it) },
                onFailure = { LoadState.Error(it.message ?: "Couldn't load this page") }
            )
    }
    return state.value
}

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Done<T>(val value: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}

@Composable
fun <T> LoadState<T>.Show(content: @Composable (T) -> Unit) {
    when (this) {
        LoadState.Loading -> Loading()
        is LoadState.Error -> Message("An error has occurred.\n$message")
        is LoadState.Done -> content(value)
    }
}

/** The phone's floating search button (bottom right). */
@Composable
fun FloatingButton(icon: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .clickable(onClick = onClick)
    ) {
        AppIcon(icon, palette.text, size = 22.dp)
    }
}

@Composable
fun Spacer16() = Spacer(Modifier.height(16.dp))
