package app.vertune.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import app.vertune.desktop.AppInfo
import app.vertune.desktop.MacWindows
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import app.vertune.desktop.player.PlayerController

/**
 * A small always-on-top window with the cover, title and artist, like a picture-in-picture
 * player. Moving the pointer over it shows the controls. Opened from the player bar or the
 * player's menu; closing it (or the button in its corner) goes back to the main window.
 */
@Composable
fun MiniPlayerWindow(nav: Navigator, icon: Painter, onOpenMain: () -> Unit) {
    if (!nav.showMiniPlayer) return
    val state = rememberWindowState(
        size = DpSize(320.dp, 400.dp),
        position = WindowPosition(Alignment.BottomEnd)
    )
    Window(
        onCloseRequest = { nav.showMiniPlayer = false },
        title = AppInfo.NAME,
        icon = icon,
        state = state,
        alwaysOnTop = true
    ) {
        remember { window.blendTitleBar(); window.minimumSize = java.awt.Dimension(220, 280) }
        // Float over every Space and full-screen apps. macOS only allows that for apps without a
        // Dock icon, so the icon is hidden while the mini player is open (the main window is too).
        // On Windows alwaysOnTop is enough: it stays over other windows and borderless full-screen
        // apps, and hiding the main window takes it off the taskbar, leaving just the mini player.
        // (Both calls do nothing outside macOS.)
        DisposableEffect(Unit) {
            MacWindows.setDockIconVisible(false)
            onDispose { MacWindows.setDockIconVisible(true) }
        }
        LaunchedEffect(Unit) {
            delay(FLOAT_DELAY_MS)
            MacWindows.floatEverywhere(window)
        }
        VertuneTheme {
            val palette = LocalPalette.current.forPlayer()
            CompositionLocalProvider(LocalPalette provides palette) {
                app.vertune.desktop.WindowsTitleBar(window)
                MiniPlayer(onClose = { nav.showMiniPlayer = false }, onOpenMain = onOpenMain)
            }
        }
    }
}

@Composable
private fun MiniPlayer(onClose: () -> Unit, onOpenMain: () -> Unit) {
    val palette = LocalPalette.current
    val track by PlayerController.current.collectAsState()
    val playing by PlayerController.isPlaying.collectAsState()
    val loading by PlayerController.isLoading.collectAsState()
    val position by PlayerController.positionMs.collectAsState()
    val duration by PlayerController.durationMs.collectAsState()
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background0)
            .hoverable(hover)
            .padding(start = 12.dp, end = 12.dp, top = TITLE_BAR_HEIGHT.coerceAtLeast(12.dp), bottom = 12.dp)
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            val t = track
            Box(Modifier.size(side).clip(RoundedCornerShape(12.dp)).background(palette.background1)) {
                if (t != null) Thumbnail(t.thumbnailSized(1200), side, RoundedCornerShape(12.dp), fallback = t.thumbnailFallback())
                else AppIcon("musical_notes", palette.textDisabled, Modifier.align(Alignment.Center), size = 48.dp)

                val overlay by animateFloatAsState(if (hovered) 1f else 0f)
                if (overlay > 0f) {
                    Box(Modifier.fillMaxSize().alpha(overlay).background(Color.Black.copy(alpha = 0.45f))) {
                        AppIconButton("expand", Color.White, { onClose(); onOpenMain() }, Modifier.align(Alignment.TopEnd), size = 18.dp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            AppIconButton("play_skip_back", Color.White, PlayerController::previous, size = 26.dp)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(palette.accent)
                                    .clickable(onClick = PlayerController::togglePlay)
                            ) {
                                if (loading) CircularProgressIndicator(color = palette.onAccent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                                else AppIcon(if (playing) "pause" else "play", palette.onAccent, size = 26.dp)
                            }
                            AppIconButton("play_skip_forward", Color.White, PlayerController::next, size = 26.dp)
                        }
                    }
                }
            }
        }
        val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
        Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(palette.background2)) {
            Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(palette.text))
        }
        BasicText(
            track?.title ?: AppInfo.NAME,
            style = Type.l.semiBold.color(palette.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp)
        )
        BasicText(
            track?.artists.orEmpty(),
            style = Type.s.semiBold.color(palette.textSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Makes the macOS title bar see-through, so the app's own dark background runs up behind the
 * window buttons like other Mac apps (instead of a bright white bar).
 */
fun java.awt.Window.blendTitleBar() {
    val root = (this as? javax.swing.RootPaneContainer)?.rootPane ?: return
    root.putClientProperty("apple.awt.fullWindowContent", true)
    root.putClientProperty("apple.awt.transparentTitleBar", true)
    root.putClientProperty("apple.awt.windowTitleVisible", false)
}

/** Gives macOS a moment to create the window before changing how it floats. */
private const val FLOAT_DELAY_MS = 300L

/** Height of the see-through macOS title bar, kept clear of controls (Windows has a normal title bar). */
val TITLE_BAR_HEIGHT = if (app.vertune.desktop.Paths.isMac) 28.dp else 0.dp
