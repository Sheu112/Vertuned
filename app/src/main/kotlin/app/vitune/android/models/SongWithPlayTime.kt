package app.vitune.android.models

import androidx.compose.runtime.Immutable
import androidx.room.Embedded

@Immutable
data class SongWithPlayTime(
    @Embedded val song: Song,
    val playTimeMs: Long
)
