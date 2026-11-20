package app.vertune.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vertune.desktop.data.Library
import app.vertune.desktop.player.PlayerController

/**
 * The phone's full player (what opens when you tap the mini player), laid out like the phone in
 * landscape: big cover on the left; title, rounded transport buttons, like button and seek bar
 * on the right; and a bar along the bottom with repeat, the queue and more options.
 */
@Composable
fun NowPlaying(nav: Navigator) {
    val palette = LocalPalette.current
    val track by PlayerController.current.collectAsState()
    val playing by PlayerController.isPlaying.collectAsState()
    val loading by PlayerController.isLoading.collectAsState()
    val position by PlayerController.positionMs.collectAsState()
    val duration by PlayerController.durationMs.collectAsState()
    val error by PlayerController.error.collectAsState()
    val repeat by PlayerController.repeat.collectAsState()
    var dragging by remember { mutableStateOf<Float?>(null) }
    val t = track ?: return

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background1)
            // Swallow clicks so they don't reach the page underneath.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                AppIconButton(
                    "chevron_down", palette.text, { nav.showPlayer = false },
                    Modifier.padding(start = 16.dp, top = 16.dp)
                )
                Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 48.dp, vertical = 24.dp)) {
                    // Cover
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Thumbnail(t.largeThumbnail, minOf(maxWidth, maxHeight, 560.dp), RoundedCornerShape(8.dp))
                    }
                    Spacer(Modifier.width(48.dp))
                    // Controls
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f).fillMaxHeight().widthIn(max = 520.dp)
                    ) {
                        BasicText(
                            t.title,
                            style = Type.xxl.semiBold.color(palette.text).copy(textAlign = TextAlign.Center),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(enabled = t.albumId != null) {
                                t.albumId?.let { nav.showPlayer = false; nav.go(Screen.Album(it)) }
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText(
                                error ?: t.artists.orEmpty(),
                                style = Type.l.semiBold.color(if (error != null) palette.red else palette.textSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable(enabled = t.artistId != null) {
                                    t.artistId?.let { nav.showPlayer = false; nav.go(Screen.Artist(it)) }
                                }
                            )
                            if (t.explicit) {
                                Spacer(Modifier.width(8.dp))
                                AppIcon("explicit", palette.textSecondary, size = 18.dp)
                            }
                        }
                        Spacer(Modifier.height(36.dp))

                        // Previous, play/pause, next: the phone's rounded buttons.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PillButton("play_skip_back", palette.background2, palette.text, PlayerController::previous, Modifier.width(80.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(BUTTON_HEIGHT)
                                    .clip(RoundedCornerShape(BUTTON_HEIGHT / 2))
                                    .background(palette.accent)
                                    .clickable(onClick = PlayerController::togglePlay)
                            ) {
                                if (loading) CircularProgressIndicator(color = palette.onAccent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                                else AppIcon(if (playing) "pause" else "play", palette.onAccent, size = 28.dp)
                            }
                            PillButton("play_skip_forward", palette.background2, palette.text, PlayerController::next, Modifier.width(80.dp))
                        }
                        Spacer(Modifier.height(24.dp))

                        // Like button and seek bar.
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            val liked = rememberLiked(t.id)
                            PillButton(
                                if (liked) "heart" else "heart_outline",
                                palette.background2,
                                if (liked) palette.red else palette.text,
                                { Library.setLiked(t, !liked) },
                                Modifier.width(BUTTON_HEIGHT)
                            )
                            Spacer(Modifier.width(20.dp))
                            Column(Modifier.weight(1f)) {
                                ThinSlider(
                                    value = dragging ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                                    onChange = { dragging = it },
                                    onChangeFinished = {
                                        dragging?.let { PlayerController.seekTo((it * duration).toLong()) }
                                        dragging = null
                                    },
                                    enabled = duration > 0,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(Modifier.fillMaxWidth()) {
                                    val shown = dragging?.let { (it * duration).toLong() } ?: position
                                    BasicText(formatTime(shown), style = Type.xxs.semiBold.color(palette.textSecondary))
                                    Spacer(Modifier.weight(1f))
                                    BasicText(formatTime(duration), style = Type.xxs.semiBold.color(palette.textSecondary))
                                }
                            }
                        }
                    }
                }
            }
            if (nav.showQueue) QueuePanel(nav)
        }

        // The phone's bottom bar.
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(palette.background2)
                .padding(horizontal = 16.dp)
        ) {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                AppIconButton(
                    if (repeat == PlayerController.Repeat.One) "repeat_on" else "repeat",
                    if (repeat == PlayerController.Repeat.Off) palette.textDisabled else palette.text,
                    PlayerController::cycleRepeat,
                    size = 20.dp
                )
                AppIconButton("shuffle", palette.textDisabled, PlayerController::shuffleUpcoming, size = 20.dp)
            }
            AppIconButton(
                "playlist",
                if (nav.showQueue) palette.text else palette.textSecondary,
                { nav.showQueue = !nav.showQueue },
                Modifier.align(Alignment.Center),
                size = 22.dp
            )
            Box(Modifier.align(Alignment.CenterEnd)) {
                var menu by remember { mutableStateOf(false) }
                AppIconButton("ellipsis_horizontal", palette.text, { menu = true }, size = 22.dp)
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    modifier = Modifier.background(palette.surface)
                ) {
                    songMenuItems(t, nav).forEach { item ->
                        DropdownMenuItem(
                            text = { BasicText(item.label, style = Type.xs.medium.color(palette.text)) },
                            onClick = {
                                menu = false
                                if (item.label.startsWith("Go to") || item.label.startsWith("More of")) nav.showPlayer = false
                                item.onClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PillButton(
    icon: String,
    background: androidx.compose.ui.graphics.Color,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(BUTTON_HEIGHT)
            .clip(RoundedCornerShape(BUTTON_HEIGHT / 2))
            .background(background)
            .clickable(onClick = onClick)
    ) {
        AppIcon(icon, tint, size = 22.dp)
    }
}

private val BUTTON_HEIGHT = 64.dp
