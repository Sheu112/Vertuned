package app.vertune.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.vertune.desktop.ui.LocalPalette
import java.awt.Component
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT

/**
 * Windows' version of the Mac's see-through title bar: asks Windows (through dwmapi.dll, with
 * Java's foreign function API, no extra libraries) to draw the window's title bar dark, in the
 * app's own background colour, instead of the default white bar.
 * Windows 10 gets the dark bar; Windows 11 also gets the exact colour. Everything fails quietly.
 */
object WinWindows {
    private class Native {
        val linker: Linker = Linker.nativeLinker()
        val dwm: SymbolLookup = SymbolLookup.libraryLookup("dwmapi", Arena.global())

        // HRESULT DwmSetWindowAttribute(HWND, DWORD attribute, LPCVOID value, DWORD size)
        val setAttribute = linker.downcallHandle(
            dwm.find("DwmSetWindowAttribute").get(),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT)
        )

        fun set(hwnd: Long, attribute: Int, value: Int) = Arena.ofConfined().use { arena ->
            val v = arena.allocate(JAVA_INT).apply { set(JAVA_INT, 0, value) }
            setAttribute.invokeWithArguments(MemorySegment.ofAddress(hwnd), attribute, v, 4) as Int
        }
    }

    private val native: Native? by lazy {
        if (Paths.isWindows) runCatching { Native() }.onFailure { println("WinWindows unavailable: $it") }.getOrNull() else null
    }

    /** The Windows handle (HWND) behind a Java window. */
    private fun hwnd(window: java.awt.Window): Long? = runCatching {
        // Compose windows expose it directly.
        runCatching { window.javaClass.getMethod("getWindowHandle").invoke(window) as Long }.getOrNull()?.takeIf { it != 0L }
            ?: run {
                // Plain AWT windows: ask the window's peer (needs the --add-opens flags in build.gradle.kts).
                val accessor = Class.forName("sun.awt.AWTAccessor").getMethod("getComponentAccessor").invoke(null)
                val peer = Class.forName("sun.awt.AWTAccessor\$ComponentAccessor")
                    .getMethod("getPeer", Component::class.java).invoke(accessor, window) ?: return null
                (peer.javaClass.getMethod("getHWnd").invoke(peer) as Long).takeIf { it != 0L }
            }
    }.onFailure { println("WinWindows: can't reach the window: $it") }.getOrNull()

    fun styleTitleBar(window: java.awt.Window, dark: Boolean, background: Color, text: Color) {
        val n = native ?: return
        val hwnd = hwnd(window) ?: return
        runCatching {
            // Windows 10 20H1+ uses 20; earlier Windows 10 builds used 19.
            if (n.set(hwnd, DARK_MODE, if (dark) 1 else 0) != 0) n.set(hwnd, DARK_MODE_OLD, if (dark) 1 else 0)
            n.set(hwnd, CAPTION_COLOR, background.colorRef())
            n.set(hwnd, TEXT_COLOR, text.colorRef())
        }.onFailure { println("WinWindows: $it") }
    }

    /** Windows wants colours as 0x00BBGGRR. */
    private fun Color.colorRef(): Int {
        val argb = toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (b shl 16) or (g shl 8) or r
    }

    private const val DARK_MODE_OLD = 19
    private const val DARK_MODE = 20
    private const val CAPTION_COLOR = 35
    private const val TEXT_COLOR = 36
}

/** Keeps a window's Windows title bar in the app's colours (does nothing on a Mac). */
@Composable
fun WindowsTitleBar(window: java.awt.Window) {
    if (!Paths.isWindows) return
    val palette = LocalPalette.current
    LaunchedEffect(window, palette.isDark, palette.background0, palette.text) {
        WinWindows.styleTitleBar(window, palette.isDark, palette.background0, palette.text)
    }
}
