package app.vertune.desktop.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.dp
import app.vertune.desktop.AppInfo
import app.vertune.desktop.Paths
import app.vertune.desktop.data.Library
import app.vertune.desktop.data.Settings
import app.vertune.desktop.discord.DiscordPresence
import app.vertune.desktop.discord.DiscordQrLogin
import app.vertune.desktop.player.PlayerController
import app.vertune.desktop.player.SongCache
import app.vertune.desktop.player.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

/** The phone's settings tabs, shown on the rail while Settings is open (Sync is left out: it's for Piped on the phone). */
enum class SettingsTab(val key: String, val fallback: String, val icon: String) {
    Appearance("appearance", "Appearance", "color_palette"),
    Player("player", "Player", "play"),
    Cache("cache", "Cache", "server"),
    Database("database", "Database", "server"),
    Sync("sync", "Sync", "sync"),
    Other("other", "Other", "shapes"),
    About("about", "About", "information")
}

@Composable
fun SettingsScreen(tab: SettingsTab) {
    when (tab) {
        SettingsTab.Appearance -> AppearanceSettings()
        SettingsTab.Player -> PlayerSettings()
        SettingsTab.Cache -> CacheSettings()
        SettingsTab.Database -> DatabaseSettings()
        SettingsTab.Sync -> SettingsCategoryScreen(str("sync", "Sync")) { DiscordSettingsGroup() }
        SettingsTab.Other -> OtherSettings()
        SettingsTab.About -> AboutSettings()
    }
}

// ---------------------------------------------------------------- Building blocks (phone: SettingsScreen.kt)

@Composable
private fun SettingsCategoryScreen(title: String, description: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        Header(title)
        if (description != null) {
            BasicText(description, style = Type.s.color(palette.textSecondary), modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
        }
        Column(Modifier.widthIn(max = 820.dp)) { content() }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    description: String? = null,
    important: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) = Column(Modifier.fillMaxWidth()) {
    BasicText(title, style = Type.xs.semiBold.color(LocalPalette.current.accent), modifier = Modifier.padding(start = 32.dp))
    if (description != null) SettingsDescription(description, important)
    content()
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun SettingsDescription(text: String, important: Boolean = false) {
    val palette = LocalPalette.current
    BasicText(
        text,
        style = if (important) Type.xxs.semiBold.color(palette.red) else Type.xxs.color(palette.textSecondary),
        modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsEntry(
    title: String,
    onClick: () -> Unit,
    text: String? = null,
    isEnabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val palette = LocalPalette.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(25))
            .clickable(enabled = isEnabled, onClick = onClick)
            .alpha(if (isEnabled) 1f else 0.5f)
            .padding(start = 32.dp, end = 16.dp)
            .padding(vertical = 16.dp)
            .fillMaxWidth()
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Type.xs.semiBold.color(palette.text))
            if (text != null) BasicText(text, style = Type.xs.semiBold.color(palette.textSecondary))
        }
        trailing?.invoke()
    }
}

/** The phone's switch: accent track with a light knob when on, dim when off. */
@Composable
private fun Switch(isChecked: Boolean) {
    val palette = LocalPalette.current
    val transition = updateTransition(isChecked)
    val background by transition.animateColor { if (it) palette.accent else palette.background1 }
    val knob by transition.animateColor { if (it) palette.onAccent else palette.textDisabled }
    val offset by transition.animateDp { if (it) 36.dp else 12.dp }
    Canvas(Modifier.size(width = 48.dp, height = 24.dp)) {
        drawRoundRect(background, cornerRadius = CornerRadius(12.dp.toPx()))
        drawCircle(knob, radius = 8.dp.toPx(), center = center.copy(x = offset.toPx()))
    }
}

@Composable
internal fun SwitchSettingsEntry(title: String, text: String?, isChecked: Boolean, isEnabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) =
    SettingsEntry(title, { onCheckedChange(!isChecked) }, text, isEnabled) { Switch(isChecked) }

@Composable
private fun <T> ValueSelectorSettingsEntry(
    title: String,
    selected: T,
    values: List<T>,
    onSelect: (T) -> Unit,
    text: String? = null,
    isEnabled: Boolean = true,
    valueText: @Composable (T) -> String = { it.toString() },
    trailing: (@Composable () -> Unit)? = null
) {
    var showing by remember { mutableStateOf(false) }
    if (showing) ValueSelectorDialog(title, selected, values, valueText, { showing = false }, onSelect)
    SettingsEntry(title, { showing = true }, text ?: valueText(selected), isEnabled, trailing)
}

