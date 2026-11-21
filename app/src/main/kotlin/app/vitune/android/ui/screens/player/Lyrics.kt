package app.vitune.android.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import app.vitune.android.Database
import app.vitune.android.LocalPlayerServiceBinder
import app.vitune.android.R
import app.vitune.android.models.Lyrics
import app.vitune.android.models.LyricsMode
import app.vitune.android.preferences.PlayerPreferences
import app.vitune.android.query
import app.vitune.android.service.LOCAL_KEY_PREFIX
import app.vitune.android.transaction
import app.vitune.android.ui.components.LocalMenuState
import app.vitune.android.ui.components.themed.CircularProgressIndicator
import app.vitune.android.ui.components.themed.DefaultDialog
import app.vitune.android.ui.components.themed.Menu
import app.vitune.android.ui.components.themed.MenuEntry
import app.vitune.android.ui.components.themed.TextField
import app.vitune.android.ui.components.themed.TextFieldDialog
import app.vitune.android.ui.components.themed.TextPlaceholder
import app.vitune.android.ui.components.themed.ValueSelectorDialogBody
import app.vitune.android.ui.modifiers.verticalFadingEdge
import app.vitune.android.utils.SynchronizedLyrics
import app.vitune.android.utils.SynchronizedLyricsState
import app.vitune.android.utils.center
import app.vitune.android.utils.color
import app.vitune.android.utils.isInPip
import app.vitune.android.utils.medium
import app.vitune.android.utils.semiBold
import app.vitune.android.utils.toast
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.onOverlay
import app.vitune.core.ui.onOverlayShimmer
import app.vitune.core.ui.overlay
import app.vitune.core.ui.utils.dp
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.bodies.NextBody
import app.vitune.providers.innertube.requests.lyrics
import app.vitune.providers.kugou.KuGou
import app.vitune.providers.lrclib.LrcLib
import app.vitune.providers.lrclib.LrcParser
import app.vitune.providers.lrclib.models.Track
import app.vitune.providers.lrclib.toLrcFile
import app.vitune.providers.wordsync.LyricsCredits
import app.vitune.providers.wordsync.WordLyrics
import app.vitune.providers.wordsync.WordSync
import com.valentinilk.shimmer.shimmer
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val UPDATE_DELAY = 50L

/** Lyrics text sizes offered in the lyrics menu (14 is the original size). */
private val LYRICS_SIZES = listOf(
    R.string.lyrics_size_small to 14,
    R.string.lyrics_size_medium to 16,
    R.string.lyrics_size_large to 19,
    R.string.lyrics_size_extra_large to 23
)

