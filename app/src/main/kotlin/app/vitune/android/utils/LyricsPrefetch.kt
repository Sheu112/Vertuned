package app.vitune.android.utils

import androidx.media3.common.MediaItem
import app.vitune.android.Database
import app.vitune.android.models.Lyrics
import app.vitune.android.service.LOCAL_KEY_PREFIX
import app.vitune.core.ui.utils.songBundle
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.bodies.NextBody
import app.vitune.providers.innertube.requests.lyrics
import app.vitune.providers.kugou.KuGou
import app.vitune.providers.lrclib.LrcLib
import app.vitune.providers.wordsync.WordSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration.Companion.milliseconds

/**
 * Saves lyrics (plain, synced and word-by-word) for songs ahead of time, so they work offline.
 * Used by the playlist download button. Looks lyrics up the same way the lyrics screen does
 * (ui/screens/player/Lyrics.kt) and skips whatever is already saved.
 */
object LyricsPrefetch {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val slots = Semaphore(2)

    fun prefetch(mediaItems: List<MediaItem>) {
        mediaItems.distinctBy { it.mediaId }
            .filterNot { it.mediaId.startsWith(LOCAL_KEY_PREFIX) }
            .forEach { item ->
                scope.launch {
                    slots.withPermit { runCatching { fetch(item) }.exceptionOrNull()?.printStackTrace() }
                }
            }
    }

    private suspend fun fetch(item: MediaItem) {
        val id = item.mediaId
        val saved = Database.lyrics(id).first()
        if (saved?.fixed != null && saved.synced != null && saved.word != null) return

        val metadata = item.mediaMetadata
        val title = metadata.title?.toString().orEmpty()
        val artist = metadata.artist?.toString().orEmpty()
        val album = metadata.albumTitle?.toString()
        val durationMs = parseDuration(metadata.extras?.songBundle?.durationText)
        val duration = durationMs.milliseconds

        val fixed = saved?.fixed
            ?: Innertube.lyrics(NextBody(videoId = id))?.getOrNull()
            ?: LrcLib.bestLyrics(artist = artist, title = title, duration = duration, album = album, synced = false)
                ?.map { it?.text }?.getOrNull()

        val synced = saved?.synced
            ?: LrcLib.bestLyrics(artist = artist, title = title, duration = duration, album = album)
                ?.map { it?.text }?.getOrNull()
            ?: LrcLib.bestLyrics(artist = artist, title = title.split("(")[0].trim(), duration = duration, album = album)
                ?.map { it?.text }?.getOrNull()
            ?: KuGou.lyrics(artist = artist, title = title, duration = durationMs / 1000)
                ?.map { it?.value }?.getOrNull()

        // Null after a failed lookup, so the lyrics screen tries again later.
        val word = saved?.word ?: WordSync.lyrics(title = title, artist = artist, durationMs = durationMs)
            ?.takeIf { it.isSuccess }
            ?.let { result -> result.getOrNull()?.lyrics?.serialize().orEmpty() }

        runCatching {
            // Lyrics belong to a saved song, so make sure it's in the library first
            runCatching { Database.insert(item) }
            Database.upsert(
                Lyrics(
                    songId = id,
                    fixed = fixed.orEmpty(),
                    synced = synced.orEmpty(),
                    startTime = saved?.startTime,
                    word = word
                )
            )
        }
    }

    /** "3:05" or "1:02:03" in milliseconds; 0 if unknown. */
    private fun parseDuration(text: String?): Long = text
        ?.split(':')
        ?.mapNotNull { it.trim().toLongOrNull() }
        ?.fold(0L) { acc, part -> acc * 60 + part }
        ?.times(1000)
        ?: 0L
}
