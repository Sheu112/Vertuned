package app.vertune.desktop.data

import app.vertune.desktop.Paths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * The local library: songs you played or liked, and a log of how long you listened to each,
 * which is what "My top 50" is built from. Stored in a small SQLite file.
 */
object Library {
    private val connection: Connection by lazy {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${Paths.databaseFile.absolutePath}").apply {
            createStatement().use { st ->
                st.executeUpdate("PRAGMA journal_mode=WAL")
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS Song (
                        id TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        artists TEXT,
                        artistId TEXT,
                        album TEXT,
                        albumId TEXT,
                        thumbnail TEXT,
                        durationText TEXT,
                        explicit INTEGER NOT NULL DEFAULT 0,
                        likedAt INTEGER
                    )
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS Event (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        songId TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        playTime INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                st.executeUpdate("CREATE INDEX IF NOT EXISTS Event_songId ON Event(songId)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS Event_timestamp ON Event(timestamp)")
            }
        }
    }

    private val mutableVersion = MutableStateFlow(0)

    /** Changes every time the library changes, so screens know to reload. */
    val version: StateFlow<Int> = mutableVersion.asStateFlow()

    private fun changed() {
        mutableVersion.value++
    }

    @Synchronized
    fun save(track: Track) {
        connection.prepareStatement(
            """
            INSERT INTO Song (id, title, artists, artistId, album, albumId, thumbnail, durationText, explicit)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                title = excluded.title,
                artists = COALESCE(excluded.artists, artists),
                artistId = COALESCE(excluded.artistId, artistId),
                album = COALESCE(excluded.album, album),
                albumId = COALESCE(excluded.albumId, albumId),
                thumbnail = COALESCE(excluded.thumbnail, thumbnail),
                durationText = COALESCE(excluded.durationText, durationText),
                explicit = excluded.explicit
            """.trimIndent()
        ).use { st ->
            st.setString(1, track.id)
            st.setString(2, track.title)
            st.setString(3, track.artists)
            st.setString(4, track.artistId)
            st.setString(5, track.album)
            st.setString(6, track.albumId)
            st.setString(7, track.thumbnail)
            st.setString(8, track.durationText)
            st.setInt(9, if (track.explicit) 1 else 0)
            st.executeUpdate()
        }
        changed()
    }

    @Synchronized
    fun isLiked(id: String): Boolean =
        connection.prepareStatement("SELECT likedAt FROM Song WHERE id = ?").use { st ->
            st.setString(1, id)
            st.executeQuery().use { rs -> rs.next() && rs.getObject(1) != null }
        }

    @Synchronized
    fun setLiked(track: Track, liked: Boolean) {
        save(track)
        connection.prepareStatement("UPDATE Song SET likedAt = ? WHERE id = ?").use { st ->
            if (liked) st.setLong(1, System.currentTimeMillis()) else st.setNull(1, java.sql.Types.INTEGER)
            st.setString(2, track.id)
            st.executeUpdate()
        }
        changed()
    }

    /** Records that [track] was listened to for [playTimeMs]. */
    @Synchronized
    fun addListen(track: Track, playTimeMs: Long, at: Long = System.currentTimeMillis()) {
        if (playTimeMs < MIN_LISTEN_MS) return
        save(track)
        connection.prepareStatement("INSERT INTO Event (songId, timestamp, playTime) VALUES (?, ?, ?)").use { st ->
            st.setString(1, track.id)
            st.setLong(2, at)
            st.setLong(3, playTimeMs)
            st.executeUpdate()
        }
        changed()
    }

    @Synchronized
    fun favorites(): List<Track> = query("SELECT * FROM Song WHERE likedAt IS NOT NULL ORDER BY likedAt DESC")

    @Synchronized
    fun history(limit: Int = 200): List<Track> = query(
        """
        SELECT Song.* FROM Song JOIN (
            SELECT songId, MAX(timestamp) AS last FROM Event GROUP BY songId
        ) e ON e.songId = Song.id
        ORDER BY e.last DESC LIMIT $limit
        """.trimIndent()
    )

    data class TopEntry(val track: Track, val listenedMs: Long, val plays: Int)

