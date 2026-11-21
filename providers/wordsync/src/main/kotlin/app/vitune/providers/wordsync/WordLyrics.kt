package app.vitune.providers.wordsync

/**
 * Lyrics where every word has its own timing, so the player can light up words as they're sung.
 *
 * Saved as text in NetEase's YRC layout, one line per lyric line:
 * `[lineStart,lineDuration](wordStart,wordDuration,0)word(wordStart,wordDuration,0)word`
 * (all times in milliseconds from the start of the song).
 */
data class WordLyrics(val lines: List<Line>) {
    data class Word(val start: Long, val duration: Long, val text: String) {
        val end get() = start + duration
    }

    data class Line(val start: Long, val duration: Long, val words: List<Word>) {
        val end get() = start + duration
        val text get() = words.joinToString("") { it.text }.trim()
    }

    fun serialize(): String = lines.joinToString("\n") { line ->
        "[${line.start},${line.duration}]" + line.words.joinToString("") { word ->
            "(${word.start},${word.duration},0)${word.text}"
        }
    }

    /** Plain line-by-line LRC made from the same timings, for when only line sync is wanted. */
    fun toLrc(): String = lines.joinToString("\n") { line ->
        val minutes = line.start / 60_000
        val seconds = (line.start % 60_000) / 1000
        val hundredths = (line.start % 1000) / 10
        String.format(java.util.Locale.ROOT, "[%02d:%02d.%02d]%s", minutes, seconds, hundredths, line.text)
    }

    companion object {
        private val lineHeader = Regex("""^\[(\d+),(\d+)]""")
        private val yrcMark = Regex("""\((\d+),(\d+),-?\d+\)""")
        private val qrcMark = Regex("""\((\d+),(\d+)\)""")

        /** Reads NetEase YRC text (which is also how word lyrics are saved). Null if nothing usable. */
        fun parseYrc(text: String?): WordLyrics? = parse(text) { body ->
            // Timing comes first, then the word runs until the next timing mark.
            val marks = yrcMark.findAll(body).toList()
            marks.mapIndexed { i, m ->
                val end = marks.getOrNull(i + 1)?.range?.first ?: body.length
                Word(m.groupValues[1].toLong(), m.groupValues[2].toLong(), body.substring(m.range.last + 1, end))
            }
        }

        /** Reads decrypted QQ Music QRC text, where each word comes before its timing. */
        fun parseQrc(text: String?): WordLyrics? = parse(text) { body ->
            // The word comes first, then its timing mark.
            val marks = qrcMark.findAll(body).toList()
            marks.mapIndexed { i, m ->
                val start = marks.getOrNull(i - 1)?.range?.last?.plus(1) ?: 0
                Word(m.groupValues[1].toLong(), m.groupValues[2].toLong(), body.substring(start, m.range.first))
            }
        }

        private fun parse(text: String?, words: (String) -> List<Word>): WordLyrics? {
            if (text.isNullOrBlank()) return null
            val lines = text.lineSequence().mapNotNull { raw ->
                val line = raw.trim('\r')
                val header = lineHeader.find(line) ?: return@mapNotNull null
                val parsed = words(line.substring(header.range.last + 1)).filter { it.text.isNotEmpty() }
                if (parsed.isEmpty()) return@mapNotNull null
                Line(header.groupValues[1].toLong(), header.groupValues[2].toLong(), parsed)
            }.sortedBy { it.start }.distinctBy { it.start }.toList()
                .let { all -> LyricsCredits.strip(all) { it.text } }
            // Credits-only results (just "Lyricist: …") aren't worth showing.
            return WordLyrics(lines).takeIf { lines.size >= 3 }
        }
    }
}
