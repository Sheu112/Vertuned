package app.vertune.desktop.player

import app.vertune.desktop.Paths
import app.vertune.desktop.data.Library
import app.vertune.desktop.data.Settings
import app.vertune.desktop.data.Track
import app.vertune.desktop.data.toTrack
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.bodies.ContinuationBody
import app.vitune.providers.innertube.models.bodies.NextBody
import app.vitune.providers.innertube.requests.nextPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The play queue and everything about what's playing right now. */
object PlayerController {
    enum class Repeat { Off, All, One }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val mutableQueue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = mutableQueue.asStateFlow()

    private val mutableIndex = MutableStateFlow(-1)
    val index: StateFlow<Int> = mutableIndex.asStateFlow()

    private val mutableCurrent = MutableStateFlow<Track?>(null)
    val current: StateFlow<Track?> = mutableCurrent.asStateFlow()

    private val mutableIsPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = mutableIsPlaying.asStateFlow()

    private val mutableIsLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = mutableIsLoading.asStateFlow()

    private val mutablePosition = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = mutablePosition.asStateFlow()

    private val mutableDuration = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = mutableDuration.asStateFlow()

    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError.asStateFlow()

    private val mutableRepeat = MutableStateFlow(Repeat.Off)
    val repeat: StateFlow<Repeat> = mutableRepeat.asStateFlow()

    private val ffmpeg by lazy { Paths.tool("ffmpeg") }

    private var playJob: Job? = null

    @Volatile
    private var session: AudioSession? = null

    /** Listening time collected for the current song, saved to the library when it changes. */
    private var listenTrack: Track? = null
    private var listenMs = 0L
    private var listenStartedAt = 0L

    private var radio: Radio? = null
    private var radioJob: Job? = null

    init {
        scope.launch {
            while (isActive) {
                session?.let { mutablePosition.value = it.positionMs }
                delay(POSITION_TICK_MS)
            }
        }
    }

    // ---- Queue commands ----

    /** Plays [tracks] starting at [startIndex]. With [radio], similar songs are added after it. */
    fun play(tracks: List<Track>, startIndex: Int = 0, radio: Boolean = false) {
        if (tracks.isEmpty()) return
        mutableQueue.value = tracks
        this.radio = null
        radioJob?.cancel()
        mutableIsPlaying.value = true
        playAt(startIndex.coerceIn(tracks.indices))
        if (radio) startRadio(tracks[startIndex.coerceIn(tracks.indices)])
    }

    /** Plays one song, followed by a radio of similar songs, like the phone app does. */
    fun playWithRadio(track: Track) = play(listOf(track), 0, radio = true)

    fun playNext(track: Track) {
        val q = mutableQueue.value.toMutableList()
        if (q.isEmpty()) return play(listOf(track))
        q.add(mutableIndex.value + 1, track)
        mutableQueue.value = q
    }

    fun addToQueue(track: Track) {
        if (mutableQueue.value.isEmpty()) return play(listOf(track))
        mutableQueue.value = mutableQueue.value + track
    }

    fun removeAt(i: Int) {
        val q = mutableQueue.value.toMutableList()
        if (i !in q.indices || i == mutableIndex.value) return
        q.removeAt(i)
        mutableQueue.value = q
        if (i < mutableIndex.value) mutableIndex.value--
    }

    fun clearQueue() {
        val cur = mutableCurrent.value ?: return
        mutableQueue.value = listOf(cur)
        mutableIndex.value = 0
        radio = null
        radioJob?.cancel()
    }

    fun playAt(i: Int) {
        val q = mutableQueue.value
        if (i !in q.indices) return
        mutableIndex.value = i
        mutableCurrent.value = q[i]
        startCurrent(0)
        if (i >= q.size - RADIO_PREFETCH_WHEN_LEFT) extendRadio()
        prefetch(i + 1)
    }

    // ---- Transport ----

    fun togglePlay() {
        if (mutableCurrent.value == null) return
        setPlaying(!mutableIsPlaying.value)
    }

    fun setPlaying(playing: Boolean) {
        mutableIsPlaying.value = playing
        val s = session
        if (s != null) {
            s.paused = !playing
            if (playing) listenStartedAt = System.currentTimeMillis()
        } else if (playing && playJob?.isActive != true && mutableCurrent.value != null) {
            startCurrent(mutablePosition.value)
        }
    }

    fun next() {
        val q = mutableQueue.value
        val i = mutableIndex.value
        when {
            i + 1 < q.size -> playAt(i + 1)
            mutableRepeat.value == Repeat.All && q.isNotEmpty() -> playAt(0)
        }
    }

    fun previous() {
        if (mutablePosition.value > RESTART_THRESHOLD_MS || mutableIndex.value <= 0) seekTo(0)
        else playAt(mutableIndex.value - 1)
    }

    fun seekTo(ms: Long) {
        if (mutableCurrent.value == null) return
        mutablePosition.value = ms
        startCurrent(ms.coerceAtLeast(0))
    }

    fun cycleRepeat() {
        mutableRepeat.value = Repeat.entries[(mutableRepeat.value.ordinal + 1) % Repeat.entries.size]
    }

    fun shuffleUpcoming() {
        val q = mutableQueue.value
        val i = mutableIndex.value
        if (i < 0 || i >= q.size - 1) return
        mutableQueue.value = q.take(i + 1) + q.drop(i + 1).shuffled()
    }

    /** Saves the listening time of the current song. Call before quitting. */
    fun shutdown() {
        playJob?.cancel()
        session?.let { old ->
            old.stop()
            addListen(old.consumePlayed())
        }
        flushListen(null)
    }

    // ---- Playback ----

