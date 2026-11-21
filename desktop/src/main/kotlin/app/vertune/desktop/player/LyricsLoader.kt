package app.vertune.desktop.player

import app.vertune.desktop.data.Library
import app.vertune.desktop.data.Track
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.bodies.NextBody
import app.vitune.providers.innertube.requests.lyrics
import app.vitune.providers.kugou.KuGou
import app.vitune.providers.lrclib.LrcLib
import app.vitune.providers.lrclib.LrcParser
import app.vitune.providers.lrclib.toLrcFile
import app.vitune.providers.wordsync.WordLyrics
import app.vitune.providers.wordsync.WordSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Finds lyrics the same way the phone does (ui/screens/player/Lyrics.kt): plain lyrics from
 * YouTube Music or LrcLib, synced lyrics from LrcLib or KuGou, and saves them in the library.
 */
object LyricsLoader {
    suspend fun load(track: Track, durationMs: Long): Library.SavedLyrics = withContext(Dispatchers.IO) {
        val saved = runCatching { Library.lyrics(track.id) }.getOrNull()
        if (saved?.fixed != null && saved.synced != null) return@withContext saved

        val artist = track.artists.orEmpty()
        val title = track.title
        val duration = durationMs.milliseconds

        val fixed = saved?.fixed
            ?: Innertube.lyrics(NextBody(videoId = track.id))?.getOrNull()
            ?: LrcLib.bestLyrics(artist = artist, title = title, duration = duration, album = track.album, synced = false)
                ?.map { it?.text }?.getOrNull()

        val synced = saved?.synced
            ?: LrcLib.bestLyrics(artist = artist, title = title, duration = duration, album = track.album)
                ?.map { it?.text }?.getOrNull()
            ?: LrcLib.bestLyrics(artist = artist, title = title.split("(")[0].trim(), duration = duration, album = track.album)
                ?.map { it?.text }?.getOrNull()
            ?: KuGou.lyrics(artist = artist, title = title, duration = durationMs / 1000)?.map { it?.value }?.getOrNull()

        Library.SavedLyrics(fixed.orEmpty(), synced.orEmpty(), saved?.word).also {
            runCatching {
                Library.save(track)
                Library.saveLyrics(track.id, it.fixed, it.synced)
            }
        }
    }

    /**
     * Word-by-word lyrics from NetEase, then QQ Music, like the phone. Returns what was saved
     * ("" when neither has them), or null if the lookup failed and should be tried again later.
     */
    suspend fun loadWord(track: Track, durationMs: Long): String? = withContext(Dispatchers.IO) {
        val result = WordSync.lyrics(title = track.title, artist = track.artists.orEmpty(), durationMs = durationMs)
        if (result == null || result.isFailure) return@withContext null
        val word = result.getOrNull()?.lyrics?.serialize().orEmpty()
        runCatching {
            Library.save(track)
            Library.saveWordLyrics(track.id, word)
        }
        word
    }

    fun parseWord(text: String?): WordLyrics? = WordLyrics.parseYrc(text)

    /** Synced lyrics as (time in ms → line), with the file's offset applied; null if they can't be read. */
    fun parseSynced(text: String?): List<Pair<Long, String>>? {
        if (text.isNullOrBlank()) return null
        val file = LrcParser.parse(text)?.toLrcFile() ?: return null
        val offset = file.offset?.inWholeMilliseconds ?: 0L
        return file.lines.entries.sortedBy { it.key }.map { (it.key - offset) to it.value }.takeIf { it.isNotEmpty() }
    }
}
