package app.vertune.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.vertune.desktop.data.Library
import app.vertune.desktop.data.Track
import app.vertune.desktop.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val pad = Modifier.padding(horizontal = 24.dp)

@Composable
private fun <T> rememberLibrary(vararg keys: Any?, read: () -> T): T? {
    val version by Library.version.collectAsState()
    val value by produceState<T?>(null, version, *keys) {
        value = withContext(Dispatchers.IO) { runCatching(read).getOrNull() }
    }
    return value
}

@Composable
private fun TrackListPage(title: String, tracks: List<Track>?, empty: String, nav: Navigator) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(pad.padding(top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (!tracks.isNullOrEmpty()) {
                    PillButton("Play") { PlayerController.play(tracks, 0) }
                    Spacer(Modifier.width(10.dp))
                    PillButton("Shuffle", primary = false) { PlayerController.play(tracks.shuffled(), 0) }
                }
            }
        }
        when {
            tracks == null -> item { Loading() }
            tracks.isEmpty() -> item { Message(empty) }
            else -> itemsIndexed(tracks) { i, t -> Row(pad) { SongRow(t, nav, queue = tracks, index = i) } }
        }
    }
}

@Composable
fun FavoritesScreen(nav: Navigator) = TrackListPage(
    title = "Favourites",
    tracks = rememberLibrary { Library.favorites() },
    empty = "Songs you like with the ♥ button show up here.",
    nav = nav
)

@Composable
fun HistoryScreen(nav: Navigator) = TrackListPage(
    title = "History",
    tracks = rememberLibrary { Library.history() },
    empty = "Songs you listen to show up here.",
    nav = nav
)

private enum class Period(val label: String, val days: Long?) {
    Week("Past week", 7), Month("Past month", 30), Year("Past year", 365), All("All time", null)
}

/** My top 50: the songs you've listened to the longest, with how long you listened to each. */
@Composable
fun TopScreen(nav: Navigator) {
    var period by remember { mutableStateOf(Period.All) }
    val since = period.days?.let { System.currentTimeMillis() - it * 24 * 60 * 60 * 1000 }
    val top = rememberLibrary(period) { Library.top(TOP_COUNT, since) }
    val total = rememberLibrary(period) { Library.totalListenedMs(since) } ?: 0L
    val tracks = top?.map { it.track }.orEmpty()

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(pad.padding(top = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("My top 50", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (tracks.isNotEmpty()) PillButton("Play") { PlayerController.play(tracks, 0) }
                }
                Text(
                    "Listened ${formatListened(total)} " +
                        if (period == Period.All) "in total" else "in the ${period.label.lowercase()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Period.entries.forEach { p ->
                        val selected = p == period
                        Text(
                            p.label,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { period = p }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
        when {
            top == null -> item { Loading() }
            top.isEmpty() -> item { Message("Listen to some music and your most played songs show up here.") }
            else -> itemsIndexed(top) { i, entry ->
                Row(pad) {
                    SongRow(
                        entry.track,
                        nav,
                        queue = tracks,
                        index = i,
                        trailingText = formatListened(entry.listenedMs),
                        subtitle = listOfNotNull(
                            entry.track.artists,
                            "${entry.plays} ${if (entry.plays == 1) "play" else "plays"}"
                        ).joinToString(" • "),
                        leading = {
                            Text(
                                "${i + 1}",
                                modifier = Modifier.width(36.dp),
                                fontWeight = FontWeight.Bold,
                                color = if (i < 3) MaterialTheme.colorScheme.primary else Color.Unspecified
                            )
                        }
                    )
                }
            }
        }
    }
}

private const val TOP_COUNT = 50
