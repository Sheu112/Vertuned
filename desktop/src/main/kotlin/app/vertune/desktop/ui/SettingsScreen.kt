package app.vertune.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.vertune.desktop.AppInfo
import app.vertune.desktop.Paths
import app.vertune.desktop.data.Library
import app.vertune.desktop.data.Settings
import app.vertune.desktop.discord.DiscordPresence
import app.vertune.desktop.player.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = 28.dp).widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun SettingsScreen() {
    val scope = rememberCoroutineScope()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // ---- Discord
        Group("Discord") {
            val enabled by Settings.discordEnabled.flow.collectAsState()
            val token by Settings.discordToken.flow.collectAsState()
            val appId by Settings.discordApplicationId.flow.collectAsState()
            val status by DiscordPresence.status.collectAsState()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show what I'm listening to")
                    Hint("Shows the song and artist on your Discord profile, like on your phone.")
                }
                Switch(checked = enabled, onCheckedChange = { Settings.discordEnabled.value = it })
            }
            OutlinedTextField(
                value = token,
                onValueChange = { Settings.discordToken.value = it.trim() },
                label = { Text("Discord token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.widthIn(min = 420.dp)
            )
            Hint(
                "The same token you use in the phone app. Keep it private, it works like a password. " +
                    "Using it this way is against Discord's terms, as on the phone."
            )
            OutlinedTextField(
                value = appId,
                onValueChange = { Settings.discordApplicationId.value = it.trim() },
                label = { Text("Application ID (for album art, optional)") },
                singleLine = true,
                modifier = Modifier.widthIn(min = 420.dp)
            )
            Text(
                "Connection status: " + when (status) {
                    DiscordPresence.Status.Off -> "Off"
                    DiscordPresence.Status.Connecting -> "Connecting…"
                    DiscordPresence.Status.Connected -> "Connected"
                    DiscordPresence.Status.Reconnecting -> "Reconnecting…"
                    DiscordPresence.Status.TokenRejected -> "Discord rejected the token. Paste a new one."
                },
                color = if (status == DiscordPresence.Status.TokenRejected) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- Library
        Group("Library") {
            var importResult by remember { mutableStateOf<String?>(null) }
            Text("Import from your phone")
            Hint(
                "In the phone app, go to Settings → Database → Backup and save the file, then send it to this Mac " +
                    "(AirDrop, Google Drive, email…). Importing adds your favourites and listening history, " +
                    "so My top 50 matches your phone."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton("Choose backup file…") {
                    val dialog = FileDialog(null as Frame?, "Choose the phone app backup", FileDialog.LOAD)
                    dialog.isVisible = true
                    val file = dialog.file?.let { File(dialog.directory, it) }
                    if (file != null) {
                        importResult = "Importing…"
                        scope.launch {
                            importResult = withContext(Dispatchers.IO) {
                                runCatching { Library.importPhoneBackup(file) }.fold(
                                    { (songs, listens) -> "Imported $songs songs and $listens listens." },
                                    { "That file couldn't be imported: ${it.message}" }
                                )
                            }
                        }
                    }
                }
            }
            importResult?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }

        // ---- Playback
        Group("Playback") {
            var updateResult by remember { mutableStateOf<String?>(null) }
            Text("Update the YouTube downloader")
            Hint("If songs stop playing, YouTube may have changed something. Updating yt-dlp usually fixes it.")
            PillButton("Update yt-dlp", primary = false) {
                updateResult = "Updating…"
                scope.launch { updateResult = StreamResolver.update() }
            }
            updateResult?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        Group("About") {
            Text("${AppInfo.NAME} for Mac ${AppInfo.VERSION}")
            Hint("Your library is stored in ${Paths.dataDir.absolutePath}")
            Spacer(Modifier.width(1.dp))
        }
    }
}
