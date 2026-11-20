package app.vertune.desktop.player

import app.vertune.desktop.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * Turns a YouTube video id into a direct audio link, using yt-dlp (the same tool the phone app
 * uses). Links stay valid for a few hours, so they are cached.
 */
object StreamResolver {
    data class Stream(val url: String, val durationMs: Long?, val headers: Map<String, String>)

    class ResolveException(message: String) : Exception(message)

    @Serializable
    private data class YtDlpInfo(
        val id: String,
        val url: String? = null,
        val duration: Double? = null,
        @SerialName("http_headers") val httpHeaders: Map<String, String>? = null
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private data class Cached(val stream: Stream, val at: Long)

    private val cache = mutableMapOf<String, Cached>()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = mutableMapOf<String, kotlinx.coroutines.Deferred<Stream>>()

    private val ytDlp by lazy { Paths.tool("yt-dlp") ?: Paths.tool("yt-dlp_macos") }
    private val quickJs by lazy { Paths.tool("qjs") }

    fun invalidate(id: String) {
        synchronized(cache) { cache.remove(id) }
    }

    suspend fun resolve(id: String): Stream {
        synchronized(cache) {
            cache[id]?.takeIf { System.currentTimeMillis() - it.at < CACHE_MS }?.let { return it.stream }
        }
        // If the same song is already being resolved (for example by prefetching), wait for that.
        val job = mutex.withLock {
            inFlight[id] ?: scope.async { runYtDlp(id) }.also { inFlight[id] = it }
        }
        return try {
            job.await().also { stream ->
                synchronized(cache) { cache[id] = Cached(stream, System.currentTimeMillis()) }
            }
        } finally {
            mutex.withLock { inFlight.remove(id) }
        }
    }

    private suspend fun runYtDlp(id: String): Stream = withContext(Dispatchers.IO) {
        val binary = ytDlp ?: throw ResolveException("yt-dlp is missing from the app")
        val command = buildList {
            add(binary)
            add("-f"); add("bestaudio[ext=m4a]/bestaudio")
            add("-J"); add("--no-playlist"); add("--no-warnings")
            quickJs?.let { add("--js-runtimes"); add("quickjs:$it") }
            add("--"); add(id)
        }
        val errFile = kotlin.io.path.createTempFile("vertune-ytdlp", ".log").toFile()
        try {
            val process = ProcessBuilder(command)
                .redirectError(errFile)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(TIMEOUT_S, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw ResolveException("yt-dlp took too long")
            }
            if (process.exitValue() != 0 || output.isBlank()) {
                val err = errFile.readText().lines().lastOrNull { it.contains("ERROR") } ?: "unknown error"
                throw ResolveException(err.removePrefix("ERROR: ").take(300))
            }
            val info = json.decodeFromString<YtDlpInfo>(output)
            Stream(
                url = info.url ?: throw ResolveException("No playable audio found"),
                durationMs = info.duration?.let { (it * 1000).toLong() },
                headers = info.httpHeaders.orEmpty()
            )
        } finally {
            errFile.delete()
        }
    }

    /** Updates the bundled yt-dlp to the newest version (YouTube changes often break old ones). */
    suspend fun update(): String = withContext(Dispatchers.IO) {
        val binary = ytDlp ?: return@withContext "yt-dlp is missing from the app"
        runCatching {
            val p = ProcessBuilder(binary, "-U").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor(2, TimeUnit.MINUTES)
            out.lines().lastOrNull { it.isNotBlank() } ?: "Done"
        }.getOrElse { it.message ?: "Update failed" }
    }

    private const val CACHE_MS = 2 * 60 * 60 * 1000L
    private const val TIMEOUT_S = 60L
}
