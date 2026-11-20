package app.vertune.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vertune.desktop.data.Library
import app.vertune.desktop.data.Settings
import app.vertune.desktop.player.PlayerController

/**
 * The phone's collapsed player, widened for a Mac window: tinted card with rounded top corners,
 * the played part filled with a lighter tint, and the controls the phone has in its full player.
 */
@Composable
fun PlayerBar(nav: Navigator) {
    val palette = LocalPalette.current
    val track by PlayerController.current.collectAsState()
    val playing by PlayerController.isPlaying.collectAsState()
    val loading by PlayerController.isLoading.collectAsState()
    val position by PlayerController.positionMs.collectAsState()
    val duration by PlayerController.durationMs.collectAsState()
    val error by PlayerController.error.collectAsState()
    val repeat by PlayerController.repeat.collectAsState()
    val volume by Settings.volume.flow.collectAsState()

    // While dragging the seek bar, show the drag position instead of the playing position.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val progress = dragging ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    val t = track ?: return

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(palette.background1)
    ) {
        ThinSlider(
            value = progress,
            onChange = { dragging = it },
            onChangeFinished = {
                dragging?.let { PlayerController.seekTo((it * duration).toLong()) }
                dragging = null
            },
            enabled = duration > 0,
            height = 3.dp,
            modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.fillMaxWidth().height(BAR_HEIGHT)) {
            // The phone fills the played part of the collapsed player with background2.
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(palette.background2.copy(alpha = 0.6f)))

            // Now playing
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth(0.32f).padding(start = 12.dp)
            ) {
                Thumbnail(t.thumbnail, 48.dp, RoundedCornerShape(5.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    BasicText(
                        t.title,
                        style = Type.xs.semiBold.color(palette.text),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = t.albumId != null) { t.albumId?.let { nav.go(Screen.Album(it)) } }
                    )
                    BasicText(
                        error ?: t.artists.orEmpty(),
                        style = Type.xs.semiBold.color(if (error != null) palette.red else palette.textSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = t.artistId != null) { t.artistId?.let { nav.go(Screen.Artist(it)) } }
                    )
                }
                val liked = rememberLiked(t.id)
                AppIconButton(
                    if (liked) "heart" else "heart_outline",
                    if (liked) palette.accent else palette.textDisabled,
                    { Library.setLiked(t, !liked) },
                    size = 18.dp
                )
            }

            // Transport
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                AppIconButton("shuffle", palette.textSecondary, PlayerController::shuffleUpcoming, size = 18.dp)
                AppIconButton("play_skip_back", palette.text, PlayerController::previous)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.accent)
                        .clickable(onClick = PlayerController::togglePlay)
                ) {
                    if (loading) CircularProgressIndicator(
                        color = palette.onAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    ) else AppIcon(if (playing) "pause" else "play", palette.onAccent, size = 22.dp)
                }
                AppIconButton("play_skip_forward", palette.text, PlayerController::next)
                AppIconButton(
                    if (repeat == PlayerController.Repeat.One) "repeat_on" else "repeat",
                    if (repeat == PlayerController.Repeat.Off) palette.textSecondary else palette.accent,
                    PlayerController::cycleRepeat,
                    size = 18.dp
                )
            }

            // Time, volume and queue
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)
            ) {
                val shown = dragging?.let { (it * duration).toLong() } ?: position
                BasicText(
                    if (loading && duration <= 0) "Loading…" else "${formatTime(shown)} / ${formatTime(duration)}",
                    style = Type.xxs.semiBold.color(palette.textSecondary)
                )
                Spacer(Modifier.width(4.dp))
                AppIcon("volume_up", palette.textSecondary, size = 18.dp)
                ThinSlider(
                    value = volume,
                    onChange = { Settings.volume.value = it },
                    modifier = Modifier.width(96.dp)
                )
                AppIconButton(
                    "playlist",
                    if (nav.showQueue) palette.accent else palette.textSecondary,
                    { nav.showQueue = !nav.showQueue },
                    size = 20.dp
                )
            }
        }
    }
}

/** A slim slider in the phone's style: a thin bar that you click or drag. */
@Composable
private fun ThinSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onChangeFinished: () -> Unit = {},
    enabled: Boolean = true,
    height: Dp = 4.dp
) {
    val palette = LocalPalette.current
    var width by remember { mutableStateOf(1) }
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onChangeFinished)
    fun at(x: Float) = (x / width).coerceIn(0f, 1f)
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .height(16.dp.coerceAtLeast(height))
            .let { m ->
                if (!enabled) m else m
                    .pointerInput(Unit) {
                        width = size.width.coerceAtLeast(1)
                        detectTapGestures { change(at(it.x)); finished() }
                    }
                    .pointerInput(Unit) {
                        width = size.width.coerceAtLeast(1)
                        detectHorizontalDragGestures(
                            onDragStart = { change(at(it.x)) },
                            onDragEnd = { finished() },
                            onDragCancel = { finished() }
                        ) { c, _ -> change(at(c.position.x)) }
                    }
            }
    ) {
        Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(palette.textDisabled.copy(alpha = 0.5f)))
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(height).clip(CircleShape).background(palette.text))
    }
}

/** The queue, shown on the right like the phone's queue sheet. */
@Composable
fun QueuePanel(nav: Navigator) {
    val palette = LocalPalette.current
    val queue by PlayerController.queue.collectAsState()
    val index by PlayerController.index.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(index) { if (index > 0) listState.animateScrollToItem((index - 1).coerceAtLeast(0)) }

    Column(
        Modifier
            .padding(top = 8.dp, end = 8.dp, bottom = 8.dp)
            .width(340.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.background1)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Queue", style = Type.l.medium.color(palette.text), modifier = Modifier.weight(1f))
            BasicText("${queue.size} songs", style = Type.xxs.medium.color(palette.textSecondary))
            Spacer(Modifier.width(8.dp))
            SecondaryTextButton("Clear", PlayerController::clearQueue, enabled = queue.size > 1)
            AppIconButton("close", palette.textSecondary, { nav.showQueue = false }, size = 16.dp)
        }
        if (queue.isEmpty()) Message("Nothing queued")
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(queue, key = { i, t -> "$i-${t.id}" }) { i, t ->
                val current = i == index
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (current) palette.background2 else palette.background1)
                        .clickable { PlayerController.playAt(i) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Thumbnail(t.thumbnail, 44.dp, RoundedCornerShape(5.dp)) {
                        if (current) Box(
                            Modifier.fillMaxSize().background(palette.background0.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) { AppIcon("equalizer", palette.text, size = 20.dp) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(t.title, style = Type.xs.semiBold.color(palette.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText(t.artists.orEmpty(), style = Type.xs.semiBold.color(palette.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!current) AppIconButton("close", palette.textDisabled, { PlayerController.removeAt(i) }, size = 14.dp)
                }
            }
        }
    }
}

private val BAR_HEIGHT = 72.dp