@Composable
private inline fun <reified E : Enum<E>> EnumSettingsEntry(
    title: String,
    selected: E,
    noinline onSelect: (E) -> Unit,
    isEnabled: Boolean = true,
    noinline valueText: @Composable (E) -> String = { it.name },
    noinline trailing: (@Composable () -> Unit)? = null
) = ValueSelectorSettingsEntry(title, selected, enumValues<E>().toList(), onSelect, null, isEnabled, valueText, trailing)

@Composable
private fun SliderSettingsEntry(
    title: String,
    text: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onSlideComplete: (Float) -> Unit,
    toDisplay: @Composable (Float) -> String,
    steps: Int = 0,
    isEnabled: Boolean = true
) {
    val palette = LocalPalette.current
    var state by remember(value) { mutableFloatStateOf(value) }
    Column {
        SettingsEntry(title, {}, "$text (${toDisplay(state)})", isEnabled)
        Slider(
            value = state,
            onValueChange = { state = it },
            onValueChangeFinished = { onSlideComplete(state) },
            valueRange = range,
            steps = steps,
            enabled = isEnabled,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.background2,
                activeTickColor = palette.onAccent,
                inactiveTickColor = palette.textDisabled
            ),
            modifier = Modifier.alpha(if (isEnabled) 1f else 0.5f).padding(start = 32.dp, end = 16.dp).fillMaxWidth()
        )
    }
}

@Composable
private fun <T> Setting(value: Settings.Value<T>): T = value.flow.collectAsState().value

private fun reloadAudio() = PlayerController.applyAudioSettings()

// ---------------------------------------------------------------- Appearance

