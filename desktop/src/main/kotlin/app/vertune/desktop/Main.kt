package app.vertune.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.vertune.desktop.data.Library
import app.vertune.desktop.data.Settings
import app.vertune.desktop.discord.DiscordPresence
import app.vertune.desktop.player.PlayerController
import app.vertune.desktop.ui.App
import app.vertune.desktop.ui.Navigator
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.PrintStream
import javax.imageio.ImageIO

private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

fun main() {
    // Keep a log file next to the library, to help when something goes wrong.
    // Output still goes to the terminal too, when the app is started from one.
    runCatching {
        val file = Paths.logFile.outputStream()
        System.setOut(PrintStream(Tee(System.out, file), true))
        System.setErr(PrintStream(Tee(System.err, file), true))
    }
    Thread.setDefaultUncaughtExceptionHandler { thread, e ->
        System.err.println("Uncaught exception in ${thread.name}")
        e.printStackTrace()
    }
    println("${AppInfo.NAME} ${AppInfo.VERSION} starting, Java ${System.getProperty("java.version")}, " +
        "${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
    System.setProperty("apple.awt.application.name", AppInfo.NAME)

    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(Paths.dataDir, "image-cache").toOkioPath())
                    .maxSizeBytes(IMAGE_CACHE_BYTES)
                    .build()
            }
            .build()
    }

    val discord = startDiscord()

    application {
        val windowState = rememberWindowState(size = DpSize(1280.dp, 820.dp))
        val nav = remember { Navigator() }
        val logo = remember {
            BitmapPainter(ImageIO.read(Navigator::class.java.getResource("/vertune.png")).toComposeImageBitmap())
        }

        Window(
            onCloseRequest = {
                PlayerController.shutdown()
                discord.release()
                exitApplication()
            },
            title = AppInfo.NAME,
            icon = logo,
            state = windowState,
            onKeyEvent = { event ->
                // Space plays/pauses (text fields get the key first, so typing still works).
                when {
                    event.type != KeyEventType.KeyDown -> false
                    event.key == Key.Spacebar -> PlayerController.togglePlay().let { true }
                    event.isMetaPressed && event.key == Key.DirectionRight -> PlayerController.next().let { true }
                    event.isMetaPressed && event.key == Key.DirectionLeft -> PlayerController.previous().let { true }
                    event.isMetaPressed && event.key == Key.LeftBracket -> nav.back().let { true }
                    else -> false
                }
            }
        ) {
            window.minimumSize = java.awt.Dimension(MIN_WIDTH, MIN_HEIGHT)
            App(nav, logo)
        }
    }
}

/** Keeps the Discord presence in sync with the settings and what's playing. */
private fun startDiscord(): DiscordPresence {
    val presence = DiscordPresence(appScope, albumLookup = { id -> Library.albumTitle(id) })

    appScope.launch {
        combine(
            Settings.discordEnabled.flow,
            Settings.discordToken.flow,
            Settings.discordApplicationId.flow
        ) { enabled, token, appId -> Triple(enabled, token, appId) }
            .collect { (enabled, token, appId) -> presence.configure(if (enabled) token else null, appId) }
    }

    appScope.launch {
        combine(
            PlayerController.current,
            PlayerController.isPlaying,
            PlayerController.isLoading,
            PlayerController.positionMs,
            PlayerController.durationMs
        ) { track, playing, loading, position, duration ->
            if (track == null || !playing || loading) null
            else DiscordPresence.Track(
                id = track.id,
                title = track.title,
                artist = track.artists,
                album = track.album,
                artworkUrl = track.largeThumbnail,
                positionMs = position,
                durationMs = duration,
                isLocal = false
            )
        }.collect { presence.update(it) }
    }
    return presence
}

/** Writes everything to two streams at once (the terminal and the log file). */
private class Tee(private val a: java.io.OutputStream, private val b: java.io.OutputStream) : java.io.OutputStream() {
    override fun write(byte: Int) {
        a.write(byte)
        b.write(byte)
    }

    override fun write(bytes: ByteArray, off: Int, len: Int) {
        a.write(bytes, off, len)
        b.write(bytes, off, len)
    }

    override fun flush() {
        a.flush()
        b.flush()
    }
}

private const val IMAGE_CACHE_BYTES = 256L * 1024 * 1024
private const val MIN_WIDTH = 960
private const val MIN_HEIGHT = 640
