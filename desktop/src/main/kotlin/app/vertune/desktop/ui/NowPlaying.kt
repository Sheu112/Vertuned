package app.vertune.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import app.vertune.desktop.data.Library
import app.vertune.desktop.data.Settings
import app.vertune.desktop.data.Track
import app.vertune.desktop.player.AudioEffects
import app.vertune.desktop.player.LyricsLoader
import app.vitune.providers.wordsync.WordLyrics
import app.vertune.desktop.player.PlayerController
import kotlinx.coroutines.delay

/**
 * The phone's full player (Player.kt and Controls.kt), laid out like the phone in landscape:
 * big cover on the left (click it for lyrics), title and controls on the right in the "New" or
 * "Classic" layout, and the phone's bottom bar with the loop button, the queue and the menu.
 */
@Composable
fun NowPlaying(nav: Navigator) {
    CompositionLocalProvider(LocalPalette provides LocalPalette.current.forPlayer()) {
        NowPlayingContent(nav)
    }
}

@Composable
private fun NowPlayingContent(nav: Navigator) {
    val palette = LocalPalette.current
    val track by PlayerController.current.collectAsState()
    val repeat by PlayerController.repeat.collectAsState()
    val layout by Settings.playerLayout.flow.collectAsState()
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
                    Modifier.padding(start = 16.dp, top = TITLE_BAR_HEIGHT + 8.dp)
                )
                Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 48.dp, vertical = 24.dp)) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        CoverWithLyrics(t, minOf(maxWidth, maxHeight, 640.dp))
                    }
                    Spacer(Modifier.width(48.dp))
                    Box(Modifier.weight(1f).fillMaxHeight().widthIn(max = 560.dp), contentAlignment = Alignment.Center) {
                        when (layout) {
                            Settings.PlayerLayout.New -> ModernControls(t, nav)
                            Settings.PlayerLayout.Classic -> ClassicControls(t, nav)
                        }
                    }
                }
            }
            if (nav.showQueue) QueuePanel(nav)
        }

        // The phone's bar under the player: loop the song, open the queue, more options.
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(palette.background2)
                .padding(horizontal = 12.dp)
        ) {
            if (layout == Settings.PlayerLayout.New) AppIconButton(
                "infinite",
                if (repeat == PlayerController.Repeat.One) palette.text else palette.textDisabled,
                { PlayerController.setTrackLoop(repeat != PlayerController.Repeat.One) },
                Modifier.align(Alignment.CenterStart),
                size = 20.dp
            )
            AppIconButton(
                if (nav.showQueue) "chevron_down" else "chevron_up",
                palette.text,
                { nav.showQueue = !nav.showQueue },
                Modifier.align(Alignment.Center),
                size = 18.dp
            )
            PlayerMenuButton(t, nav, Modifier.align(Alignment.CenterEnd))
        }
    }
}

// ---------------------------------------------------------------- Title and artist