@Composable
private fun AppearanceSettings() = SettingsCategoryScreen(str("appearance", "Appearance")) {
    val colorSource = Setting(Settings.colorSource)
    val colorMode = Setting(Settings.colorMode)
    val darkness = Setting(Settings.darkness)
    val roundness = Setting(Settings.thumbnailRoundness)
    val font = Setting(Settings.fontFamily)
    val fontPadding = Setting(Settings.applyFontPadding)
    val language = Setting(Settings.language)
    val prevCollapsed = Setting(Settings.showPrevButtonCollapsed)
    val layout = Setting(Settings.playerLayout)
    val showLike = Setting(Settings.showLike)
    val seekStyle = Setting(Settings.seekBarStyle)
    val seekQuality = Setting(Settings.wavySeekBarQuality)
    val autoOpen = Setting(Settings.autoOpenPlayer)
    val hideExplicit = Setting(Settings.hideExplicit)
    val palette = LocalPalette.current

    SettingsGroup(str("colors", "Colors")) {
        EnumSettingsEntry(str("color_source", "Accent color source"), colorSource, { Settings.colorSource.value = it }, valueText = {
            when (it) {
                Settings.ColorSource.Default -> str("color_source_default", "Default")
                Settings.ColorSource.Dynamic -> str("color_source_dynamic", "Dynamic")
            }
        })
        EnumSettingsEntry(str("color_mode", "Color mode"), colorMode, { Settings.colorMode.value = it }, valueText = {
            when (it) {
                Settings.ColorMode.System -> str("color_mode_system", "System")
                Settings.ColorMode.Light -> str("color_mode_light", "Light")
                Settings.ColorMode.Dark -> str("color_mode_dark", "Dark")
            }
        })
        if (palette.isDark) EnumSettingsEntry(str("darkness", "Darkness"), darkness, { Settings.darkness.value = it }, valueText = {
            when (it) {
                Settings.Darkness.Normal -> str("darkness_normal", "Normal")
                Settings.Darkness.AMOLED -> str("darkness_amoled", "AMOLED")
                Settings.Darkness.PureBlack -> str("darkness_pureblack", "Pure black")
            }
        })
    }

    SettingsGroup(str("shapes", "Shapes")) {
        EnumSettingsEntry(
            str("thumbnail_roundness", "Thumbnail roundness"), roundness, { Settings.thumbnailRoundness.value = it },
            valueText = {
                when (it) {
                    Settings.ThumbnailRoundness.None -> str("none", "None")
                    Settings.ThumbnailRoundness.Light -> str("thumbnail_roundness_light", "Light")
                    Settings.ThumbnailRoundness.Medium -> str("thumbnail_roundness_medium", "Medium")
                    Settings.ThumbnailRoundness.Heavy -> str("thumbnail_roundness_heavy", "Heavy")
                    Settings.ThumbnailRoundness.Heavier -> str("thumbnail_roundness_heavier", "Heavier")
                    Settings.ThumbnailRoundness.Heaviest -> str("thumbnail_roundness_heaviest", "Heaviest")
                }
            },
            trailing = {
                Spacer(
                    Modifier
                        .border(1.dp, palette.accent, RoundedCornerShape(roundness.dp.dp))
                        .background(palette.background1, RoundedCornerShape(roundness.dp.dp))
                        .size(36.dp)
                )
            }
        )
    }

    SettingsGroup(str("text", "Text")) {
        ValueSelectorSettingsEntry(
            str("language", "Language"), language, listOf("") + Strings.languages, { Settings.language.value = it },
            valueText = { if (it.isBlank()) str("color_source_default", "Default") else Strings.displayName(it) }
        )
        EnumSettingsEntry(str("font", "Font"), font, { Settings.fontFamily.value = it }, valueText = {
            if (it == Settings.FontFamily.System) str("use_system_font", "Use system font") else it.name
        })
        SwitchSettingsEntry(
            str("use_system_font", "Use system font"), str("use_system_font_description", "Use the font applied by the system"),
            font == Settings.FontFamily.System
        ) { Settings.fontFamily.value = if (it) Settings.FontFamily.System else Settings.FontFamily.Poppins }
        SwitchSettingsEntry(
            str("apply_font_padding", "Apply font padding"), str("apply_font_padding_description", "Add spacing around texts"), fontPadding
        ) { Settings.applyFontPadding.value = it }
    }

    SettingsGroup(str("player", "Player")) {
        SwitchSettingsEntry(
            str("previous_button_while_collapsed", "Previous button while collapsed"),
            str("previous_button_while_collapsed_description", "Shows the previous button even when the player is collapsed"),
            prevCollapsed
        ) { Settings.showPrevButtonCollapsed.value = it }
        EnumSettingsEntry(str("player_layout", "Player layout"), layout, { Settings.playerLayout.value = it }, valueText = {
            when (it) {
                Settings.PlayerLayout.Classic -> str("classic_player_layout_name", "Classic")
                Settings.PlayerLayout.New -> str("new_player_layout_name", "Modern (new)")
            }
        })
        if (layout == Settings.PlayerLayout.New) SwitchSettingsEntry(
            str("show_like_button", "Show like button"),
            str("show_like_button_description", "Shows the like button in the player"),
            showLike
        ) { Settings.showLike.value = it }
        EnumSettingsEntry(str("seek_bar_style", "Seek bar style"), seekStyle, { Settings.seekBarStyle.value = it }, valueText = {
            when (it) {
                Settings.SeekBarStyle.Static -> str("static_seek_bar_name", "Static")
                Settings.SeekBarStyle.Wavy -> str("wavy_seek_bar_name", "Wavy")
            }
        })
        if (seekStyle == Settings.SeekBarStyle.Wavy) EnumSettingsEntry(
            str("seek_bar_quality", "Seek bar quality"), seekQuality, { Settings.wavySeekBarQuality.value = it },
            valueText = {
                when (it) {
                    Settings.WavySeekBarQuality.Poor -> str("seek_bar_quality_poor", "Poor")
                    Settings.WavySeekBarQuality.Low -> str("seek_bar_quality_low", "Low")
                    Settings.WavySeekBarQuality.Medium -> str("seek_bar_quality_medium", "Medium")
                    Settings.WavySeekBarQuality.High -> str("seek_bar_quality_high", "High")
                    Settings.WavySeekBarQuality.Great -> str("seek_bar_quality_great", "Great")
                    Settings.WavySeekBarQuality.Subpixel -> str("seek_bar_quality_subpixel", "Subpixel")
                }
            }
        )
        SwitchSettingsEntry(
            str("auto_open_player", "Auto open player"),
            str("auto_open_player_description", "Opens the player when you start playing a song"),
            autoOpen
        ) { Settings.autoOpenPlayer.value = it }
    }

    SettingsGroup(str("songs", "Songs")) {
        SwitchSettingsEntry(
            str("hide_explicit", "Hide explicit"), str("hide_explicit_description", "Hides songs marked as explicit"), hideExplicit
        ) { Settings.hideExplicit.value = it }
    }
}

// ---------------------------------------------------------------- Player

