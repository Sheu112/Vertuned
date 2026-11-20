package app.vertune.desktop

import app.vertune.desktop.data.Library
import app.vertune.desktop.data.Track
import app.vertune.desktop.data.toTrack
import app.vertune.desktop.player.StreamResolver
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.bodies.SearchBody
import app.vitune.providers.innertube.requests.searchPage
import app.vitune.providers.innertube.utils.from
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Checks the parts that don't need a screen, on the CI Mac: the library database, YouTube search,
 * yt-dlp and ffmpeg. Run with `gradle :desktop:smokeTest`.
 */
fun main(): Unit = runBlocking {
    var failed = false
    fun check(name: String, block: () -> String) {
        runCatching(block)
            .onSuccess { println("OK   $name: $it") }
            .onFailure {
                failed = true
                println("FAIL $name: ${it.message}")
                it.printStackTrace(System.out)
            }
    }

    check("library") {
        val t = Track(id = "smoke-test", title = "Smoke test", artists = "Vertune", durationText = "3:21")
        Library.addListen(t, 60_000)
        Library.setLiked(t, true)
        val top = Library.top(50).first { it.track.id == t.id }
        require(Library.favorites().any { it.id == t.id })
        "top entry ${top.listenedMs} ms, liked"
    }

    for (tool in listOf("yt-dlp", "ffmpeg", "qjs")) check("tool $tool") {
        Paths.tool(tool) ?: error("not found")
    }

    var firstSong: Track? = null
    check("search") {
        val page = runBlocking {
            Innertube.searchPage(
                SearchBody(query = "daft punk get lucky", params = Innertube.SearchFilter.Song.value),
                Innertube.SongItem.Companion::from
            )
        }?.getOrThrow()
        val tracks = page?.items?.mapNotNull { it.toTrack() }.orEmpty()
        firstSong = tracks.firstOrNull() ?: error("no results")
        "${tracks.size} songs, first: ${firstSong?.title} by ${firstSong?.artists}"
    }

    val youtubeOk = firstSong?.let { song ->
        var ok = true
        check("yt-dlp resolve") {
            val stream = runBlocking { StreamResolver.resolve(song.id) }
            "${stream.durationMs} ms, ${stream.url.take(60)}…"
        }.also { ok = !failed }
        if (ok) check("ffmpeg decode") {
            val stream = runBlocking { StreamResolver.resolve(song.id) }
            val out = kotlin.io.path.createTempFile("vertune-smoke", ".pcm").toFile()
            val p = ProcessBuilder(
                Paths.tool("ffmpeg")!!, "-hide_banner", "-loglevel", "error", "-nostdin",
                "-ss", "30", "-i", stream.url, "-t", "5", "-vn", "-f", "s16le", "-ac", "2", "-ar", "44100", "-y",
                out.absolutePath
            ).redirectErrorStream(true).start()
            val log = p.inputStream.bufferedReader().readText()
            p.waitFor(60, TimeUnit.SECONDS)
            val bytes = out.length()
            out.delete()
            require(bytes > 44_100 * 4 * 4) { "only $bytes bytes decoded. $log" }
            "$bytes bytes of audio"
        }
        ok
    } ?: false

    println(if (failed) "Some checks failed" else "All checks passed (YouTube playback checked: $youtubeOk)")
    exitProcess(if (failed) 1 else 0)
}
