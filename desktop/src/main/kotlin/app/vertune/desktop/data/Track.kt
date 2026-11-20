package app.vertune.desktop.data

import app.vitune.providers.innertube.Innertube

/** A song, as the Mac app stores and shows it. */
data class Track(
    val id: String,
    val title: String,
    val artists: String? = null,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val thumbnail: String? = null,
    val durationText: String? = null,
    val explicit: Boolean = false
) {
    /** A bigger version of the artwork, for the player and Discord. */
    val largeThumbnail: String? get() = thumbnailSized(544)

    /**
     * The artwork at [size] pixels, like the phone's String.thumbnail(): Google image links
     * take a size; YouTube video stills are swapped for the full-size one.
     */
    fun thumbnailSized(size: Int): String? = thumbnail?.let { url ->
        when {
            url.startsWith("https://lh3.googleusercontent.com") || url.startsWith("https://yt3.googleusercontent.com") ->
                url.substringBefore('=') + "=w$size-h$size"
            url.startsWith("https://yt3.ggpht.com") -> url.substringBefore('=') + "=w$size-h$size-s$size"
            url.contains("i.ytimg.com/vi/") -> "https://i.ytimg.com/vi/" +
                url.substringAfter("/vi/").substringBefore('/') + "/" + (if (size > 480) "maxresdefault.jpg" else "hqdefault.jpg")
            else -> url
        }
    }

    /** A smaller backup for [thumbnailSized], for videos that have no full-size still. */
    fun thumbnailFallback(): String? = thumbnail?.let { url ->
        if (url.contains("i.ytimg.com/vi/")) "https://i.ytimg.com/vi/" + url.substringAfter("/vi/").substringBefore('/') + "/hqdefault.jpg"
        else url
    }

    val durationMs: Long
        get() = durationText?.let(::parseDuration) ?: 0L

    companion object {
        fun parseDuration(text: String): Long? = runCatching {
            text.split(":").map { it.trim().toLong() }.fold(0L) { acc, part -> acc * 60 + part } * 1000
        }.getOrNull()
    }
}

fun Innertube.SongItem.toTrack(): Track? {
    val id = info?.endpoint?.videoId ?: return null
    return Track(
        id = id,
        title = info?.name ?: return null,
        artists = authors?.mapNotNull { it.name }?.filter { it.isNotBlank() }
            ?.joinToString("")?.takeIf { it.isNotBlank() },
        artistId = authors?.firstNotNullOfOrNull { it.endpoint?.browseId },
        album = album?.name,
        albumId = album?.endpoint?.browseId,
        thumbnail = thumbnail?.size(226),
        durationText = durationText,
        explicit = explicit
    )
}