@Composable
private fun PlayerSettings() = SettingsCategoryScreen(str("player", "Player")) {
    val persistentQueue = Setting(Settings.persistentQueue)
    val skipOnError = Setting(Settings.skipOnError)
    val skipSilence = Setting(Settings.skipSilence)
    val minimumSilence = Setting(Settings.minimumSilenceMs)
    val loudness = Setting(Settings.loudnessNormalization)
    val baseGain = Setting(Settings.loudnessBaseGain)
    val bassBoost = Setting(Settings.bassBoost)
    val bassAmount = Setting(Settings.bassBoostAmount)
    val sponsorBlock = Setting(Settings.sponsorBlock)
    val reverb = Setting(Settings.reverb)

    SettingsGroup(str("player", "Player")) {
        SwitchSettingsEntry(
            str("persistent_queue", "Persistent queue"),
            str("persistent_queue_description", "Save and restore playing songs"),
            persistentQueue
        ) { Settings.persistentQueue.value = it }
        SwitchSettingsEntry(
            str("skip_on_error", "Skip on error"),
            str("skip_on_error_description", "Skip to the next song when an error occurs"),
            skipOnError
        ) { Settings.skipOnError.value = it }
    }

    SettingsGroup(str("audio", "Audio")) {
        SwitchSettingsEntry(str("skip_silence", "Skip silence"), str("skip_silence_description", "Skip silent parts during playback"), skipSilence) {
            Settings.skipSilence.value = it
            reloadAudio()
        }
        if (skipSilence) SliderSettingsEntry(
            str("minimum_silence_length", "Minimum silence length"),
            str("minimum_silence_length_description", "The minimum time the audio has to be silent to get skipped"),
            minimumSilence.toFloat().coerceIn(1f, 2000f), 1f..2000f,
            onSlideComplete = { Settings.minimumSilenceMs.value = it.toLong(); reloadAudio() },
            toDisplay = { str("format_ms", "%1\$s ms", it.toLong()) }
        )
        SwitchSettingsEntry(
            str("loudness_normalization", "Loudness normalization"),
            str("loudness_normalization_description", "Adjust the volume to a fixed level"),
            loudness
        ) {
            Settings.loudnessNormalization.value = it
            reloadAudio()
        }
        if (loudness) SliderSettingsEntry(
            str("loudness_base_gain", "Base gain"),
            str("loudness_base_gain_description", "The 'target' gain for the loudness normalization"),
            baseGain, -20f..20f,
            onSlideComplete = { Settings.loudnessBaseGain.value = it; reloadAudio() },
            toDisplay = { str("format_db", "%1\$s dB", "%.1f".format(it)) }
        )
        SwitchSettingsEntry(
            str("bass_boost", "Bass boost"),
            str("bass_boost_description", "Boost low frequencies to improve listening experience"),
            bassBoost
        ) {
            Settings.bassBoost.value = it
            reloadAudio()
        }
        if (bassBoost) SliderSettingsEntry(
            str("bass_boost_level", "Bass boost level"),
            str("bass_boost_level_description", "Level (0–1000) of boosting low frequencies; use at own risk!"),
            bassAmount * 1000f, 0f..1000f,
            onSlideComplete = { Settings.bassBoostAmount.value = it / 1000f; reloadAudio() },
            toDisplay = { it.roundToInt().toString() }
        )
        SwitchSettingsEntry(
            str("sponsor_block", "Sponsorblock"),
            str("sponsor_block_description", "Blocks sponsored/offtopic content using data from the Sponsorblock API"),
            sponsorBlock
        ) { Settings.sponsorBlock.value = it }
        EnumSettingsEntry(str("reverb", "Reverb"), reverb, { Settings.reverb.value = it; reloadAudio() }, valueText = {
            when (it) {
                Settings.Reverb.None -> str("none", "None")
                Settings.Reverb.SmallRoom -> str("reverb_small_room", "Small room")
                Settings.Reverb.MediumRoom -> str("reverb_medium_room", "Medium room")
                Settings.Reverb.LargeRoom -> str("reverb_large_room", "Large room")
                Settings.Reverb.MediumHall -> str("reverb_medium_hall", "Medium hall")
                Settings.Reverb.LargeHall -> str("reverb_large_hall", "Large hall")
                Settings.Reverb.Plate -> str("reverb_plate", "Plate")
            }
        })
    }

    var importResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    SettingsGroup(
        "Phone",
        "In the phone app, go to Settings → Database → Backup and save the file, then send it to this ${Paths.deviceName} " +
            "(${if (Paths.isWindows) "Nearby Share, Google Drive" else "AirDrop, Google Drive"}, email…). This adds your favourites, playlists, lyrics and listening history."
    ) {
        SettingsEntry("Import from your phone", {
            val file = chooseFile("Choose the phone app backup", FileDialog.LOAD) ?: return@SettingsEntry
            importResult = "Importing…"
            scope.launch {
                importResult = withContext(Dispatchers.IO) {
                    runCatching { Library.importPhoneBackup(file) }.fold(
                        { (songs, listens) -> "Imported $songs songs and $listens listens." },
                        { "That file couldn't be imported: ${it.message}" }
                    )
                }
            }
        }, importResult ?: "Choose the backup file you made on the phone")
    }
}

