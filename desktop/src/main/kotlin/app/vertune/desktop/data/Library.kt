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
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS Playlist (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS SongPlaylistMap (
                        songId TEXT NOT NULL,
                        playlistId INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        PRIMARY KEY (songId, playlistId)
                    )
                    """.trimIndent()
                )
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS Lyrics (songId TEXT PRIMARY KEY NOT NULL, fixed TEXT, synced TEXT)"
                )
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS SearchQuery (query TEXT PRIMARY KEY NOT NULL, at INTEGER NOT NULL)"
                )
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS Bookmark (
                        id TEXT PRIMARY KEY NOT NULL,
                        kind TEXT NOT NULL,
                        title TEXT,
                        subtitle TEXT,
                        thumbnail TEXT,
                        bookmarkedAt INTEGER NOT NULL
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

    // ---- Playlists (like the phone app's local playlists)

    data class PlaylistPreview(val id: Long, val name: String, val songCount: Int, val thumbnails: List<String>)

    enum class PlaylistSort { Name, SongCount, DateAdded }

    @Synchronized
    fun playlists(sort: PlaylistSort = PlaylistSort.DateAdded, descending: Boolean = true): List<PlaylistPreview> {
        val order = when (sort) {
            PlaylistSort.Name -> "p.name COLLATE NOCASE"
            PlaylistSort.SongCount -> "songCount"
            PlaylistSort.DateAdded -> "p.createdAt"
        } + if (descending) " DESC" else " ASC"
        val rows = connection.createStatement().use { st ->
            st.executeQuery(
                "SELECT p.id, p.name, (SELECT COUNT(*) FROM SongPlaylistMap m WHERE m.playlistId = p.id) AS songCount " +
                    "FROM Playlist p ORDER BY $order"
            ).use { rs -> buildList { while (rs.next()) add(Triple(rs.getLong(1), rs.getString(2), rs.getInt(3))) } }
        }
        return rows.map { (id, name, count) ->
            val thumbs = connection.prepareStatement(
                "SELECT s.thumbnail FROM SongPlaylistMap m JOIN Song s ON s.id = m.songId " +
                    "WHERE m.playlistId = ? AND s.thumbnail IS NOT NULL ORDER BY m.position LIMIT 4"
            ).use { st ->
                st.setLong(1, id)
                st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
            }
            PlaylistPreview(id, name, count, thumbs)
        }
    }

    @Synchronized
    fun playlistName(id: Long): String? =
        connection.prepareStatement("SELECT name FROM Playlist WHERE id = ?").use { st ->
            st.setLong(1, id)
            st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    @Synchronized
    fun playlistSongs(id: Long): List<Track> = connection.prepareStatement(
        "SELECT Song.* FROM SongPlaylistMap m JOIN Song ON Song.id = m.songId WHERE m.playlistId = ? ORDER BY m.position"
    ).use { st ->
        st.setLong(1, id)
        st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toTrack()) } }
    }

    @Synchronized
    fun createPlaylist(name: String): Long {
        connection.prepareStatement("INSERT INTO Playlist (name, createdAt) VALUES (?, ?)").use { st ->
            st.setString(1, name)
            st.setLong(2, System.currentTimeMillis())
            st.executeUpdate()
        }
        val id = connection.createStatement().use { it.executeQuery("SELECT last_insert_rowid()").use { rs -> rs.next(); rs.getLong(1) } }
        changed()
        return id
    }

    @Synchronized
    fun renamePlaylist(id: Long, name: String) {
        connection.prepareStatement("UPDATE Playlist SET name = ? WHERE id = ?").use { st ->
            st.setString(1, name)
            st.setLong(2, id)
            st.executeUpdate()
        }
        changed()
    }

    @Synchronized
    fun deletePlaylist(id: Long) {
        connection.prepareStatement("DELETE FROM SongPlaylistMap WHERE playlistId = ?").use { it.setLong(1, id); it.executeUpdate() }
        connection.prepareStatement("DELETE FROM Playlist WHERE id = ?").use { it.setLong(1, id); it.executeUpdate() }
        changed()
    }

    @Synchronized
    fun addToPlaylist(id: Long, track: Track) {
        save(track)
        connection.prepareStatement(
            "INSERT OR IGNORE INTO SongPlaylistMap (songId, playlistId, position) " +
                "VALUES (?, ?, (SELECT COALESCE(MAX(position), -1) + 1 FROM SongPlaylistMap WHERE playlistId = ?))"
        ).use { st ->
            st.setString(1, track.id)
            st.setLong(2, id)
            st.setLong(3, id)
            st.executeUpdate()
        }
        changed()
    }

    @Synchronized
    fun removeFromPlaylist(id: Long, songId: String) {
        connection.prepareStatement("DELETE FROM SongPlaylistMap WHERE playlistId = ? AND songId = ?").use { st ->
            st.setLong(1, id)
            st.setString(2, songId)
            st.executeUpdate()
        }
        changed()
    }

    /** Moves the song at [from] to [to] (positions in the playlist's order). */
    @Synchronized
    fun movePlaylistSong(id: Long, from: Int, to: Int) {
        val songs = playlistSongs(id).map { it.id }.toMutableList()
        if (from !in songs.indices || to !in songs.indices) return
        songs.add(to, songs.removeAt(from))
        connection.prepareStatement("UPDATE SongPlaylistMap SET position = ? WHERE playlistId = ? AND songId = ?").use { st ->
            songs.forEachIndexed { i, songId ->
                st.setInt(1, i)
                st.setLong(2, id)
                st.setString(3, songId)
                st.addBatch()
            }
            st.executeBatch()
        }
        changed()
    }

    /** Every song in the library (played, liked or in a playlist), like the phone's Songs tab. */
    @Synchronized
    fun songs(): List<Track> = query(
        """
        SELECT Song.* FROM Song LEFT JOIN (
            SELECT songId, SUM(playTime) AS total FROM Event GROUP BY songId
        ) e ON e.songId = Song.id
        WHERE e.total IS NOT NULL OR Song.likedAt IS NOT NULL
            OR Song.id IN (SELECT songId FROM SongPlaylistMap)
        ORDER BY Song.rowid DESC
        """.trimIndent()
    )

    // ---- Lyrics (saved, like the phone, so they load instantly next time)

    data class SavedLyrics(val fixed: String?, val synced: String?)

    @Synchronized
    fun lyrics(songId: String): SavedLyrics? =
        connection.prepareStatement("SELECT fixed, synced FROM Lyrics WHERE songId = ?").use { st ->
            st.setString(1, songId)
            st.executeQuery().use { rs -> if (rs.next()) SavedLyrics(rs.getString(1), rs.getString(2)) else null }
        }

    @Synchronized
    fun saveLyrics(songId: String, fixed: String?, synced: String?) {
        connection.prepareStatement(
            "INSERT INTO Lyrics (songId, fixed, synced) VALUES (?, ?, ?) " +
                "ON CONFLICT(songId) DO UPDATE SET fixed = excluded.fixed, synced = excluded.synced"
        ).use { st ->
            st.setString(1, songId)
            st.setString(2, fixed)
            st.setString(3, synced)
            st.executeUpdate()
        }
    }

    // ---- Search history (the phone's search shows past searches)

    @Synchronized
    fun searchHistory(prefix: String = ""): List<String> =
        connection.prepareStatement("SELECT query FROM SearchQuery WHERE query LIKE ? ORDER BY at DESC LIMIT 50").use { st ->
            st.setString(1, "${prefix.replace("%", "")}%")
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }

    @Synchronized
    fun addSearch(query: String) {
        connection.prepareStatement(
            "INSERT INTO SearchQuery (query, at) VALUES (?, ?) ON CONFLICT(query) DO UPDATE SET at = excluded.at"
        ).use { st ->
            st.setString(1, query)
            st.setLong(2, System.currentTimeMillis())
            st.executeUpdate()
        }
    }

    @Synchronized
    fun removeSearch(query: String) {
        connection.prepareStatement("DELETE FROM SearchQuery WHERE query = ?").use { st ->
            st.setString(1, query)
            st.executeUpdate()
        }
        changed()
    }

    @Synchronized
    fun searchHistoryCount(): Int =
        connection.createStatement().use { st -> st.executeQuery("SELECT COUNT(*) FROM SearchQuery").use { it.next(); it.getInt(1) } }

    @Synchronized
    fun clearSearchHistory() {
        connection.createStatement().use { it.executeUpdate("DELETE FROM SearchQuery") }
        changed()
    }

    // ---- Bookmarked albums and artists (the phone's Albums and Artists tabs)

    const val BOOKMARK_ALBUM = "album"
    const val BOOKMARK_ARTIST = "artist"

    data class Bookmark(val id: String, val kind: String, val title: String?, val subtitle: String?, val thumbnail: String?)

    @Synchronized
    fun bookmarks(kind: String): List<Bookmark> =
        connection.prepareStatement("SELECT id, kind, title, subtitle, thumbnail FROM Bookmark WHERE kind = ? ORDER BY bookmarkedAt DESC").use { st ->
            st.setString(1, kind)
            st.executeQuery().use { rs ->
                buildList { while (rs.next()) add(Bookmark(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5))) }
            }
        }

    @Synchronized
    fun isBookmarked(id: String): Boolean =
        connection.prepareStatement("SELECT 1 FROM Bookmark WHERE id = ?").use { st ->
            st.setString(1, id)
            st.executeQuery().use { it.next() }
        }

    @Synchronized
    fun setBookmarked(b: Bookmark, on: Boolean) {
        if (on) connection.prepareStatement(
            "INSERT OR REPLACE INTO Bookmark (id, kind, title, subtitle, thumbnail, bookmarkedAt) VALUES (?, ?, ?, ?, ?, ?)"
        ).use { st ->
            st.setString(1, b.id); st.setString(2, b.kind); st.setString(3, b.title)
            st.setString(4, b.subtitle); st.setString(5, b.thumbnail); st.setLong(6, System.currentTimeMillis())
            st.executeUpdate()
        } else connection.prepareStatement("DELETE FROM Bookmark WHERE id = ?").use { st ->
            st.setString(1, b.id)
            st.executeUpdate()
        }
        changed()
    }

    // ---- Database settings

    @Synchronized
    fun eventCount(): Int =
        connection.createStatement().use { st -> st.executeQuery("SELECT COUNT(*) FROM Event").use { it.next(); it.getInt(1) } }

    /** The phone's "Reset quick picks": deletes the listening history that quick picks come from. */
    @Synchronized
    fun clearEvents() {
        connection.createStatement().use { it.executeUpdate("DELETE FROM Event") }
        changed()
    }

    /** Copies the whole library to [target] (Settings → Database → Backup). */
    @Synchronized
    fun backup(target: File) {
        target.delete()
        connection.createStatement().use { it.executeUpdate("VACUUM INTO '${target.absolutePath.replace("'", "''")}'") }
    }

    /** Replaces the library with a backup made by [backup]. */
    @Synchronized
    fun restore(source: File) {
        connection.createStatement().use { st ->
            st.executeUpdate("ATTACH DATABASE '${source.absolutePath.replace("'", "''")}' AS backup")
            try {
                for (table in listOf("Song", "Event", "Playlist", "SongPlaylistMap", "Lyrics", "SearchQuery", "Bookmark")) {
                    val exists = st.executeQuery("SELECT COUNT(*) FROM backup.sqlite_master WHERE name = '$table'").use { it.next() && it.getInt(1) > 0 }
                    if (!exists) continue
                    st.executeUpdate("DELETE FROM $table")
                    st.executeUpdate("INSERT INTO $table SELECT * FROM backup.$table")
                }
            } finally {
                st.executeUpdate("DETACH DATABASE backup")
                changed()
            }
        }
    }

    /** The saved songs with these ids, in the order given. */
    @Synchronized
    fun songsWithIds(ids: List<String>): List<Track> {
        if (ids.isEmpty()) return emptyList()
        val found = connection.prepareStatement(
            "SELECT * FROM Song WHERE id IN (${ids.joinToString(",") { "?" }})"
        ).use { st ->
            ids.forEachIndexed { i, id -> st.setString(i + 1, id) }
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toTrack()) } }
        }.associateBy { it.id }
        return ids.mapNotNull { found[it] }
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
                val hasPlaylists = st.executeQuery(
                    "SELECT COUNT(*) FROM phone.sqlite_master WHERE name IN ('Playlist', 'SongPlaylistMap')"
                ).use { it.next() && it.getInt(1) == 2 }
                if (hasPlaylists) {
                    val phonePlaylists = st.executeQuery(
                        "SELECT id, name FROM phone.Playlist WHERE browseId IS NULL OR browseId = ''"
                    ).use { rs -> buildList { while (rs.next()) add(rs.getLong(1) to rs.getString(2)) } }
                    val existing = connection.createStatement().use { s2 ->
                        s2.executeQuery("SELECT name FROM Playlist").use { rs -> buildSet { while (rs.next()) add(rs.getString(1)) } }
                    }
                    for ((phoneId, name) in phonePlaylists) {
                        if (name in existing) continue
                        connection.prepareStatement("INSERT INTO Playlist (name, createdAt) VALUES (?, ?)").use { ps ->
                            ps.setString(1, name)
                            ps.setLong(2, System.currentTimeMillis())
                            ps.executeUpdate()
                        }
                        val newId = st.executeQuery("SELECT last_insert_rowid()").use { it.next(); it.getLong(1) }
                        st.executeUpdate(
                            "INSERT OR IGNORE INTO SongPlaylistMap (songId, playlistId, position) " +
                                "SELECT m.songId, $newId, m.position FROM phone.SongPlaylistMap m " +
                                "WHERE m.playlistId = $phoneId AND m.songId IN (SELECT id FROM Song)"
                        )
                    }
                }
                fun phoneHas(table: String) =
                    st.executeQuery("SELECT COUNT(*) FROM phone.sqlite_master WHERE name = '$table'").use { it.next() && it.getInt(1) > 0 }
                if (phoneHas("Lyrics")) runCatching {
                    st.executeUpdate(
                        "INSERT OR IGNORE INTO Lyrics (songId, fixed, synced) SELECT songId, fixed, synced FROM phone.Lyrics"
                    )
                }
                if (phoneHas("SearchQuery")) runCatching {
                    st.executeUpdate(
                        "INSERT OR IGNORE INTO SearchQuery (query, at) SELECT query, id FROM phone.SearchQuery"
                    )
                }
                if (hasAlbums) runCatching {
                    st.executeUpdate(
                        "INSERT OR IGNORE INTO Bookmark (id, kind, title, subtitle, thumbnail, bookmarkedAt) " +
                            "SELECT id, 'album', title, authorsText, thumbnailUrl, bookmarkedAt FROM phone.Album WHERE bookmarkedAt IS NOT NULL"
                    )
                }
                if (phoneHas("Artist")) runCatching {
                    st.executeUpdate(
                        "INSERT OR IGNORE INTO Bookmark (id, kind, title, subtitle, thumbnail, bookmarkedAt) " +
                            "SELECT id, 'artist', name, NULL, thumbnailUrl, bookmarkedAt FROM phone.Artist WHERE bookmarkedAt IS NOT NULL"
                    )
                }
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