@Composable
fun Lyrics(
    mediaId: String,
    isDisplayed: Boolean,
    onDismiss: () -> Unit,
    mediaMetadataProvider: () -> MediaMetadata,
    durationProvider: () -> Long,
    ensureSongInserted: () -> Unit,
    modifier: Modifier = Modifier,
    onMenuLaunch: () -> Unit = { },
    onOpenDialog: (() -> Unit)? = null,
    shouldShowSynchronizedLyrics: Boolean = PlayerPreferences.isShowingSynchronizedLyrics,
    setShouldShowSynchronizedLyrics: (Boolean) -> Unit = {
        PlayerPreferences.isShowingSynchronizedLyrics = it
    },
    shouldKeepScreenAwake: Boolean = PlayerPreferences.lyricsKeepScreenAwake,
    shouldUpdateLyrics: Boolean = true,
    showControls: Boolean = true
) = AnimatedVisibility(
    visible = isDisplayed,
    enter = fadeIn(),
    exit = fadeOut()
) {
    val currentEnsureSongInserted by rememberUpdatedState(ensureSongInserted)
    val currentMediaMetadataProvider by rememberUpdatedState(mediaMetadataProvider)
    val currentDurationProvider by rememberUpdatedState(durationProvider)

    val (colorPalette, typography) = LocalAppearance.current
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val binder = LocalPlayerServiceBinder.current
    val density = LocalDensity.current
    val view = LocalView.current

    val pip = isInPip()

    var lyrics by remember { mutableStateOf<Lyrics?>(null) }
    // Each song remembers word-by-word, line-by-line or unsynchronized (word-by-word until changed).
    // A song with no timed lyrics at all falls back to unsynchronized unless that was picked on purpose.
    val chosenMode = lyrics?.mode
    val lyricsMode = chosenMode ?: LyricsMode.WORD
    val hasTimedLyrics = lyrics == null || lyrics?.synced?.isNotBlank() == true || lyrics?.word != ""
    @Suppress("NAME_SHADOWING")
    val shouldShowSynchronizedLyrics = lyricsMode != LyricsMode.UNSYNCED && (chosenMode != null || hasTimedLyrics)
    val wordByWord = lyricsMode == LyricsMode.WORD
    val setMode: (Int) -> Unit = { newMode ->
        lyrics?.let { current -> query { Database.upsert(current.copy(mode = newMode)) } }
    }
    val lyricsSize = PlayerPreferences.lyricsTextSize
    val lyricsStyle = typography.xs.copy(fontSize = lyricsSize.sp).center.medium
    val wordLyrics = remember(lyrics?.word, wordByWord) {
        if (wordByWord) WordLyrics.parseYrc(lyrics?.word) else null
    }

    val showSynchronizedLyrics = remember(shouldShowSynchronizedLyrics, lyrics, wordLyrics) {
        shouldShowSynchronizedLyrics && (wordLyrics != null || lyrics?.synced?.isBlank() != true)
    }

    var editing by remember(mediaId, shouldShowSynchronizedLyrics) { mutableStateOf(false) }
    var picking by remember(mediaId, shouldShowSynchronizedLyrics) { mutableStateOf(false) }
    var error by remember(mediaId, shouldShowSynchronizedLyrics) { mutableStateOf(false) }

    val text = remember(lyrics, showSynchronizedLyrics) {
        if (showSynchronizedLyrics) lyrics?.synced else lyrics?.fixed
    }
    var invalidLrc by remember(text) { mutableStateOf(false) }

    DisposableEffect(shouldKeepScreenAwake) {
        view.keepScreenOn = shouldKeepScreenAwake

        onDispose {
            view.keepScreenOn = false
        }
    }

    LaunchedEffect(mediaId, shouldShowSynchronizedLyrics) {
        runCatching {
            withContext(Dispatchers.IO) {
                Database
                    .lyrics(mediaId)
                    .distinctUntilChanged()
                    .cancellable()
                    .collect { currentLyrics ->
                        if (
                            !shouldUpdateLyrics ||
                            (currentLyrics?.fixed != null && currentLyrics.synced != null)
                        ) lyrics = currentLyrics
                        else {
                            val mediaMetadata = currentMediaMetadataProvider()
                            var duration =
                                withContext(Dispatchers.Main) { currentDurationProvider() }

                            while (duration == C.TIME_UNSET) {
                                delay(100.milliseconds)
                                duration =
                                    withContext(Dispatchers.Main) { currentDurationProvider() }
                            }

                            val album = mediaMetadata.albumTitle?.toString()
                            val artist = mediaMetadata.artist?.toString().orEmpty()
                            val title = mediaMetadata.title?.toString().orEmpty().let {
                                if (mediaId.startsWith(LOCAL_KEY_PREFIX)) it
                                    .substringBeforeLast('.')
                                    .trim()
                                else it
                            }

                            lyrics = null
                            error = false

                            val fixed = currentLyrics?.fixed ?: Innertube
                                .lyrics(NextBody(videoId = mediaId))
                                ?.getOrNull()
                                ?: LrcLib.bestLyrics(
                                    artist = artist,
                                    title = title,
                                    duration = duration.milliseconds,
                                    album = album,
                                    synced = false
                                )?.map { it?.text }?.getOrNull()

                            val synced = currentLyrics?.synced ?: LrcLib.bestLyrics(
                                artist = artist,
                                title = title,
                                duration = duration.milliseconds,
                                album = album
                            )?.map { it?.text }?.getOrNull() ?: LrcLib.bestLyrics(
                                artist = artist,
                                title = title.split("(")[0].trim(),
                                duration = duration.milliseconds,
                                album = album
                            )?.map { it?.text }?.getOrNull() ?: KuGou.lyrics(
                                artist = artist,
                                title = title,
                                duration = duration / 1000
                            )?.map { it?.value }?.getOrNull()

                            Lyrics(
                                songId = mediaId,
                                fixed = fixed.orEmpty(),
                                synced = synced.orEmpty(),
                                startTime = currentLyrics?.startTime,
                                word = currentLyrics?.word,
                                mode = currentLyrics?.mode
                            ).also {
                                this@withContext.ensureActive()

                                transaction {
                                    runCatching {
                                        currentEnsureSongInserted()
                                        Database.upsert(it)
                                    }
                                }
                            }
                        }

                        error =
                            (shouldShowSynchronizedLyrics && lyrics?.synced?.isBlank() == true) ||
                            (!shouldShowSynchronizedLyrics && lyrics?.fixed?.isBlank() == true)
                    }
            }
        }.exceptionOrNull()?.let {
            if (it is CancellationException) throw it
            else it.printStackTrace()
        }
    }

    // Word-by-word lyrics come from NetEase, then QQ Music, and are looked up once per song.
    val needsWordLyrics = shouldUpdateLyrics && wordByWord && shouldShowSynchronizedLyrics &&
        lyrics != null && lyrics?.word == null
    LaunchedEffect(mediaId, needsWordLyrics) {
        if (!needsWordLyrics) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                val mediaMetadata = currentMediaMetadataProvider()
                val duration = withContext(Dispatchers.Main) { currentDurationProvider() }
                val title = mediaMetadata.title?.toString().orEmpty().let {
                    if (mediaId.startsWith(LOCAL_KEY_PREFIX)) it.substringBeforeLast('.').trim() else it
                }
                val result = WordSync.lyrics(
                    title = title,
                    artist = mediaMetadata.artist?.toString().orEmpty(),
                    durationMs = if (duration == C.TIME_UNSET) 0L else duration
                )
                // A network failure leaves it unset so it's tried again next time.
                if (result == null || result.isFailure) return@withContext
                val found = result.getOrNull()?.lyrics?.serialize().orEmpty()
                ensureActive()
                lyrics?.let { current ->
                    transaction {
                        runCatching { Database.upsert(current.copy(word = found)) }
                    }
                }
            }
        }.exceptionOrNull()?.let {
            if (it is CancellationException) throw it
            else it.printStackTrace()
        }
    }

    if (editing) TextFieldDialog(
        hintText = stringResource(R.string.enter_lyrics),
        initialTextInput = (if (shouldShowSynchronizedLyrics) lyrics?.synced else lyrics?.fixed)
            .orEmpty(),
        singleLine = false,
        maxLines = 10,
        isTextInputValid = { true },
        onDismiss = { editing = false },
        onAccept = {
            transaction {
                runCatching {
                    currentEnsureSongInserted()

                    Database.upsert(
                        if (shouldShowSynchronizedLyrics) Lyrics(
                            songId = mediaId,
                            fixed = lyrics?.fixed,
                            synced = it,
                            startTime = lyrics?.startTime,
                            // Your own edit wins over word-by-word lyrics found online
                            word = "",
                            mode = lyrics?.mode
                        ) else Lyrics(
                            songId = mediaId,
                            fixed = it,
                            synced = lyrics?.synced,
                            startTime = lyrics?.startTime,
                            word = lyrics?.word,
                            mode = lyrics?.mode
                        )
                    )
                }
            }
        }
    )

    if (picking && shouldShowSynchronizedLyrics) {
        var query by rememberSaveable {
            mutableStateOf(
                currentMediaMetadataProvider().title?.toString().orEmpty().let {
                    if (mediaId.startsWith(LOCAL_KEY_PREFIX)) it
                        .substringBeforeLast('.')
                        .trim()
                    else it
                }
            )
        }

        LrcLibSearchDialog(
            query = query,
            setQuery = { query = it },
            onDismiss = { picking = false },
            onPick = {
                runCatching {
                    transaction {
                        Database.upsert(
                            Lyrics(
                                songId = mediaId,
                                fixed = lyrics?.fixed,
                                synced = it.syncedLyrics,
                                // A hand-picked LrcLib track wins over word-by-word lyrics
                                word = "",
                                mode = lyrics?.mode
                            )
                        )
                    }
                }
            }
        )
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            }
            .fillMaxSize()
            .background(colorPalette.overlay)
    ) {
        val animatedHeight by animateDpAsState(
            targetValue = this.maxHeight, // `this` needed, thanks Android Lint!
            label = ""
        )

        AnimatedVisibility(
            visible = error && !(shouldShowSynchronizedLyrics && wordLyrics != null),
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            BasicText(
                text = stringResource(
                    if (shouldShowSynchronizedLyrics) R.string.synchronized_lyrics_not_available
                    else R.string.lyrics_not_available
                ),
                style = typography.xs.center.medium.color(colorPalette.onOverlay),
                modifier = Modifier
                    .background(Color.Black.copy(0.4f))
                    .padding(all = 8.dp)
                    .fillMaxWidth(),
                maxLines = if (pip) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }

        AnimatedVisibility(
            visible = !text.isNullOrBlank() && !error && invalidLrc && shouldShowSynchronizedLyrics,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            BasicText(
                text = stringResource(R.string.invalid_synchronized_lyrics),
                style = typography.xs.center.medium.color(colorPalette.onOverlay),
                modifier = Modifier
                    .background(Color.Black.copy(0.4f))
                    .padding(all = 8.dp)
                    .fillMaxWidth(),
                maxLines = if (pip) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }

        val lyricsState = rememberSaveable(text, wordLyrics) {
            if (wordLyrics != null) SynchronizedLyricsState(
                sentences = wordLyrics.lines.associate { it.start to it.text },
                offset = 0L
            ) else {
                val file = lyrics?.synced?.takeIf { it.isNotBlank() }?.let {
                    LrcParser.parse(it)?.toLrcFile()
                }

                SynchronizedLyricsState(
                    // Leave out the credit lines lyrics sites put at the top
                    sentences = file?.lines?.let { lines ->
                        LyricsCredits.strip(lines.entries.toList()) { it.value }.associate { it.key to it.value }
                    },
                    offset = file?.offset?.inWholeMilliseconds ?: 0L
                )
            }
        }

        val currentPosition = {
            binder?.player?.let { player ->
                player.currentPosition + UPDATE_DELAY + lyricsState.offset -
                    (lyrics?.startTime ?: 0L)
            } ?: 0L
        }
        // Updated every tick while word-by-word lyrics are showing, to light up the words.
        var wordPosition by remember { mutableLongStateOf(0L) }

        val synchronizedLyrics = remember(lyricsState) {
            invalidLrc = lyricsState.sentences == null && wordLyrics == null
            lyricsState.sentences?.let {
                SynchronizedLyrics(it.toImmutableMap(), currentPosition)
            }
        }

        AnimatedContent(
            targetState = showSynchronizedLyrics,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = ""
        ) { synchronized ->
            val lazyListState = rememberLazyListState()
            if (synchronized) {
                LaunchedEffect(synchronizedLyrics, density, animatedHeight) {
                    val currentSynchronizedLyrics = synchronizedLyrics ?: return@LaunchedEffect
                    val centerOffset = with(density) { (-animatedHeight / 3).roundToPx() }

                    lazyListState.animateScrollToItem(
                        index = currentSynchronizedLyrics.index + 1,
                        scrollOffset = centerOffset
                    )

                    while (true) {
                        delay(UPDATE_DELAY.milliseconds)
                        if (wordLyrics != null) wordPosition = currentPosition()
                        if (!currentSynchronizedLyrics.update()) continue

                        lazyListState.animateScrollToItem(
                            index = currentSynchronizedLyrics.index + 1,
                            scrollOffset = centerOffset
                        )
                    }
                }

                if (synchronizedLyrics != null) LazyColumn(
                    state = lazyListState,
                    userScrollEnabled = false,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .verticalFadingEdge()
                        .fillMaxWidth()
                ) {
                    item(key = "header", contentType = 0) {
                        Spacer(modifier = Modifier.height(maxHeight))
                    }
                    itemsIndexed(
                        items = synchronizedLyrics.sentences.values.toImmutableList()
                    ) { index, sentence ->
                        val color by animateColorAsState(
                            if (index == synchronizedLyrics.index) Color.White
                            else colorPalette.textDisabled
                        )

                        val wordLine = wordLyrics?.lines?.getOrNull(index)
                        if (wordLine != null && index == synchronizedLyrics.index) WordByWordLine(
                            line = wordLine,
                            position = { wordPosition },
                            style = lyricsStyle,
                            sungColor = Color.White,
                            unsungColor = colorPalette.textDisabled,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 32.dp)
                        ) else if (sentence.isBlank()) Image(
                            painter = painterResource(R.drawable.musical_notes),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(color),
                            modifier = Modifier
                                .padding(vertical = 4.dp, horizontal = 32.dp)
                                .size(lyricsSize.dp)
                        ) else BasicText(
                            text = sentence,
                            style = lyricsStyle.color(color),
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 32.dp)
                        )
                    }
                    item(key = "footer", contentType = 0) {
                        Spacer(modifier = Modifier.height(maxHeight))
                    }
                }
            } else BasicText(
                text = lyrics?.fixed.orEmpty(),
                style = lyricsStyle.color(colorPalette.onOverlay),
                modifier = Modifier
                    .verticalFadingEdge()
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
                    .padding(vertical = maxHeight / 4, horizontal = 32.dp)
            )
        }

        if (text == null && !error) Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.shimmer()
        ) {
            repeat(4) {
                TextPlaceholder(
                    color = colorPalette.onOverlayShimmer,
                    modifier = Modifier.alpha(1f - it * 0.2f)
                )
            }
        }

        if (showControls) {
            if (onOpenDialog != null) Image(
                painter = painterResource(R.drawable.expand),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette.onOverlay),
                modifier = Modifier
                    .padding(all = 4.dp)
                    .clickable(
                        indication = ripple(bounded = false),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {
                            onOpenDialog()
                        }
                    )
                    .padding(all = 8.dp)
                    .size(20.dp)
                    .align(Alignment.BottomStart)
            )

            Image(
                painter = painterResource(R.drawable.ellipsis_horizontal),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette.onOverlay),
                modifier = Modifier
                    .padding(all = 4.dp)
                    .clickable(
                        indication = ripple(bounded = false),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {
                            onMenuLaunch()
                            menuState.display {
                                Menu {
                                    // This song's lyrics mode, with a check next to the current one
                                    listOf(
                                        Triple(LyricsMode.WORD, R.string.lyrics_mode_word, R.drawable.text),
                                        Triple(LyricsMode.LINE, R.string.lyrics_mode_line, R.drawable.time),
                                        Triple(LyricsMode.UNSYNCED, R.string.lyrics_mode_unsynced, R.drawable.musical_notes)
                                    ).forEach { (entryMode, label, icon) ->
                                        val current = when {
                                            !shouldShowSynchronizedLyrics -> LyricsMode.UNSYNCED
                                            wordByWord -> LyricsMode.WORD
                                            else -> LyricsMode.LINE
                                        }
                                        MenuEntry(
                                            icon = icon,
                                            text = stringResource(label),
                                            onClick = {
                                                menuState.hide()
                                                setMode(entryMode)
                                            },
                                            trailingContent = if (entryMode == current) {
                                                {
                                                    BasicText(
                                                        text = "✓",
                                                        style = typography.s.semiBold.color(colorPalette.accent)
                                                    )
                                                }
                                            } else null
                                        )
                                    }

                                    MenuEntry(
                                        icon = R.drawable.text,
                                        text = stringResource(R.string.lyrics_text_size),
                                        onClick = {
                                            menuState.display {
                                                Menu {
                                                    LYRICS_SIZES.forEach { (label, size) ->
                                                        MenuEntry(
                                                            icon = R.drawable.text,
                                                            text = (if (size == lyricsSize) "✓ " else "") +
                                                                stringResource(label),
                                                            onClick = {
                                                                menuState.hide()
                                                                PlayerPreferences.lyricsTextSize = size
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    )

                                    MenuEntry(
                                        icon = R.drawable.pencil,
                                        text = stringResource(R.string.edit_lyrics),
                                        onClick = {
                                            menuState.hide()
                                            editing = true
                                        }
                                    )

                                    MenuEntry(
                                        icon = R.drawable.sync,
                                        text = stringResource(R.string.refetch_lyrics),
                                        enabled = lyrics != null,
                                        onClick = {
                                            menuState.hide()

                                            transaction {
                                                runCatching {
                                                    currentEnsureSongInserted()

                                                    Database.upsert(
                                                        if (shouldShowSynchronizedLyrics) Lyrics(
                                                            songId = mediaId,
                                                            fixed = lyrics?.fixed,
                                                            synced = null,
                                                            word = null,
                                                            mode = lyrics?.mode
                                                        ) else Lyrics(
                                                            songId = mediaId,
                                                            fixed = null,
                                                            synced = lyrics?.synced,
                                                            startTime = lyrics?.startTime,
                                                            word = lyrics?.word,
                                                            mode = lyrics?.mode
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    )

                                    if (shouldShowSynchronizedLyrics) {
                                        MenuEntry(
                                            icon = R.drawable.download,
                                            text = stringResource(R.string.pick_from_lrclib),
                                            onClick = {
                                                menuState.hide()
                                                picking = true
                                            }
                                        )
                                        MenuEntry(
                                            icon = R.drawable.play_skip_forward,
                                            text = stringResource(R.string.set_lyrics_start_offset),
                                            secondaryText = stringResource(
                                                R.string.set_lyrics_start_offset_description
                                            ),
                                            onClick = {
                                                menuState.hide()
                                                lyrics?.let {
                                                    val startTime = binder?.player?.currentPosition
                                                    query {
                                                        Database.upsert(it.copy(startTime = startTime))
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                    .padding(all = 8.dp)
                    .size(20.dp)
                    .align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
fun LrcLibSearchDialog(
    query: String,
    setQuery: (String) -> Unit,
    onDismiss: () -> Unit,
    onPick: (Track) -> Unit,
    modifier: Modifier = Modifier
) = DefaultDialog(
    onDismiss = onDismiss,
    horizontalPadding = 0.dp,
    modifier = modifier
) {
    val [_, typography] = LocalAppearance.current

    val tracks = remember { mutableStateListOf<Track>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        loading = true
        error = false

        delay(1000.milliseconds)

        LrcLib.lyrics(
            query = query,
            synced = true
        )?.onSuccess { newTracks ->
            tracks.clear()
            tracks.addAll(newTracks.filter { !it.syncedLyrics.isNullOrBlank() })
            loading = false
            error = false
        }?.onFailure {
            loading = false
            error = true
            it.printStackTrace()
        } ?: run { loading = false }
    }

    TextField(
        value = query,
        onValueChange = setQuery,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        maxLines = 1,
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))

    when {
        loading -> CircularProgressIndicator(
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        error || tracks.isEmpty() -> BasicText(
            text = stringResource(R.string.no_lyrics_found),
            style = typography.s.semiBold.center,
            modifier = Modifier
                .padding(all = 24.dp)
                .align(Alignment.CenterHorizontally)
        )

        else -> ValueSelectorDialogBody(
            onDismiss = onDismiss,
            title = stringResource(R.string.choose_lyric_track),
            selectedValue = null,
            values = tracks.toImmutableList(),
            onValueSelect = {
                transaction {
                    onPick(it)
                    onDismiss()
                }
            },
            valueText = {
                "${it.artistName} - ${it.trackName} (${
                    it.duration.seconds.toComponents { minutes, seconds, _ ->
                        "$minutes:${seconds.toString().padStart(2, '0')}"
                    }
                })"
            }
        )
    }
}

/**
 * The line being sung, with each word lighting up as it's reached. The word in progress
 * brightens gradually over its own duration.
 */
@Composable
private fun WordByWordLine(
    line: WordLyrics.Line,
    position: () -> Long,
    style: TextStyle,
    sungColor: Color,
    unsungColor: Color,
    modifier: Modifier = Modifier
) {
    val now = position()
    val text = remember(line, now) {
        buildAnnotatedString {
            line.words.forEachIndexed { i, word ->
                val progress = when {
                    now >= word.end -> 1f
                    now <= word.start -> 0f
                    word.duration <= 0 -> 1f
                    else -> (now - word.start).toFloat() / word.duration
                }
                val wordText = if (i == 0) word.text.trimStart() else word.text
                withStyle(SpanStyle(color = lerp(unsungColor, sungColor, progress))) {
                    append(if (i == line.words.lastIndex) wordText.trimEnd() else wordText)
                }
            }
        }
    }

    BasicText(
        text = text,
        style = style,
        modifier = modifier
    )
}