@Composable
private fun MediaInfo(t: Track, nav: Navigator) {
    val palette = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(t.title, transitionSpec = { fadeIn() togetherWith fadeOut() }) { title ->
            BasicText(
                title,
                style = Type.l.bold.color(palette.text).copy(textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(enabled = t.albumId != null) {
                    t.albumId?.let { nav.showPlayer = false; nav.go(Screen.Album(it)) }
                }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                t.artists.orEmpty(),
                style = Type.s.bold.color(palette.textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).clickable(enabled = t.artistId != null) {
                    t.artistId?.let { nav.showPlayer = false; nav.go(Screen.Artist(it)) }
                }
            )
            if (t.explicit) {
                Spacer(Modifier.width(4.dp))
                AppIcon("explicit", palette.text, size = 15.dp)
            }
        }
        val error by PlayerController.error.collectAsState()
        error?.let {
            Spacer(Modifier.height(4.dp))
            BasicText(it, style = Type.xs.medium.color(palette.red), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- Controls

@Composable
private fun playRadius(): Dp {
    val playing by PlayerController.isPlaying.collectAsState()
    return animateDpAsState(if (playing) 16.dp else 32.dp, tween(100, easing = LinearEasing)).value
}

@Composable
private fun PlayerSeekBar() {
    val position by PlayerController.positionMs.collectAsState()
    val duration by PlayerController.durationMs.collectAsState()
    val playing by PlayerController.isPlaying.collectAsState()
    SeekBar(
        position = position,
        duration = duration,
        onSeek = { PlayerController.seekTo(it) },
        isActive = playing,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ModernControls(t: Track, nav: Navigator) {
    val showLike by Settings.showLike.flow.collectAsState()
    val radius = playRadius()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
        MediaInfo(t, nav)
        Spacer(Modifier.height(32.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (showLike) 4.dp else 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (showLike) SkipButton("play_skip_back", PlayerController::previous, -PRESS_OFFSET)
            PlayButton(radius, Modifier.height(CONTROL_HEIGHT).weight(if (showLike) 3f else 4f))
            SkipButton("play_skip_forward", PlayerController::next, PRESS_OFFSET)
        }
        Spacer(Modifier.height(24.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (showLike) {
                val liked = rememberLiked(t.id)
                BigIconButton(if (liked) "heart" else "heart_outline", { Library.setLiked(t, !liked) }, Modifier.weight(1f))
            } else SkipButton("play_skip_back", PlayerController::previous, -PRESS_OFFSET)
            Column(Modifier.weight(4f)) { PlayerSeekBar() }
        }
    }
}

@Composable
private fun ClassicControls(t: Track, nav: Navigator) {
    val palette = LocalPalette.current
    val repeat by PlayerController.repeat.collectAsState()
    val playing by PlayerController.isPlaying.collectAsState()
    val loading by PlayerController.isLoading.collectAsState()
    val radius = playRadius()
    val liked = rememberLiked(t.id)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
        MediaInfo(t, nav)
        Spacer(Modifier.height(32.dp))
        PlayerSeekBar()
        Spacer(Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AppIconButton(if (liked) "heart" else "heart_outline", if (palette.isDefault) palette.red else palette.accent, { Library.setLiked(t, !liked) })
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AppIconButton("play_skip_back", palette.text, PlayerController::previous)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(radius))
                    .clickable(onClick = PlayerController::togglePlay)
                    .background(palette.background2)
                    .size(64.dp)
            ) {
                if (loading) CircularProgressIndicator(color = palette.text, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                else AppIcon(if (playing) "pause" else "play", palette.text, size = 32.dp)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AppIconButton("play_skip_forward", palette.text, PlayerController::next)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AppIconButton(
                    "infinite",
                    if (repeat == PlayerController.Repeat.One) palette.text else palette.textDisabled,
                    { PlayerController.setTrackLoop(repeat != PlayerController.Repeat.One) }
                )
            }
        }
    }
}

@Composable
private fun PlayButton(radius: Dp, modifier: Modifier) {
    val palette = LocalPalette.current
    val playing by PlayerController.isPlaying.collectAsState()
    val loading by PlayerController.isLoading.collectAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .clickable(onClick = PlayerController::togglePlay)
            .background(palette.accent)
    ) {
        if (loading) CircularProgressIndicator(color = palette.onAccent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        else AppIcon(if (playing) "pause" else "play", palette.onAccent, size = 32.dp)
    }
}

/** The phone's skip buttons nudge sideways while pressed. */
@Composable
private fun RowScope.SkipButton(icon: String, onClick: () -> Unit, pressOffset: Dp) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val offset by animateDpAsState(if (pressed) pressOffset else 0.dp)
    BigIconButton(
        icon,
        null,
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(32.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .offset { IntOffset(offset.roundToPx(), 0) }
    )
}

@Composable
private fun BigIconButton(icon: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .background(palette.background2)
            .height(CONTROL_HEIGHT)
    ) {
        AppIcon(icon, palette.text, size = 24.dp)
    }
}

// ---------------------------------------------------------------- Cover and lyrics

@Composable
private fun CoverWithLyrics(t: Track, size: Dp) {
    val showLyrics by Settings.showLyrics.flow.collectAsState()
    Box(
        Modifier
            .size(size)
            .clip(Dimens.thumbnailShape)
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                Settings.showLyrics.value = !showLyrics
            }
    ) {
        Thumbnail(t.thumbnailSized(1200), size, Dimens.thumbnailShape, fallback = t.thumbnailFallback())
        AnimatedVisibility(showLyrics, enter = fadeIn(), exit = fadeOut()) { LyricsOverlay(t) }
    }
}

@Composable
private fun LyricsOverlay(t: Track) {
    val palette = LocalPalette.current
    val synced by Settings.showSyncedLyrics.flow.collectAsState()
    val lyrics by produceState<Library.SavedLyrics?>(null, t.id) {
        value = null
        // Wait for the song's length, which helps find the right synced lyrics.
        var d = PlayerController.durationMs.value
        var waited = 0
        while (d <= 0 && waited < 50) { delay(100); waited++; d = PlayerController.durationMs.value }
        value = runCatching { LyricsLoader.load(t, d) }.getOrElse { Library.SavedLyrics("", "") }
    }
    val lines = remember(lyrics?.synced) { LyricsLoader.parseSynced(lyrics?.synced) }

    // Word-by-word lyrics (NetEase, then QQ Music), looked up once per song after the rest.
    val wordMode by Settings.showWordLyrics.flow.collectAsState()
    val wordText by produceState<String?>(null, t.id, lyrics, synced, wordMode) {
        val saved = lyrics ?: return@produceState
        value = saved.word
        if (saved.word == null && synced && wordMode) {
            value = runCatching { LyricsLoader.loadWord(t, PlayerController.durationMs.value) }.getOrNull()
        }
    }
    val words = remember(wordText, wordMode) { if (wordMode) LyricsLoader.parseWord(wordText) else null }
    val missing = lyrics != null && (if (synced) lines == null && words == null else lyrics?.fixed.isNullOrBlank())

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
        val height = maxHeight
        when {
            lyrics == null -> Loading()
            missing -> BasicText(
                if (synced) str("synchronized_lyrics_not_available", "Synchronized lyrics are not available for this song")
                else str("lyrics_not_available", "Lyrics are not available for this song"),
                style = Type.xs.medium.color(Color.White).copy(textAlign = TextAlign.Center),
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(0.4f)).padding(8.dp)
            )
            synced && words != null -> WordSyncedLyrics(words, height)
            synced && lines != null -> SyncedLyrics(lines, height)
            else -> BasicText(
                lyrics?.fixed.orEmpty(),
                style = lyricsText().color(Color.White).copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = height / 4, horizontal = 32.dp)
            )
        }

        // The phone's lyrics menu: switch between synced and plain, or search online.
        Box(Modifier.align(Alignment.BottomEnd)) {
            var menu by remember { mutableStateOf(false) }
            var sizeMenu by remember { mutableStateOf(false) }
            AppIconButton("ellipsis_horizontal", Color.White, { menu = true }, size = 20.dp)
            DarkMenu(sizeMenu, { sizeMenu = false }) {
                val current by Settings.lyricsTextSize.flow.collectAsState()
                for ((key, fallback, size) in LYRICS_SIZES) {
                    MenuItem((if (size == current) "✓ " else "") + str(key, fallback), null) {
                        sizeMenu = false
                        Settings.lyricsTextSize.value = size
                    }
                }
            }
            DarkMenu(menu, { menu = false }) {
                MenuItem(str("lyrics_text_size", "Text size"), "text") { menu = false; sizeMenu = true }
                MenuItem(
                    if (synced) str("show_unsynchronized_lyrics", "Show unsynchronized lyrics")
                    else str("show_synchronized_lyrics", "Show synchronized lyrics"),
                    "time"
                ) { menu = false; Settings.showSyncedLyrics.value = !synced }
                if (synced) MenuItem(
                    if (wordMode) str("show_line_by_line_lyrics", "Show line-by-line lyrics")
                    else str("show_word_by_word_lyrics", "Show word-by-word lyrics"),
                    "text"
                ) { menu = false; Settings.showWordLyrics.value = !wordMode }
                if (synced) {
                    val follow by Settings.lyricsFollowLine.flow.collectAsState()
                    MenuItem(
                        if (follow) str("lyrics_scroll_freely", "Scroll lyrics freely")
                        else str("lyrics_follow_line", "Follow the current line"),
                        "sync"
                    ) { menu = false; Settings.lyricsFollowLine.value = !follow }
                }
                MenuItem(str("search_lyrics_online", "Search lyrics online"), "search") {
                    menu = false
                    openUrl("https://www.google.com/search?q=" + java.net.URLEncoder.encode("${t.title} ${t.artists.orEmpty()} lyrics", "UTF-8"))
                }
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lines: List<Pair<Long, String>>, height: Dp) {
    val palette = LocalPalette.current
    val position by PlayerController.positionMs.collectAsState()
    val index = lines.indexOfLast { it.first <= position + LYRICS_LEAD_MS }.coerceAtLeast(-1)
    val follow by Settings.lyricsFollowLine.flow.collectAsState()
    val state = rememberLazyListState()
    // Before the first line starts, keep the first line in its spot (not yet lit) so the lyrics show.
    LaunchedEffect(index, follow) { if (follow) state.animateScrollToItem(index.coerceAtLeast(0) + 1, -(height.value / 3).toInt()) }
    LazyColumn(state = state, userScrollEnabled = !follow, horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
        item { Spacer(Modifier.height(height)) }
        itemsIndexed(lines) { i, (_, line) ->
            val color by animateColorAsState(if (i == index) Color.White else palette.textDisabled)
            if (line.isBlank()) AppIcon("musical_notes", color, Modifier.padding(vertical = 4.dp, horizontal = 32.dp), size = 16.dp)
            else BasicText(
                line,
                style = lyricsText().color(color).copy(textAlign = TextAlign.Center),
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 32.dp)
            )
        }
        item { Spacer(Modifier.height(height)) }
    }
}

/** Like [SyncedLyrics], but the current line lights up word by word as it's sung. */
@Composable
private fun WordSyncedLyrics(lyrics: WordLyrics, height: Dp) {
    val palette = LocalPalette.current
    val reported by PlayerController.positionMs.collectAsState()
    val playing by PlayerController.isPlaying.collectAsState()
    // The player reports its position a few times a second; fill in the frames between.
    var position by remember { mutableStateOf(reported) }
    LaunchedEffect(reported, playing) {
        val base = reported
        val since = System.nanoTime()
        position = base
        if (!playing) return@LaunchedEffect
        while (true) {
            withFrameMillis { }
            position = base + (System.nanoTime() - since) / 1_000_000
        }
    }

    val lines = lyrics.lines
    val index = lines.indexOfLast { it.start <= position + LYRICS_LEAD_MS }.coerceAtLeast(-1)
    val follow by Settings.lyricsFollowLine.flow.collectAsState()
    val state = rememberLazyListState()
    // Before the first line starts, keep the first line in its spot (not yet lit) so the lyrics show.
    LaunchedEffect(index, follow) { if (follow) state.animateScrollToItem(index.coerceAtLeast(0) + 1, -(height.value / 3).toInt()) }
    LazyColumn(state = state, userScrollEnabled = !follow, horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
        item { Spacer(Modifier.height(height)) }
        itemsIndexed(lines) { i, line ->
            val color by animateColorAsState(if (i == index) Color.White else palette.textDisabled)
            val style = lyricsText().copy(textAlign = TextAlign.Center)
            val modifier = Modifier.padding(vertical = 4.dp, horizontal = 32.dp)
            if (i == index) BasicText(wordLine(line, position, Color.White, palette.textDisabled), style = style, modifier = modifier)
            else BasicText(line.text, style = style.color(color), modifier = modifier)
        }
        item { Spacer(Modifier.height(height)) }
    }
}

/** The line's words, each brightening from [unsung] to [sung] over its own duration. */
private fun wordLine(line: WordLyrics.Line, now: Long, sung: Color, unsung: Color) = buildAnnotatedString {
    line.words.forEachIndexed { i, word ->
        val progress = when {
            now >= word.end || word.duration <= 0 -> if (now >= word.start) 1f else 0f
            now <= word.start -> 0f
            else -> (now - word.start).toFloat() / word.duration
        }
        var text = word.text
        if (i == 0) text = text.trimStart()
        if (i == line.words.lastIndex) text = text.trimEnd()
        withStyle(SpanStyle(color = lerp(unsung, sung, progress))) { append(text) }
    }
}

// ---------------------------------------------------------------- Menu and dialogs

@Composable
private fun PlayerMenuButton(t: Track, nav: Navigator, modifier: Modifier) {
    val palette = LocalPalette.current
    var menu by remember { mutableStateOf(false) }
    var speedDialog by remember { mutableStateOf(false) }
    var sleepDialog by remember { mutableStateOf(false) }
    Box(modifier) {
        AppIconButton("ellipsis_horizontal", palette.text, { menu = true }, size = 20.dp)
        DarkMenu(menu, { menu = false }) {
            MenuItem(str("start_radio", "Start radio"), "radio") { menu = false; PlayerController.playWithRadio(t) }
            MenuItem(str("add_to_playlist", "Add to playlist"), "add") { menu = false; nav.addToPlaylist = t }
            MenuItem(str("playback_settings", "Playback settings"), "speed") { menu = false; speedDialog = true }
            MenuItem(str("sleep_timer", "Sleep timer"), "alarm") { menu = false; sleepDialog = true }
            MenuItem(str("pip", "Picture-in-picture"), "expand") { menu = false; nav.showMiniPlayer = true }
            t.albumId?.let { id ->
                MenuItem(str("go_to_album", "Go to album"), "disc") { menu = false; nav.showPlayer = false; nav.go(Screen.Album(id)) }
            }
            t.artistId?.let { id ->
                MenuItem(str("format_go_to_artist", "More of %s", t.artists ?: ""), "person") {
                    menu = false; nav.showPlayer = false; nav.go(Screen.Artist(id))
                }
            }
            MenuItem(str("pre_cache", "Pre cache"), "download") { menu = false; app.vertune.desktop.player.SongCache.precache(t) }
            MenuItem(str("watch_on_youtube", "Watch on YouTube"), "play") { menu = false; openUrl("https://www.youtube.com/watch?v=${t.id}") }
            MenuItem(str("open_in_youtube_music", "Open in YouTube Music"), "play") { menu = false; openUrl("https://music.youtube.com/watch?v=${t.id}") }
        }
    }
    if (speedDialog) PlaybackSettingsDialog { speedDialog = false }
    if (sleepDialog) SleepTimerDialog { sleepDialog = false }
}

@Composable
fun DarkMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(LocalPalette.current.background1)
    ) { content() }
}

