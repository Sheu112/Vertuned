package app.vertune.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

/** A settings group: accent-coloured title, like the phone's settings pages. */
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = 24.dp).widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(title, style = Type.xs.semiBold.color(LocalPalette.current.accent), modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        content()
    }
}

@Composable
private fun Item(title: String, text: String? = null, trailing: @Composable () -> Unit = {}) {
    val palette = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(title, style = Type.s.semiBold.color(palette.text))
            if (text != null) BasicText(text, style = Type.xs.semiBold.color(palette.textSecondary))
        }
        trailing()
    }
}

@Composable
private fun Hint(text: String, error: Boolean = false) {
    val palette = LocalPalette.current
    BasicText(
        text,
        style = Type.xs.semiBold.color(if (error) palette.red else palette.textSecondary),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

/** The phone's switch: a small pill that turns the accent colour when on. */
@Composable
private fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Box(
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier
            .width(40.dp)
            .height(22.dp)
            .clip(CircleShape)
            .background(if (checked) palette.accent else palette.textDisabled)
            .clickable { onChange(!checked) }
            .padding(3.dp)
    ) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(palette.text))
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, secret: Boolean = false) {
    val palette = LocalPalette.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = Type.xs.medium.color(palette.text),
        cursorBrush = SolidColor(palette.text),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(palette.background1).padding(12.dp)) {
                if (value.isEmpty()) BasicText(hint, style = Type.xs.medium.color(palette.textDisabled))
                inner()
            }
        }
    )
}

@Composable
fun SettingsScreen() {
    val scope = rememberCoroutineScope()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(end = 16.dp, bottom = 120.dp)) {
        Header("Settings")

        Group("DISCORD") {
            val enabled by Settings.discordEnabled.flow.collectAsState()
            val token by Settings.discordToken.flow.collectAsState()
            val appId by Settings.discordApplicationId.flow.collectAsState()
            val status by DiscordPresence.status.collectAsState()

            Item("Show what I'm listening to", "Shows the song and artist on your Discord profile, like on your phone.") {
                Toggle(enabled) { Settings.discordEnabled.value = it }
            }
            Field(token, { Settings.discordToken.value = it.trim() }, "Discord token", secret = true)
            Hint(
                "The same token you use in the phone app. Keep it private, it works like a password. " +
                    "Using it this way is against Discord's terms, as on the phone."
            )
            Field(appId, { Settings.discordApplicationId.value = it.trim() }, "Application ID (for album art, optional)")
            Hint(
                "Connection status: " + when (status) {
                    DiscordPresence.Status.Off -> "Off"
                    DiscordPresence.Status.Connecting -> "Connecting…"
                    DiscordPresence.Status.Connected -> "Connected"
                    DiscordPresence.Status.Reconnecting -> "Reconnecting…"
                    DiscordPresence.Status.TokenRejected -> "Discord rejected the token. Paste a new one."
                },
                error = status == DiscordPresence.Status.TokenRejected
            )
        }

        Group("LIBRARY") {
            var importResult by remember { mutableStateOf<String?>(null) }
            Item(
                "Import from your phone",
                "In the phone app, go to Settings → Database → Backup and save the file, then send it to this Mac " +
                    "(AirDrop, Google Drive, email…). This adds your favourites, playlists and listening history, " +
                    "so My top 50 matches your phone."
            )
            Row(Modifier.padding(horizontal = 16.dp)) {
                SecondaryTextButton("Choose backup file…", {
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
                })
            }
            importResult?.let { Hint(it) }
        }

        Group("PLAYBACK") {
            var updateResult by remember { mutableStateOf<String?>(null) }
            Item("Update the YouTube downloader", "If songs stop playing, YouTube may have changed something. Updating yt-dlp usually fixes it.")
            Row(Modifier.padding(horizontal = 16.dp)) {
                SecondaryTextButton("Update yt-dlp", {
                    updateResult = "Updating…"
                    scope.launch { updateResult = StreamResolver.update() }
                })
            }
            updateResult?.let { Hint(it) }
        }

        Group("ABOUT") {
            Item("${AppInfo.NAME} for Mac", AppInfo.VERSION)
            Hint("Your library is stored in ${Paths.dataDir.absolutePath}")
            Spacer(Modifier.width(1.dp))
        }
    }
}