@Composable
private fun DiscordSettingsGroup() = SettingsGroup(
    str("discord", "Discord"),
    str("discord_presence_warning", "This logs in with your Discord account token, which is against Discord's Terms of Service."),
    important = true
) {
    val enabled = Setting(Settings.discordEnabled)
    val token = Setting(Settings.discordToken)
    val appId = Setting(Settings.discordApplicationId)
    val status by DiscordPresence.status.collectAsState()
    var editingToken by remember { mutableStateOf(false) }
    var editingAppId by remember { mutableStateOf(false) }

    var loggingIn by remember { mutableStateOf(false) }
    if (loggingIn) DiscordLoginDialog { loggingIn = false }

    if (editingToken) TextFieldDialog("", str("discord_token", "Discord token"), token, { editingToken = false }) {
        Settings.discordToken.value = it.trim()
    }
    if (editingAppId) TextFieldDialog("", str("discord_application_id", "Application ID"), appId, { editingAppId = false }) {
        Settings.discordApplicationId.value = it.trim()
    }

    SwitchSettingsEntry(
        str("discord_presence", "Discord Rich Presence"),
        str("discord_presence_description", "Show the song you're listening to on your Discord profile"),
        enabled
    ) { Settings.discordEnabled.value = it }
    if (enabled) SettingsEntry(
        str("discord_status", "Connection status"), {},
        when (status) {
            DiscordPresence.Status.Off -> str("discord_status_off", "Off")
            DiscordPresence.Status.Connecting -> str("discord_status_connecting", "Connecting…")
            DiscordPresence.Status.Connected -> str("discord_status_connected", "Connected")
            DiscordPresence.Status.Reconnecting -> str("discord_status_reconnecting", "Reconnecting…")
            DiscordPresence.Status.TokenRejected -> str("discord_status_token_rejected", "Discord rejected the token")
        }
    )
    SettingsEntry(
        str("discord_login", "Log in with Discord"), { loggingIn = true },
        "Scan a code with the Discord app on your phone and the token is filled in for you"
    )
    SettingsEntry(
        str("discord_token", "Discord token"), { editingToken = true },
        if (token.isBlank()) str("discord_token_not_set", "Not set") else str("discord_token_set", "Set")
    )
    SettingsEntry(
        str("discord_application_id", "Application ID"), { editingAppId = true },
        appId.ifBlank { str("discord_application_id_description", "Optional, needed to show album art") }
    )
}

/**
 * Discord's QR code login: scan the code with the Discord app on your phone, confirm, and the
 * token is saved and Rich Presence switched on.
 */
@Composable
private fun DiscordLoginDialog(onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    var step by remember { mutableStateOf<DiscordQrLogin.Step>(DiscordQrLogin.Step.Connecting) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        DiscordQrLogin.run { s ->
            step = s
            if (s is DiscordQrLogin.Step.Done) {
                Settings.discordToken.value = s.token
                Settings.discordEnabled.value = true
            }
        }
    }
    DialogFrame(onDismiss) {
        BasicText(
            str("discord_login", "Log in with Discord"),
            style = Type.s.semiBold.color(palette.text).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
            when (val s = step) {
                DiscordQrLogin.Step.Connecting -> Loading()
                is DiscordQrLogin.Step.ShowCode -> QrCode(s.url)
                is DiscordQrLogin.Step.Scanned -> AppIcon("information", palette.accent, size = 64.dp)
                is DiscordQrLogin.Step.Done -> AppIcon("heart", palette.accent, size = 64.dp)
                is DiscordQrLogin.Step.Failed -> AppIcon("alert_circle", palette.red, size = 64.dp)
            }
        }
        BasicText(
            when (val s = step) {
                DiscordQrLogin.Step.Connecting -> "Getting a code from Discord…"
                is DiscordQrLogin.Step.ShowCode ->
                    "Open Discord on your phone, go to Settings → Scan QR Code, and point the camera at this code."
                is DiscordQrLogin.Step.Scanned ->
                    "Hi ${s.username}! Tap Log in on your phone to finish."
                is DiscordQrLogin.Step.Done -> str("discord_login_success", "Logged in to Discord")
                is DiscordQrLogin.Step.Failed -> s.reason
            },
            style = Type.xs.medium.color(palette.textSecondary).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            when (step) {
                is DiscordQrLogin.Step.Done -> DialogTextButton(str("done", "Done"), onDismiss, primary = true)
                is DiscordQrLogin.Step.Failed -> {
                    DialogTextButton(str("cancel", "Cancel"), onDismiss)
                    DialogTextButton("Try again", { step = DiscordQrLogin.Step.Connecting; attempt++ }, primary = true)
                }
                else -> DialogTextButton(str("cancel", "Cancel"), onDismiss)
            }
        }
    }
}