    private fun startCurrent(fromMs: Long) {
        val track = mutableCurrent.value ?: return
        playJob?.cancel()
        session?.let { old ->
            old.stop()
            addListen(old.consumePlayed())
        }
        session = null
        flushListen(track)

        mutablePosition.value = fromMs
        mutableDuration.value = track.durationMs
        mutableError.value = null

        playJob = scope.launch(Dispatchers.IO) {
            var position = fromMs
            var attempts = 0
            while (isActive) {
                mutableIsLoading.value = true
                val stream = try {
                    StreamResolver.resolve(track.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    mutableIsLoading.value = false
                    failAndSkip(e.message ?: "Couldn't load this song")
                    return@launch
                }
                stream.durationMs?.let { mutableDuration.value = it }

                val ff = ffmpeg ?: run {
                    mutableIsLoading.value = false
                    mutableError.value = "ffmpeg is missing from the app"
                    mutableIsPlaying.value = false
                    return@launch
                }
                val s = AudioSession(ff, stream, position) { Settings.volume.value }
                s.paused = !mutableIsPlaying.value
                session = s
                listenStartedAt = System.currentTimeMillis()

                val done = CompletableDeferred<AudioSession.Outcome>()
                Thread({ done.complete(s.run()) }, "vertune-audio").apply { isDaemon = true }.start()
                // The song counts as loaded once sound starts coming out.
                launch {
                    while (isActive && done.isActive) {
                        if (s.playedMs > 0 || s.paused) {
                            mutableIsLoading.value = false
                            break
                        }
                        delay(100)
                    }
                }

                val outcome = try {
                    done.await()
                } catch (e: CancellationException) {
                    s.stop()
                    throw e
                }
                addListen(s.consumePlayed())
                mutableIsLoading.value = false

                when (outcome) {
                    AudioSession.Outcome.Completed -> {
                        session = null
                        onTrackEnded()
                        return@launch
                    }
                    AudioSession.Outcome.Stopped -> return@launch
                    is AudioSession.Outcome.Failed -> {
                        attempts++
                        if (attempts > MAX_RETRIES) {
                            session = null
                            failAndSkip(outcome.message)
                            return@launch
                        }
                        // Links can expire, so get a fresh one and continue where it stopped.
                        StreamResolver.invalidate(track.id)
                        position = s.positionMs
                        delay(RETRY_DELAY_MS)
                    }
                }
            }
        }
    }

    private fun onTrackEnded() {
        when {
            mutableRepeat.value == Repeat.One -> scope.launch { startCurrent(0) }
            mutableIndex.value + 1 < mutableQueue.value.size || mutableRepeat.value == Repeat.All ->
                scope.launch { next() }
            else -> {
                flushListen(null)
                mutableIsPlaying.value = false
                mutablePosition.value = 0
            }
        }
    }

    private suspend fun failAndSkip(message: String) {
        mutableError.value = message
        delay(SKIP_AFTER_ERROR_MS)
        if (mutableIndex.value + 1 < mutableQueue.value.size) next()
        else mutableIsPlaying.value = false
    }

    @Synchronized
    private fun addListen(ms: Long) {
        listenMs += ms
    }

    /** Saves the collected listening time if the song is changing to [next]. */
    @Synchronized
    private fun flushListen(next: Track?) {
        val previous = listenTrack
        if (previous != null && previous.id != next?.id) {
            val ms = listenMs
            scope.launch(Dispatchers.IO) { runCatching { Library.addListen(previous, ms) } }
            listenMs = 0
        }
        if (previous?.id != next?.id) {
            listenTrack = next
            listenMs = 0
        }
    }

    private fun prefetch(i: Int) {
        val track = mutableQueue.value.getOrNull(i) ?: return
        scope.launch(Dispatchers.IO) {
            delay(PREFETCH_DELAY_MS)
            runCatching { StreamResolver.resolve(track.id) }
        }
    }

    // ---- Radio ----

    private fun startRadio(seed: Track) {
        radio = Radio(seed.id)
        extendRadio()
    }

    private fun extendRadio() {
        val r = radio ?: return
        if (radioJob?.isActive == true) return
        radioJob = scope.launch(Dispatchers.IO) {
            val more = r.next()
            if (radio !== r) return@launch
            val existing = mutableQueue.value.map { it.id }.toSet()
            val fresh = more.filter { it.id !in existing }
            if (fresh.isNotEmpty()) {
                mutableQueue.value = mutableQueue.value + fresh
                prefetch(mutableIndex.value + 1)
            }
        }
    }

    private class Radio(private val videoId: String) {
        private var playlistId: String? = null
        private var params: String? = null
        private var playlistSetVideoId: String? = null
        private var continuation: String? = null
        private var started = false

        suspend fun next(): List<Track> {
            val page = if (!started) {
                started = true
                Innertube.nextPage(NextBody(videoId = videoId))?.getOrNull()?.let { result ->
                    playlistId = result.playlistId
                    params = result.params
                    playlistSetVideoId = result.playlistSetVideoId
                    result.itemsPage
                }
            } else {
                val c = continuation ?: return emptyList()
                Innertube.nextPage(ContinuationBody(continuation = c))?.getOrNull()
            }
            continuation = page?.continuation?.takeUnless { it == continuation }
            return page?.items?.mapNotNull { it.toTrack() }.orEmpty()
        }
    }

    private const val POSITION_TICK_MS = 250L
    private const val RESTART_THRESHOLD_MS = 3_000L
    private const val MAX_RETRIES = 2
    private const val RETRY_DELAY_MS = 800L
    private const val SKIP_AFTER_ERROR_MS = 2_500L
    private const val PREFETCH_DELAY_MS = 3_000L
    private const val RADIO_PREFETCH_WHEN_LEFT = 3
}