@Composable
fun MenuItem(text: String, icon: String?, onClick: () -> Unit) {
    val palette = LocalPalette.current
    DropdownMenuItem(
        text = { BasicText(text, style = Type.xs.medium.color(palette.text)) },
        leadingIcon = icon?.let { { AppIcon(it, palette.text, size = 18.dp) } },
        onClick = onClick
    )
}

fun openUrl(url: String) {
    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
}

/** The phone's "Playback settings" dialog: speed and pitch. */
@Composable
private fun PlaybackSettingsDialog(onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    val speed by Settings.speed.flow.collectAsState()
    val pitch by Settings.pitch.flow.collectAsState()
    var newSpeed by remember { mutableStateOf(speed) }
    var newPitch by remember { mutableStateOf(pitch) }
    DialogFrame(onDismiss) {
        BasicText(str("playback_settings", "Playback settings"), style = Type.s.semiBold.color(palette.text))
        LabeledSlider(str("speed", "Speed"), "%.2fx".format(newSpeed), newSpeed, AudioEffects.MIN_RATE..2f, { newSpeed = it }) {
            Settings.speed.value = newSpeed; PlayerController.applyAudioSettings()
        }
        LabeledSlider(str("pitch", "Pitch"), "%.2fx".format(newPitch), newPitch, 0.5f..2f, { newPitch = it }) {
            Settings.pitch.value = newPitch; PlayerController.applyAudioSettings()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
            DialogTextButton(str("reset", "Reset"), {
                newSpeed = 1f; newPitch = 1f
                Settings.speed.value = 1f; Settings.pitch.value = 1f
                PlayerController.applyAudioSettings()
            })
            DialogTextButton(str("done", "Done"), onDismiss, primary = true)
        }
    }
}

