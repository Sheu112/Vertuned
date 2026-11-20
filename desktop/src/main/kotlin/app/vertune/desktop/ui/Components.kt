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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vertune.desktop.data.Library
import app.vertune.desktop.data.Track
import app.vertune.desktop.player.PlayerController
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Artwork(url: String?, size: Dp, shape: Shape = RoundedCornerShape(6.dp), modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (url != null) AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 20.dp, bottom = 10.dp)
    )
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun Message(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/**
 * One song in a list. Click plays it (with the rest of [queue] after it when given,
 * or a radio of similar songs when not); right-click or ⋮ shows more options.
 */
@Composable
fun SongRow(
    track: Track,
    nav: Navigator,
    queue: List<Track>? = null,
    index: Int = 0,
    leading: (@Composable () -> Unit)? = null,
    trailingText: String? = track.durationText,
    subtitle: String? = listOfNotNull(track.artists, track.album).joinToString(" • ").ifBlank { null }
) {
    val current by PlayerController.current.collectAsState()
    val isCurrent = current?.id == track.id
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val liked = rememberLiked(track.id)
    var menu by remember { mutableStateOf(false) }

    val play = {
        if (queue != null) PlayerController.play(queue, index) else PlayerController.playWithRadio(track)
    }

    ContextMenuArea(items = { songMenuItems(track, nav) }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        hovered -> MaterialTheme.colorScheme.surfaceVariant
                        else -> Color.Transparent
                    }
                )
                .hoverable(interaction)
                .clickable(onClick = play)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            leading?.invoke()
            Artwork(track.thumbnail, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle != null) Text(
                    (if (track.explicit) "🅴 " else "") + subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { Library.setLiked(track, !liked) }) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (liked) "Unlike" else "Like",
                    tint = if (liked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (hovered) 1f else 0.4f)
                )
            }
            if (trailingText != null) Text(
                trailingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    songMenuItems(track, nav).forEach { item ->
                        DropdownMenuItem(text = { Text(item.label) }, onClick = {
                            menu = false
                            item.onClick()
                        })
                    }
                }
            }
        }
    }
}

fun songMenuItems(track: Track, nav: Navigator): List<ContextMenuItem> = buildList {
    add(ContextMenuItem("Play next") { PlayerController.playNext(track) })
    add(ContextMenuItem("Add to queue") { PlayerController.addToQueue(track) })
    add(ContextMenuItem("Start radio") { PlayerController.playWithRadio(track) })
    track.albumId?.let { id -> add(ContextMenuItem("Go to album") { nav.go(Screen.Album(id)) }) }
    track.artistId?.let { id -> add(ContextMenuItem("Go to artist") { nav.go(Screen.Artist(id)) }) }
    add(
        ContextMenuItem("Copy YouTube Music link") {
            val sel = java.awt.datatransfer.StringSelection("https://music.youtube.com/watch?v=${track.id}")
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, null)
        }
    )
}

/** A square card for albums, playlists and artists, used in horizontal rows. */
@Composable
fun Card(
    title: String,
    subtitle: String?,
    image: String?,
    round: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        Modifier
            .width(170.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Artwork(image, 150.dp, if (round) CircleShape else RoundedCornerShape(8.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        if (subtitle != null) Text(
            subtitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun <T> CardRow(items: List<T>, card: @Composable (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(end = 16.dp)) {
        items(items) { card(it) }
    }
}

/** Runs [block] once per [key] and keeps its result; shows loading/error states. */
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
        is LoadState.Error -> Message("Couldn't load: $message")
        is LoadState.Done -> content(value)
    }
}

@Composable
fun PillButton(text: String, primary: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp)
    ) {
        Text(
            text,
            color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun OnLaunch(block: suspend () -> Unit) = LaunchedEffect(Unit) { block() }
