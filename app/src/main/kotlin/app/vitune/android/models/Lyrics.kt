package app.vitune.android.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Immutable
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Lyrics(
    @PrimaryKey val songId: String,
    val fixed: String?,
    val synced: String?,
    val startTime: Long? = null,
    // Word-by-word lyrics (YRC layout, see WordLyrics). Null = not looked up yet, "" = none found.
    val word: String? = null,
    // How this song's lyrics are shown (see LyricsMode); null = not chosen, which means word-by-word
    val mode: Int? = null
)

/** The ways a song's lyrics can be shown, saved per song in [Lyrics.mode]. Same numbers on the Mac. */
object LyricsMode {
    const val UNSYNCED = 0
    const val LINE = 1
    const val WORD = 2
}