@Composable
fun LabeledSlider(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onDone: () -> Unit
) {
    val palette = LocalPalette.current
    Column {
        BasicText("$title ($valueText)", style = Type.xs.semiBold.color(palette.text))
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.background2
            )
        )
    }
}

/** The phone's sleep timer: stop after some minutes, or at the end of the song. */
@Composable
private fun SleepTimerDialog(onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    val sleepAt by PlayerController.sleepAt.collectAsState()
    val atEnd by PlayerController.sleepAtEndOfSong.collectAsState()
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1000) } }
    DialogFrame(onDismiss) {
        BasicText(str("sleep_timer", "Sleep timer"), style = Type.s.semiBold.color(palette.text))
        when {
            sleepAt != null -> BasicText(
                formatTime(sleepAt!! - now) + " left",
                style = Type.xs.medium.color(palette.textSecondary)
            )
            atEnd -> BasicText(str("sleep_timer_until_song_end", "Until the end of the song"), style = Type.xs.medium.color(palette.textSecondary))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45, 60, 90, 120).forEach { m ->
                SecondaryTextButton("$m min", { PlayerController.setSleepTimer(m); onDismiss() })
            }
        }
        SecondaryTextButton(str("sleep_timer_until_song_end", "Until the end of the song"), { PlayerController.sleepAtEndOfSong(); onDismiss() })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
            if (sleepAt != null || atEnd) DialogTextButton(str("stop_sleep_timer", "Stop sleep timer"), { PlayerController.setSleepTimer(null); onDismiss() })
            DialogTextButton(str("cancel", "Cancel"), onDismiss)
        }
    }
}

private val CONTROL_HEIGHT = 64.dp
private val PRESS_OFFSET = 8.dp
private const val LYRICS_LEAD_MS = 50L

/** Lyrics text at the size picked in the lyrics menu (16 by default, a step up from the phone's 14). */
@Composable
private fun lyricsText(): androidx.compose.ui.text.TextStyle {
    val size by Settings.lyricsTextSize.flow.collectAsState()
    val base = Type.s.medium
    return base.copy(
        fontSize = size.sp,
        lineHeight = if (base.lineHeight.isSpecified) (size * 1.5f).sp else base.lineHeight
    )
}

/** The lyrics text sizes offered in the menu: (label key, fallback label, size). */
private val LYRICS_SIZES = listOf(
    Triple("lyrics_size_small", "Small", 14),
    Triple("lyrics_size_medium", "Medium", 16),
    Triple("lyrics_size_large", "Large", 19),
    Triple("lyrics_size_extra_large", "Extra large", 23)
)
