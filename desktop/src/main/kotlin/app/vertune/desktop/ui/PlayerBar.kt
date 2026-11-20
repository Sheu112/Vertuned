package app.vertune.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vertune.desktop.data.Library
import app.vertune.desktop.data.Settings
import app.vertune.desktop.player.PlayerController

@Composable
fun PlayerBar(nav: Navigator) {
    val track by PlayerController.current.collectAsState()
    val playing by PlayerController.isPlaying.collectAsState()
    val loading by PlayerController.isLoading.collectAsState()
    val position by PlayerController.positionMs.collectAsState()
    val duration by PlayerController.durationMs.collectAsState()
    val error by PlayerController.error.collectAsState()
    val repeat by PlayerController.repeat.collectAsState()
    val volume by Settings.volume.flow.collectAsState()

    // While dragging the slider, show the drag position instead of the playing position.
    var dragging by remember { mutableStateOf<Float?>(null) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp)
    ) {
        // Now playing
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            val t = track
            if (t != null) {
                Artwork(t.thumbnail, 56.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Text(
                        t.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(enabled = t.albumId != null) {
                            t.albumId?.let { nav.go(Screen.Album(it)) }
                        }
                    )
                    Text(
                        error ?: t.artists.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable(enabled = t.artistId != null) {
                            t.artistId?.let { nav.go(Screen.Artist(it)) }
                        }
                    )
                }
                val liked = rememberLiked(t.id)
                IconButton(onClick = { Library.setLiked(t, !liked) }) {
                    Icon(
                        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        if (liked) "Unlike" else "Like",
                        tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Transport + progress
        Column(Modifier.weight(1.4f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = PlayerController::shuffleUpcoming) {
                    Icon(Icons.Filled.Shuffle, "Shuffle upcoming", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = PlayerController::previous) { Icon(Icons.Filled.SkipPrevious, "Previous") }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface)
                        .clickable(onClick = PlayerController::togglePlay),
                    contentAlignment = Alignment.Center
                ) {
                    if (loading && playing) CircularProgressIndicator(
                        Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.background,
                        strokeWidth = 2.dp
                    ) else Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (playing) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.background
                    )
                }
                IconButton(onClick = PlayerController::next) { Icon(Icons.Filled.SkipNext, "Next") }
                IconButton(onClick = PlayerController::cycleRepeat) {
                    Icon(
                        if (repeat == PlayerController.Repeat.One) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        "Repeat",
                        tint = if (repeat == PlayerController.Repeat.Off) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val shown = dragging?.let { (it * duration).toLong() } ?: position
                Text(formatTime(shown), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = if (duration > 0) (dragging ?: (position.toFloat() / duration)).coerceIn(0f, 1f) else 0f,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { PlayerController.seekTo((it * duration).toLong()) }
                        dragging = null
                    },
                    enabled = track != null && duration > 0,
                    colors = sliderColors(),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(20.dp)
                )
                Text(formatTime(duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Volume + queue
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, "Volume", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = volume,
                onValueChange = { Settings.volume.value = it },
                colors = sliderColors(),
                modifier = Modifier.width(120.dp).padding(horizontal = 8.dp)
            )
            IconButton(onClick = { nav.showQueue = !nav.showQueue }) {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    "Queue",
                    tint = if (nav.showQueue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.onSurface,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.outline
)

@Composable
fun QueuePanel(nav: Navigator) {
    val queue by PlayerController.queue.collectAsState()
    val index by PlayerController.index.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(index) { if (index > 0) listState.animateScrollToItem((index - 1).coerceAtLeast(0)) }

    Column(Modifier.width(340.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Queue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                "Clear",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = PlayerController::clearQueue).padding(6.dp)
            )
        }
        if (queue.isEmpty()) Message("Nothing queued")
        LazyColumn(state = listState) {
            itemsIndexed(queue, key = { i, t -> "$i-${t.id}" }) { i, t ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (i == index) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)
                        .clickable { PlayerController.playAt(i) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Artwork(t.thumbnail, 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            t.artists.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (i != index) IconButton(onClick = { PlayerController.removeAt(i) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