/** Draws [text] as a QR code: dark squares on a white card, so phone cameras read it easily. */
@Composable
private fun QrCode(text: String) {
    val matrix = remember(text) {
        com.google.zxing.qrcode.QRCodeWriter().encode(
            text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0,
            mapOf(com.google.zxing.EncodeHintType.MARGIN to 2)
        )
    }
    Canvas(Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color.White)) {
        val cell = size.width / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix.get(x, y)) drawRect(
                androidx.compose.ui.graphics.Color.Black,
                topLeft = androidx.compose.ui.geometry.Offset(x * cell, y * cell),
                size = androidx.compose.ui.geometry.Size(cell + 0.5f, cell + 0.5f)
            )
        }
    }
}

// ---------------------------------------------------------------- Cache

private val IMAGE_CACHE_CHOICES_MB = listOf(64L, 128L, 256L, 512L, 1024L, 2048L)

private fun sizeLabel(mb: Long) = when {
    mb == 0L -> null
    mb >= 1024 -> "${mb / 1024}GB"
    else -> "${mb}MB"
}

private fun folderSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

@Composable
private fun CacheSettings() = SettingsCategoryScreen(
    str("cache", "Cache"),
    str("cache_description", "When the cache runs out of space, the resources that haven't been accessed for the longest time are cleared")
) {
    val scope = rememberCoroutineScope()
    val imageLimit = Setting(Settings.imageCacheMb)
    var imageUsed by remember { mutableLongStateOf(0L) }
    var imageVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(imageVersion) { imageUsed = withContext(Dispatchers.IO) { folderSize(Paths.imageCacheDir) } }

    SettingsGroup(
        str("image_cache", "Image cache"),
        str(
            "format_cache_space_used_percentage", "%1\$s used (%2\$s%%)",
            formatBytes(imageUsed), (imageUsed * 100 / (imageLimit * 1024 * 1024).coerceAtLeast(1)).toString()
        )
    ) {
        SettingsEntry(str("clear", "Clear"), {
            scope.launch(Dispatchers.IO) {
                runCatching { Paths.imageCacheDir.deleteRecursively() }
                imageVersion++
            }
        })
        ValueSelectorSettingsEntry(
            str("max_size", "Max size"), imageLimit, IMAGE_CACHE_CHOICES_MB, { Settings.imageCacheMb.value = it },
            valueText = { sizeLabel(it).orEmpty() }
        )
        SettingsDescription("A new image cache size is used the next time you open the app.")
    }

    val songLimit = Setting(Settings.songCacheMb)
    val paused = Setting(Settings.pauseSongCache)
    val used by SongCache.bytes.collectAsState()
    val songs by SongCache.ids.collectAsState()
    SettingsGroup(
        str("song_cache", "Song cache"),
        if (songLimit == 0L) str("format_cache_space_used", "%1\$s used", formatBytes(used))
        else str(
            "format_cache_space_used_percentage", "%1\$s used (%2\$s%%)",
            formatBytes(used), (used * 100 / (songLimit * 1024 * 1024)).toString()
        )
    ) {
        ValueSelectorSettingsEntry(
            str("max_size", "Max size"), songLimit, SongCache.sizeChoicesMb,
            {
                Settings.songCacheMb.value = it
                scope.launch(Dispatchers.IO) { SongCache.evict() }
            },
            valueText = { sizeLabel(it) ?: "Unlimited" }
        )
        SwitchSettingsEntry(
            str("pause_song_cache", "Pause cache"),
            str("pause_song_cache_description", "Temporarily pauses new songs being added to cache"),
            paused
        ) { Settings.pauseSongCache.value = it }
        SettingsEntry(
            str("clear", "Clear"), { scope.launch(Dispatchers.IO) { SongCache.clear() } },
            plural("song_count_plural", songs.size, "${songs.size} songs"),
            isEnabled = songs.isNotEmpty()
        )
        SettingsDescription("Songs are kept in ${SongCache.dir.absolutePath}")
    }
}