    /** The most listened songs, by total listening time. [sinceMs] limits it to a recent period. */
    @Synchronized
    fun top(limit: Int = 50, sinceMs: Long? = null): List<TopEntry> {
        val where = if (sinceMs != null) "WHERE Event.timestamp >= ?" else ""
        return connection.prepareStatement(
            """
            SELECT Song.*, SUM(Event.playTime) AS listened, COUNT(Event.id) AS plays
            FROM Event JOIN Song ON Song.id = Event.songId
            $where
            GROUP BY Event.songId
            ORDER BY listened DESC
            LIMIT $limit
            """.trimIndent()
        ).use { st ->
            if (sinceMs != null) st.setLong(1, sinceMs)
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(TopEntry(rs.toTrack(), rs.getLong("listened"), rs.getInt("plays")))
                }
            }
        }
    }

    @Synchronized
    fun totalListenedMs(sinceMs: Long? = null): Long =
        connection.prepareStatement(
            "SELECT COALESCE(SUM(playTime), 0) FROM Event" + if (sinceMs != null) " WHERE timestamp >= ?" else ""
        ).use { st ->
            if (sinceMs != null) st.setLong(1, sinceMs)
            st.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
        }

    @Synchronized
    fun albumTitle(songId: String): String? =
        connection.prepareStatement("SELECT album FROM Song WHERE id = ?").use { st ->
            st.setString(1, songId)
            st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    /**
     * Imports songs, likes and listening history from a backup made in the phone app
     * (Settings → Database → Backup). Returns how many songs and listens were imported.
     */
    @Synchronized
    fun importPhoneBackup(file: File): Pair<Int, Int> {
        connection.createStatement().use { st ->
            st.executeUpdate("ATTACH DATABASE '${file.absolutePath.replace("'", "''")}' AS phone")
            try {
                val hasAlbums = st.executeQuery(
                    "SELECT COUNT(*) FROM phone.sqlite_master WHERE name IN ('Album', 'SongAlbumMap')"
                ).use { it.next() && it.getInt(1) == 2 }

                val albumSelect = if (hasAlbums) {
                    "(SELECT a.title FROM phone.SongAlbumMap m JOIN phone.Album a ON a.id = m.albumId WHERE m.songId = s.id LIMIT 1)"
                } else "NULL"
                val albumIdSelect = if (hasAlbums) {
                    "(SELECT m.albumId FROM phone.SongAlbumMap m WHERE m.songId = s.id LIMIT 1)"
                } else "NULL"

                val songs = st.executeUpdate(
                    """
                    INSERT INTO Song (id, title, artists, album, albumId, thumbnail, durationText, likedAt)
                    SELECT s.id, s.title, s.artistsText, $albumSelect, $albumIdSelect, s.thumbnailUrl, s.durationText, s.likedAt
                    FROM phone.Song s WHERE s.id NOT LIKE 'local:%'
                    ON CONFLICT(id) DO UPDATE SET likedAt = COALESCE(likedAt, excluded.likedAt),
                        album = COALESCE(album, excluded.album)
                    """.trimIndent()
                )
                val events = st.executeUpdate(
                    """
                    INSERT INTO Event (songId, timestamp, playTime)
                    SELECT e.songId, e.timestamp, e.playTime FROM phone.Event e
                    WHERE e.songId IN (SELECT id FROM Song)
                    AND NOT EXISTS (
                        SELECT 1 FROM Event x WHERE x.songId = e.songId AND x.timestamp = e.timestamp
                    )
                    """.trimIndent()
                )
                return songs to events
            } finally {
                st.executeUpdate("DETACH DATABASE phone")
                changed()
            }
        }
    }

    private fun query(sql: String): List<Track> = connection.createStatement().use { st ->
        st.executeQuery(sql).use { rs -> buildList { while (rs.next()) add(rs.toTrack()) } }
    }

    private fun ResultSet.toTrack() = Track(
        id = getString("id"),
        title = getString("title"),
        artists = getString("artists"),
        artistId = getString("artistId"),
        album = getString("album"),
        albumId = getString("albumId"),
        thumbnail = getString("thumbnail"),
        durationText = getString("durationText"),
        explicit = getInt("explicit") != 0
    )

    private const val MIN_LISTEN_MS = 5_000L
}
