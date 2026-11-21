package app.vitune.providers.wordsync

/**
 * Spots the credit lines lyrics sites put before the song ("Out Of Reach - BoyWithUke",
 * "Lyrics by: …", "作曲 : …") so they can be left out of synced lyrics.
 */
object LyricsCredits {
    private val roles = Regex(
        """^\s*(lyrics?|lyricist|words|written|writer|composed|composer|composition|music|produced|producer|""" +
            """production|arranged|arranger|arrangement|mixed|mixing|mix|mastered|mastering|recorded|recording|""" +
            """engineer|engineered|vocals?|vocal producer|backing vocals?|guitar|bass|drums|keyboards?|piano|""" +
            """programming|publisher|label|original|op|sp|isrc|作词|作詞|作曲|编曲|編曲|制作人?|製作人?|监制|混音|母带|""" +
            """录音|和声|吉他|贝斯|鼓|键盘|出品|发行|词|曲)(\s+by)?\s*[:：]""",
        RegexOption.IGNORE_CASE
    )

    // "Song - Artist" as the very first line
    private val titleLine = Regex("""^.{1,80}\s+[-–—]\s+.{1,80}$""")

    fun isCredit(line: String, isFirst: Boolean): Boolean {
        val text = line.trim()
        if (text.isEmpty()) return false
        if (roles.containsMatchIn(text)) return true
        return isFirst && titleLine.matches(text)
    }

    /**
     * Drops credit lines from the start of the lyrics (and any role lines like "Mixed by:"
     * later on), keeping everything else in order.
     */
    fun <T> strip(lines: List<T>, text: (T) -> String): List<T> {
        var leading = true
        var firstText = true
        return lines.filter { item ->
            val line = text(item)
            if (line.isBlank()) return@filter true
            val isFirst = firstText
            firstText = false
            val credit = if (leading) isCredit(line, isFirst) else roles.containsMatchIn(line.trim())
            if (!credit) leading = false
            !credit
        }
    }
}