// ---------------------------------------------------------------- Database

@Composable
private fun DatabaseSettings() = SettingsCategoryScreen(str("database", "Database")) {
    val scope = rememberCoroutineScope()
    val version by Library.version.collectAsState()
    val pauseHistory = Setting(Settings.pausePlaybackHistory)
    val pauseTime = Setting(Settings.pausePlaytime)
    val topLength = Setting(Settings.topListLength)
    val events by androidx.compose.runtime.produceState(0, version) {
        value = withContext(Dispatchers.IO) { runCatching { Library.eventCount() }.getOrDefault(0) }
    }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf<File?>(null) }

    SettingsGroup(str("cleanup", "Cleanup")) {
        SwitchSettingsEntry(
            str("pause_playback_history", "Pause playback history"),
            str("pause_playback_history_description", "Stops playback events being saved"),
            pauseHistory
        ) { Settings.pausePlaybackHistory.value = it }
        if (pauseHistory) SettingsDescription(str("pause_playback_history_warning", "Please note: this won't affect offline caching!"), important = true)
        SettingsEntry(
            str("reset_quick_picks", "Reset quick picks"),
            { scope.launch(Dispatchers.IO) { Library.clearEvents() } },
            if (events > 0) plural("format_reset_quick_picks_amount", events, "Delete $events playback events")
            else str("quick_picks_empty", "Quick picks are cleared"),
            isEnabled = events > 0
        )
        SwitchSettingsEntry(
            str("pause_playback_time", "Pause playback time"),
            str("format_pause_playback_time_description", "Stops playback time from being saved.", topLength),
            pauseTime
        ) { Settings.pausePlaytime.value = it }
    }

    SettingsGroup(str("backup", "Backup"), str("backup_description", "Personal preferences and the cache are excluded.")) {
        SettingsEntry(str("backup", "Backup"), {
            val name = "vertune_${LocalDate.now()}.db"
            val file = chooseFile(Strings.get("backup") ?: "Backup", FileDialog.SAVE, name) ?: return@SettingsEntry
            scope.launch(Dispatchers.IO) {
                message = runCatching { Library.backup(file); "Saved ${file.name}" }.getOrElse { "Backup failed: ${it.message}" }
            }
        }, str("backup_action_description", "Export the database to the external storage"))
    }

    SettingsGroup(str("restore", "Restore"), str("restore_warning", "Existing data will be overwritten."), important = true) {
        SettingsEntry(str("restore", "Restore"), {
            confirmRestore = chooseFile(Strings.get("restore") ?: "Restore", FileDialog.LOAD)
        }, str("restore_description", "Import the database from the external storage"))
    }

    message?.let { SettingsDescription(it) }

    confirmRestore?.let { file ->
        ConfirmDialog(str("restore_warning", "Existing data will be overwritten."), { confirmRestore = null }) {
            scope.launch(Dispatchers.IO) {
                message = runCatching { Library.restore(file) }.getOrElse { "Restore failed: ${it.message}" }
            }
        }
    }
}

// ---------------------------------------------------------------- Other

