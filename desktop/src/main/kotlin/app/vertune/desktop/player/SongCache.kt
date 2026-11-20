package app.vertune.desktop.player

import app.vertune.desktop.Paths
import app.vertune.desktop.data.Library
import app.vertune.desktop.data.Settings
import app.vertune.desktop.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.RandomAccessFile
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Saves songs to disk as they play, like the phone app's song cache. A saved song starts
 * instantly and plays without internet. When the cache is bigger than the size set in
 * Settings, the songs that haven't been played for the longest time are removed first.
 */
object SongCache {
    /** Cache size choices, the same ones as the phone (0 means no limit). */
    val sizeChoicesMb = listOf(512L, 1024L, 2048L, 4096L, 8192L, 0L)

    val dir: File by lazy {
        val home = System.getProperty("user.home")
        val base = if (Paths.isMac) File(home, "Library/Caches/Vertune") else File(Paths.dataDir, "cache")
        File(base, "songs").apply { mkdirs() }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inProgress = java.util.Collections.synchronizedSet(HashSet<String>())
    private val client by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_S))
            .build()
    }

    private val mutableIds = MutableStateFlow(scan())

    /** The ids of every song that's fully saved. */
    val ids: StateFlow<Set<String>> = mutableIds.asStateFlow()

    private val mutableBytes = MutableStateFlow(totalBytes())

    /** How much space the saved songs take. */
    val bytes: StateFlow<Long> = mutableBytes.asStateFlow()

    private fun fileFor(id: String) = File(dir, "${safe(id)}.audio")
    private fun lengthFor(id: String) = File(dir, "${safe(id)}.ms")
    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun files() = dir.listFiles { f -> f.isFile && f.name.endsWith(".audio") }.orEmpty().toList()
    private fun scan() = files().map { it.name.removeSuffix(".audio") }.toSet()
    private fun totalBytes() = files().sumOf { it.length() }

    private fun refresh() {
        mutableIds.value = scan()
        mutableBytes.value = totalBytes()
    }

    /** The saved copy of a song, if there is one. Marks it as just played. */
    fun cached(id: String): File? = fileFor(id).takeIf { it.isFile && it.length() > 0 }?.also {
        it.setLastModified(System.currentTimeMillis())
    }

    /** Saved song ids, most recently played first (for the Offline playlist). */
    fun idsByRecent(): List<String> =
        files().sortedByDescending { it.lastModified() }.map { it.name.removeSuffix(".audio") }

    fun isCached(id: String) = fileFor(id).isFile

    /** Saves a song in the background, using a link that was already looked up. */
    fun save(track: Track, stream: StreamResolver.Stream) {
        if (Settings.pauseSongCache.value) return
        scope.launch { saveNow(track, stream) }
    }

    private fun saveNow(track: Track, stream: StreamResolver.Stream) {
        if (isCached(track.id) || !inProgress.add(track.id)) return
        try {
            download(stream, fileFor(track.id))
            (stream.durationMs ?: track.durationMs.takeIf { it > 0 })?.let { lengthFor(track.id).writeText(it.toString()) }
            runCatching { Library.save(track) }
            evict(keep = track.id)
        } catch (e: Exception) {
            System.err.println("Couldn't save ${track.id} to the cache: ${e.message}")
        } finally {
            inProgress.remove(track.id)
            refresh()
        }
    }

    private val mutablePending = MutableStateFlow<Set<String>>(emptySet())

    /** Songs waiting to be pre-cached (shown as progress on playlist pages). */
    val pending: StateFlow<Set<String>> = mutablePending.asStateFlow()

    private val precacheSlots = Semaphore(PRECACHE_AT_ONCE)

    /** "Save for offline": looks a song up and saves it, a couple at a time. */
    fun precache(track: Track) = precacheAll(listOf(track))

    /** Pre-caches a whole playlist or album, like the phone's download button. */
    fun precacheAll(tracks: List<Track>) {
        val todo = tracks.distinctBy { it.id }.filter { !isCached(it.id) && it.id !in mutablePending.value }
        if (todo.isEmpty()) return
        mutablePending.update { it + todo.map(Track::id) }
        todo.forEach { track ->
            scope.launch {
                try {
                    precacheSlots.withPermit {
                        runCatching { StreamResolver.resolve(track.id) }
                            .onSuccess { saveNow(track, it) }
                            .onFailure { System.err.println("Couldn't save ${track.id}: ${it.message}") }
                    }
                } finally {
                    mutablePending.update { it - track.id }
                }
            }
        }
    }

    /**
     * How long a saved song is. Saved next to the song; for songs saved before that, ffmpeg
     * reads it from the file.
     */
    fun durationMs(id: String, ffmpeg: String?): Long? {
        runCatching { lengthFor(id).readText().trim().toLong() }.getOrNull()?.takeIf { it > 0 }?.let { return it }
        val tool = ffmpeg ?: return null
        return runCatching {
            val p = ProcessBuilder(tool, "-hide_banner", "-nostdin", "-i", fileFor(id).absolutePath)
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            val m = Regex("Duration: (\\d+):(\\d+):(\\d+)\\.(\\d+)").find(out) ?: return@runCatching null
            val (h, mi, sec, frac) = m.destructured
            ((h.toLong() * 3600 + mi.toLong() * 60 + sec.toLong()) * 1000 + frac.padEnd(3, '0').take(3).toLong())
                .also { lengthFor(id).writeText(it.toString()) }
        }.getOrNull()
    }

    fun remove(id: String) {
        lengthFor(id).delete()
        fileFor(id).delete()
        refresh()
    }

    fun clear() {
        dir.listFiles().orEmpty().forEach { it.delete() }
        refresh()
    }

    /** Removes the songs played longest ago until the cache fits the size limit. */
    fun evict(keep: String? = null) {
        val limit = Settings.songCacheMb.value * 1024 * 1024
        if (limit <= 0) return
        var total = totalBytes()
        if (total <= limit) return
        for (f in files().sortedBy { it.lastModified() }) {
            if (total <= limit) break
            if (keep != null && f.name == "${safe(keep)}.audio") continue
            total -= f.length()
            f.delete()
            File(dir, f.name.removeSuffix(".audio") + ".ms").delete()
        }
        refresh()
    }

    /**
     * Downloads in pieces of a few MB, like yt-dlp does, because YouTube slows down one big
     * request. Writes to a .part file and renames it once the whole song is there.
     */
    private fun download(stream: StreamResolver.Stream, target: File) {
        val part = File(target.parentFile, target.name + ".part")
        part.delete()
        try {
            RandomAccessFile(part, "rw").use { out ->
                var start = 0L
                var total: Long? = null
                while (total == null || start < total) {
                    val end = start + CHUNK_BYTES - 1
                    val request = HttpRequest.newBuilder(URI(stream.url))
                        .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_S))
                        .header("Range", "bytes=$start-$end")
                        .apply {
                            stream.headers.forEach { (k, v) ->
                                if (k.lowercase() !in RESTRICTED_HEADERS) runCatching { header(k, v) }
                            }
                        }
                        .GET()
                        .build()
                    val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
                    if (response.statusCode() !in 200..299) {
                        response.body().close()
                        error("server answered ${response.statusCode()}")
                    }
                    if (total == null) {
                        total = response.headers().firstValue("Content-Range").orElse(null)
                            ?.substringAfter('/')?.toLongOrNull()
                            ?: response.headers().firstValueAsLong("Content-Length").orElse(-1).takeIf { it > 0 }
                            ?: error("unknown size")
                    }
                    var got = 0L
                    response.body().use { input ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        out.seek(start)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            got += n
                        }
                    }
                    if (got == 0L) error("download stopped")
                    // A server that ignores Range sends the whole file at once.
                    start = if (response.statusCode() == 200) total!! else start + got
                }
                out.setLength(total!!)
            }
            if (!part.renameTo(target)) error("couldn't move the file into place")
        } finally {
            part.delete()
        }
    }

    private val RESTRICTED_HEADERS = setOf("connection", "content-length", "expect", "host", "upgrade", "range")
    private const val PRECACHE_AT_ONCE = 2
    private const val CHUNK_BYTES = 4L * 1024 * 1024
    private const val BUFFER_BYTES = 64 * 1024
    private const val CONNECT_TIMEOUT_S = 15L
    private const val REQUEST_TIMEOUT_S = 60L
}
