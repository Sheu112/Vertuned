package app.vitune.providers.wordsync

import app.vitune.providers.utils.runCatchingCancellable
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.abs

/**
 * Finds word-by-word lyrics: NetEase Cloud Music first (YRC), then QQ Music (QRC).
 * Only the song's title and artist are sent to either site.
 */
object WordSync {
    private const val USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0 Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true }

    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = true
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
            }
        }
    }

    /** Where a result came from, shown in the lyrics menu. */
    enum class Source { NetEase, QQMusic }

    data class Found(val lyrics: WordLyrics, val source: Source)

    /**
     * Word-timed lyrics for a song, or null when neither site has them.
     * [durationMs] helps pick the right version when a song has several releases.
     */
    suspend fun lyrics(title: String, artist: String, durationMs: Long): Result<Found?>? =
        runCatchingCancellable {
            val cleanTitle = cleanTitle(title)
            val artists = splitArtists(artist)

            runCatchingCancellable { netEase(cleanTitle, artists, durationMs) }
                ?.getOrNull()
                ?.let { return@runCatchingCancellable Found(it, Source.NetEase) }

            runCatchingCancellable { qqMusic(cleanTitle, artists, durationMs) }
                ?.getOrNull()
                ?.let { return@runCatchingCancellable Found(it, Source.QQMusic) }

            null
        }

    // ---- Matching

    private fun cleanTitle(title: String) = title
        .replace(Regex("""\s*[(\[](feat\.?|ft\.?|with|from|prod\.?)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*-\s*(official|lyric|audio|video).*$""", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun splitArtists(artist: String) = artist
        .split(Regex("""\s*(,|&|\bx\b|\bfeat\.?|\bft\.?|/|、)\s*""", RegexOption.IGNORE_CASE))
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }

    internal fun normalize(s: String) = s.lowercase()
        .replace('’', '\'')
        .replace(Regex("""\(.*?\)|\[.*?]"""), "")
        .replace(Regex("""\bfeat\..*"""), "")
        .replace(Regex("""[^\p{L}\p{N}']+"""), " ")
        .trim()

    private data class Candidate(val name: String, val artists: List<String>, val durationMs: Long, val load: suspend () -> WordLyrics?)

    /** Tries matching candidates, closest length first, until one actually has word timing. */
    private suspend fun pick(title: String, artists: List<String>, durationMs: Long, candidates: List<Candidate>): WordLyrics? {
        val wanted = normalize(title)
        val matching = candidates.filter { c ->
            normalize(c.name) == wanted &&
                (artists.isEmpty() || c.artists.any { a -> artists.any { a.contains(it) || it.contains(a) } })
        }.sortedBy { if (durationMs > 0) abs(it.durationMs - durationMs) else 0L }
            .filter { durationMs <= 0 || abs(it.durationMs - durationMs) <= 10_000 }

        for (c in matching.take(4)) {
            runCatchingCancellable { c.load() }?.getOrNull()?.let { return it }
        }
        return null
    }

    // ---- NetEase Cloud Music

    private suspend fun netEase(title: String, artists: List<String>, durationMs: Long): WordLyrics? {
        val body = client.get("https://music.163.com/api/cloudsearch/pc") {
            header("Referer", "https://music.163.com/")
            header("User-Agent", USER_AGENT)
            parameter("s", "$title ${artists.firstOrNull().orEmpty()}".trim())
            parameter("type", 1)
            parameter("limit", 20)
        }.bodyAsText()

        val songs = json.parseToJsonElement(body).obj("result")?.arr("songs").orEmpty()
        val candidates = songs.mapNotNull { el ->
            val song = el as? JsonObject ?: return@mapNotNull null
            val id = song.long("id") ?: return@mapNotNull null
            Candidate(
                name = song.str("name").orEmpty(),
                artists = song.arr("ar").orEmpty().mapNotNull { (it as? JsonObject)?.str("name")?.lowercase() },
                durationMs = song.long("dt") ?: 0L,
                load = { netEaseLyrics(id) }
            )
        }
        return pick(title, artists, durationMs, candidates)
    }

    private suspend fun netEaseLyrics(id: Long): WordLyrics? {
        val body = client.get("https://music.163.com/api/song/lyric/v1") {
            header("Referer", "https://music.163.com/")
            header("User-Agent", USER_AGENT)
            parameter("id", id)
            parameter("lv", -1)
            parameter("yv", -1)
            parameter("tv", -1)
        }.bodyAsText()
        val yrc = json.parseToJsonElement(body).obj("yrc")?.str("lyric")
        return WordLyrics.parseYrc(yrc)
    }

    // ---- QQ Music

    private suspend fun qqMusic(title: String, artists: List<String>, durationMs: Long): WordLyrics? {
        val body = client.get("https://c.y.qq.com/soso/fcgi-bin/client_search_cp") {
            header("Referer", "https://y.qq.com/")
            header("User-Agent", USER_AGENT)
            parameter("w", "$title ${artists.firstOrNull().orEmpty()}".trim())
            parameter("format", "json")
            parameter("n", 20)
            parameter("p", 1)
        }.bodyAsText()

        val songs = json.parseToJsonElement(body).obj("data")?.obj("song")?.arr("list").orEmpty()
        val candidates = songs.mapNotNull { el ->
            val song = el as? JsonObject ?: return@mapNotNull null
            val id = song.long("songid") ?: return@mapNotNull null
            Candidate(
                name = song.str("songname").orEmpty(),
                artists = song.arr("singer").orEmpty().mapNotNull { (it as? JsonObject)?.str("name")?.lowercase() },
                durationMs = (song.long("interval") ?: 0L) * 1000,
                load = { qqLyrics(id) }
            )
        }
        return pick(title, artists, durationMs, candidates)
    }

    private val cdataContent = Regex("""<content[^>]*><!\[CDATA\[([0-9A-Fa-f]+)]]>""")
    private val lyricContent = Regex("""LyricContent="([^"]*)"""")

    private suspend fun qqLyrics(id: Long): WordLyrics? {
        val xml = client.get("https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg") {
            header("Referer", "https://y.qq.com/")
            header("User-Agent", USER_AGENT)
            parameter("version", 15)
            parameter("miniversion", 82)
            parameter("lrctype", 4)
            parameter("musicid", id)
        }.bodyAsText()

        val hex = cdataContent.find(xml)?.groupValues?.get(1) ?: return null
        val decrypted = QrcDecrypter.decrypt(hex)
        val content = lyricContent.find(decrypted)?.groupValues?.get(1)?.let(::unescapeXml) ?: decrypted
        return WordLyrics.parseQrc(content)
    }

    private fun unescapeXml(s: String) = s
        .replace("&#10;", "\n")
        .replace("&#13;", "")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    // ---- JSON helpers

    private fun JsonElement.obj(key: String) = (this as? JsonObject)?.get(key) as? JsonObject
    private fun JsonObject.arr(key: String) = get(key) as? JsonArray
    private fun JsonObject.str(key: String) = runCatching { get(key)?.jsonPrimitive?.contentOrNull }.getOrNull()
    private fun JsonObject.long(key: String) = runCatching { get(key)?.jsonPrimitive?.longOrNull }.getOrNull()
}