@Composable
private fun OtherSettings() = SettingsCategoryScreen(str("other", "Other")) {
    val scope = rememberCoroutineScope()
    val version by Library.version.collectAsState()
    val pauseSearch = Setting(Settings.pauseSearchHistory)
    val topLength = Setting(Settings.topListLength)
    val source = Setting(Settings.quickPicksSource)
    val qpCache = Setting(Settings.quickPicksCache)
    val maxThumb = Setting(Settings.maxThumbnailSize)
    var searchVersion by remember { mutableIntStateOf(0) }
    val queries by androidx.compose.runtime.produceState(0, version, searchVersion) {
        value = withContext(Dispatchers.IO) { runCatching { Library.searchHistoryCount() }.getOrDefault(0) }
    }
    var editingTop by remember { mutableStateOf(false) }
    var ytDlpResult by remember { mutableStateOf<String?>(null) }

    SettingsGroup(str("search_history", "Search history")) {
        SwitchSettingsEntry(
            str("pause_search_history", "Pause search history"),
            str("pause_search_history_description", "Neither save new search queries nor show history"),
            pauseSearch
        ) { Settings.pauseSearchHistory.value = it }
        SettingsEntry(
            str("clear_search_history", "Clear search history"),
            { scope.launch(Dispatchers.IO) { Library.clearSearchHistory(); searchVersion++ } },
            if (queries > 0) str("format_clear_search_history_amount", "Delete %1\$s search queries", queries)
            else str("empty_history", "History is empty"),
            isEnabled = queries > 0
        )
    }

    SettingsGroup(str("built_in_playlists", "Built-in playlists")) {
        if (editingTop) TextFieldDialog("", str("top_list_length", "Top list length"), topLength.toString(), { editingTop = false }) {
            it.toIntOrNull()?.coerceIn(1, 500)?.let { n -> Settings.topListLength.value = n }
        }
        SettingsEntry(
            str("top_list_length", "Top list length"), { editingTop = true },
            str("top_list_length_description", "Limits the length of the 'My top x' playlist") + " ($topLength)"
        )
    }

    SettingsGroup(str("quick_picks", "Quick picks")) {
        EnumSettingsEntry(str("quick_picks_source", "Source"), source, { Settings.quickPicksSource.value = it }, valueText = {
            when (it) {
                Settings.QuickPicksSource.Trending -> str("trending", "Trending")
                Settings.QuickPicksSource.LastInteraction -> str("last_interaction", "Last interaction")
            }
        })
        SwitchSettingsEntry(
            str("quick_picks_cache", "Cache quick picks"),
            str("quick_picks_cache_description", "Saves the quick picks data to disk for offline access"),
            qpCache
        ) {
            Settings.quickPicksCache.value = it
            if (!it) runCatching { Paths.quickPicksFile.delete() }
        }
    }

    SettingsGroup(str("dynamic_thumbnails", "Dynamic thumbnails")) {
        SliderSettingsEntry(
            str("max_dynamic_thumbnail_size", "Max dynamic thumbnail size"),
            str("max_dynamic_thumbnail_size_description", "The maximum size of a thumbnail when a dynamic thumbnail is used"),
            maxThumb.toFloat(), 32f..1920f,
            onSlideComplete = { Settings.maxThumbnailSize.value = it.roundToInt() },
            toDisplay = { str("format_px", "%1\$spx", it.roundToInt()) }
        )
    }

    SettingsGroup(str("troubleshooting", "Troubleshooting"), str("troubleshooting_warning", "Use these as a last resort when audio playback fails"), important = true) {
        SettingsEntry(str("upgrade_yt_dlp", "Upgrade yt-dlp"), {
            ytDlpResult = Strings.get("please_wait") ?: "Please wait…"
            scope.launch { ytDlpResult = StreamResolver.update() }
        }, ytDlpResult)
        SettingsEntry(str("show_logs", "View debug logs"), {
            runCatching { Desktop.getDesktop().open(Paths.logFile) }
        }, Paths.logFile.absolutePath)
    }
}

// ---------------------------------------------------------------- About

@Composable
private fun AboutSettings() = SettingsCategoryScreen(
    str("about", "About"),
    "v${AppInfo.VERSION} for ${if (Paths.isWindows) "Windows" else "Mac"}, based on ViTune by Huizengek & vfsfitvnm"
) {
    SettingsGroup(str("social", "Social")) {
        SettingsEntry(str("github", "GitHub"), { openUrl(REPO_URL) }, str("view_source", "View the source code"))
    }
    SettingsGroup(str("contact", "Contact")) {
        SettingsEntry(str("report_bug", "Report a bug"), { openUrl("$REPO_URL/issues/new") }, str("redirect_github", "You will be redirected to GitHub"))
        SettingsEntry(str("request_feature", "Request a feature or suggest an idea"), { openUrl("$REPO_URL/issues/new") }, str("redirect_github", "You will be redirected to GitHub"))
    }
    SettingsGroup(str("version", "Version")) {
        SettingsEntry(str("version", "Version"), {}, str("current_version", "You're currently running version v%1\$s", AppInfo.VERSION))
        SettingsEntry("Library", { runCatching { Desktop.getDesktop().open(Paths.dataDir) } }, Paths.dataDir.absolutePath)
    }
}

private const val REPO_URL = "https://github.com/Sheu112/vertuned"

private fun chooseFile(title: String, mode: Int, suggested: String? = null): File? {
    val dialog = FileDialog(null as Frame?, title, mode)
    if (suggested != null) dialog.file = suggested
    dialog.isVisible = true
    return dialog.file?.let { File(dialog.directory, it) }
}
