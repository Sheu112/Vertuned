package app.vertune.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image

@Composable
fun App(nav: Navigator, logo: Painter) {
    VertuneTheme {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.weight(1f)) {
                Sidebar(nav, logo)
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    TopBar(nav)
                    Box(Modifier.weight(1f).fillMaxWidth()) { Content(nav) }
                }
                if (nav.showQueue) QueuePanel(nav)
            }
            PlayerBar(nav)
        }
    }
}

@Composable
private fun Content(nav: Navigator) {
    val holder = rememberSaveableStateHolder()
    val screen = nav.current
    holder.SaveableStateProvider(screen.toString()) {
        when (screen) {
            Screen.Home -> HomeScreen(nav)
            is Screen.Search -> SearchScreen(screen.query, nav)
            is Screen.Album -> AlbumScreen(screen.browseId, nav)
            is Screen.Artist -> ArtistScreen(screen.browseId, nav)
            is Screen.Playlist -> PlaylistScreen(screen.browseId, nav)
            Screen.Favorites -> FavoritesScreen(nav)
            Screen.History -> HistoryScreen(nav)
            Screen.Top -> TopScreen(nav)
            Screen.Settings -> SettingsScreen()
        }
    }
}

@Composable
private fun TopBar(nav: Navigator) {
    var text by remember { mutableStateOf("") }
    val screen = nav.current
    LaunchedEffect(screen) { if (screen is Screen.Search) text = screen.query }

    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = nav::back, enabled = nav.canGoBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                "Back",
                tint = if (nav.canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Search songs, albums, artists") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(50),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search(nav, text) }),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = Color.Transparent
            ),
            modifier = Modifier.width(460.dp).onEnter { search(nav, text) }
        )
    }
}

private fun search(nav: Navigator, text: String) {
    val q = text.trim()
    if (q.isEmpty()) return
    if (nav.current is Screen.Search) nav.replace(Screen.Search(q)) else nav.go(Screen.Search(q))
}

private fun Modifier.onEnter(action: () -> Unit) = this.then(
    Modifier.onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
            action()
            true
        } else false
    }
)

@Composable
private fun Sidebar(nav: Navigator, logo: Painter) {
    Column(
        Modifier.width(220.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(12.dp)
    ) {
        Row(Modifier.padding(start = 8.dp, top = 6.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(logo, null, Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Text("Vertune", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        NavItem("Home", Icons.Filled.Home, nav.current == Screen.Home) { nav.root(Screen.Home) }
        NavItem("Search", Icons.Filled.Search, nav.current is Screen.Search) { nav.root(Screen.Search()) }
        Spacer(Modifier.height(16.dp))
        Text(
            "Your library",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
        )
        NavItem("My top 50", Icons.Filled.Leaderboard, nav.current == Screen.Top) { nav.root(Screen.Top) }
        NavItem("Favourites", Icons.Filled.Favorite, nav.current == Screen.Favorites) { nav.root(Screen.Favorites) }
        NavItem("History", Icons.Filled.History, nav.current == Screen.History) { nav.root(Screen.History) }
        Spacer(Modifier.weight(1f))
        NavItem("Settings", Icons.Filled.Settings, nav.current == Screen.Settings) { nav.root(Screen.Settings) }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
