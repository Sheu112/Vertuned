package app.vertune.desktop.player

import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Plays one audio stream from a given position: ffmpeg downloads and decodes it, and the decoded
 * sound goes to the Mac's speakers through Java's built-in audio output.
 * Seeking is done by starting a new session at the new position.
 */
class AudioSession(
    private val ffmpeg: String,
    private val stream: StreamResolver.Stream,
    val startMs: Long,
    private val filter: String? = null,
    private val speed: Float = 1f,
    private val volume: () -> Float
) {
    sealed interface Outcome {
        data object Completed : Outcome
        data object Stopped : Outcome
        data class Failed(val message: String) : Outcome
    }

    private val lock = Object()

    @Volatile
    var paused = false
        set(value) {
            field = value
            synchronized(lock) { lock.notifyAll() }
        }

    @Volatile
    private var stopped = false

    @Volatile
    private var process: Process? = null

    @Volatile
    private var line: SourceDataLine? = null

    @Volatile
    private var lastPlayedMs = 0L

    /** How much of this session was actually heard. */
    val playedMs: Long
        get() = line?.let { runCatching { it.microsecondPosition / 1000 }.getOrNull() } ?: lastPlayedMs

    /** Where in the song we are. With a playback speed, heard time covers more of the song. */
    val positionMs: Long get() = startMs + (playedMs * speed).toLong()

    private var consumed = false

    /** The heard time, counted only once (for the listening-time statistics). */
    @Synchronized
    fun consumePlayed(): Long {
        if (consumed) return 0
        consumed = true
        return playedMs
    }

    /** Blocks until the stream ends, fails, or [stop] is called. */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun run(): Outcome {
        val command = buildList {
            add(ffmpeg)
            addAll(listOf("-hide_banner", "-loglevel", "error", "-nostdin"))
            val remote = stream.url.startsWith("http")
            if (remote) addAll(
                listOf(
                    "-reconnect", "1", "-reconnect_streamed", "1",
                    "-reconnect_on_network_error", "1", "-reconnect_delay_max", "5"
                )
            )
            if (remote) stream.headers["User-Agent"]?.let { addAll(listOf("-user_agent", it)) }
            val otherHeaders = stream.headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) }
            if (remote && otherHeaders.isNotEmpty()) {
                add("-headers")
                add(otherHeaders.entries.joinToString("") { "${it.key}: ${it.value}\r\n" })
            }
            if (startMs > 0) addAll(listOf("-ss", "%.3f".format(java.util.Locale.ROOT, startMs / 1000.0)))
            addAll(listOf("-i", stream.url, "-vn"))
            if (filter != null) addAll(listOf("-af", filter))
            addAll(listOf("-f", "s16le", "-acodec", "pcm_s16le"))
            addAll(listOf("-ac", CHANNELS.toString(), "-ar", SAMPLE_RATE.toString(), "pipe:1"))
        }

        val stderr = StringBuilder()
        val proc = try {
            ProcessBuilder(command).start()
        } catch (e: java.io.IOException) {
            return Outcome.Failed("Could not start ffmpeg: ${e.message}")
        }
        process = proc
        Thread {
            runCatching {
                proc.errorStream.bufferedReader().forEachLine { l ->
                    synchronized(stderr) { if (stderr.length < 4000) stderr.appendLine(l) }
                }
            }
        }.apply { isDaemon = true }.start()

        val format = AudioFormat(SAMPLE_RATE.toFloat(), 16, CHANNELS, true, false)
        val out = try {
            AudioSystem.getSourceDataLine(format).apply { open(format, SAMPLE_RATE * BYTES_PER_FRAME / 2) }
        } catch (e: Exception) {
            proc.destroyForcibly()
            return Outcome.Failed("No audio output available: ${e.message}")
        }
        line = out
        if (stopped) {
            cleanup()
            return Outcome.Stopped
        }

        val buffer = ByteArray(BUFFER_BYTES)
        var started = false
        try {
            val input = proc.inputStream
            while (!stopped) {
                val n = input.readNBytes(buffer, 0, buffer.size)
                if (n <= 0) break

                applyVolume(buffer, n - n % 2)

                if (paused) {
                    out.stop()
                    synchronized(lock) { while (paused && !stopped) lock.wait(500) }
                    if (stopped) break
                }
                if (!started || !out.isRunning) {
                    out.start()
                    started = true
                }
                out.write(buffer, 0, n - n % BYTES_PER_FRAME)
            }
            if (stopped) return Outcome.Stopped

            proc.waitFor(5, TimeUnit.SECONDS)
            val exit = runCatching { proc.exitValue() }.getOrDefault(-1)
            if (exit != 0) {
                val err = synchronized(stderr) { stderr.toString() }.lines().lastOrNull { it.isNotBlank() }
                return Outcome.Failed(err ?: "Playback stopped unexpectedly (code $exit)")
            }
            // Let the last bit of sound play out before saying the song is finished.
            while (!stopped && paused) synchronized(lock) { lock.wait(500) }
            if (!stopped) out.drain()

            val expected = stream.durationMs
            if (expected != null && filter?.contains("silenceremove") != true && positionMs < expected - TRUNCATED_TOLERANCE_MS && !stopped) {
                return Outcome.Failed("The stream ended early")
            }
            return if (stopped) Outcome.Stopped else Outcome.Completed
        } catch (e: Exception) {
            return if (stopped) Outcome.Stopped else Outcome.Failed(e.message ?: e.toString())
        } finally {
            cleanup()
        }
    }

    fun stop() {
        stopped = true
        synchronized(lock) { lock.notifyAll() }
        process?.destroy()
        line?.let { l ->
            runCatching {
                lastPlayedMs = l.microsecondPosition / 1000
                l.stop()
                l.flush()
            }
        }
    }

    private fun cleanup() {
        process?.destroyForcibly()
        line?.let { l ->
            runCatching { lastPlayedMs = l.microsecondPosition / 1000 }
            runCatching { l.close() }
        }
        line = null
    }

    private fun applyVolume(buffer: ByteArray, length: Int) {
        val v = volume().coerceIn(0f, 1f)
        if (v >= 0.999f) return
        val gain = v * v // sounds more natural than a straight line
        var i = 0
        while (i < length) {
            val sample = (buffer[i].toInt() and 0xff) or (buffer[i + 1].toInt() shl 8)
            val scaled = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = scaled.toByte()
            buffer[i + 1] = (scaled shr 8).toByte()
            i += 2
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CHANNELS = 2
        const val BYTES_PER_FRAME = 4
        const val BUFFER_BYTES = 8_192
        const val TRUNCATED_TOLERANCE_MS = 8_000L
    }
}
