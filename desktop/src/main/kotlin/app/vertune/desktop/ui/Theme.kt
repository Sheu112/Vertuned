package app.vertune.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.material3.lightColorScheme
import app.vertune.desktop.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import app.vertune.desktop.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import javax.imageio.ImageIO

/**
 * The phone app's look: its colour palettes (core/ui ColorPalette.kt), with the accent taken
 * from the album art (Dynamic) or the default blue, light or dark mode, three darkness levels,
 * and its fonts and text sizes (Typography.kt). Everything follows Settings → Appearance.
 */
@Immutable
data class Palette(
    val background0: Color,
    val background1: Color,
    val background2: Color,
    val accent: Color,
    val onAccent: Color = Color.White,
    val text: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val red: Color = Color(0xffbf4040),
    val blue: Color = Color(0xff4472cf),
    val yellow: Color = Color(0xfffff176),
    val isDefault: Boolean = false,
    val isDark: Boolean = true,
    val amoled: Boolean = false
) {
    companion object {
        /** Same as the phone's default accent. */
        val DefaultAccent = Color(0xff3e44ce)

        private val defaultAccentHsl = DefaultAccent.hsl()

        val DefaultDark = Palette(
            background0 = Color(0xff16171d),
            background1 = Color(0xff1f2029),
            background2 = Color(0xff2b2d3b),
            text = Color(0xffe1e1e2),
            textSecondary = Color(0xffa3a4a6),
            textDisabled = Color(0xff6f6f73),
            accent = DefaultAccent,
            isDefault = true,
            isDark = true
        )

        fun light(hue: Float, saturation: Float) = Palette(
            background0 = Color.hsl(hue, saturation.coerceAtMost(0.1f), 0.925f),
            background1 = Color.hsl(hue, saturation.coerceAtMost(0.3f), 0.90f),
            background2 = Color.hsl(hue, saturation.coerceAtMost(0.4f), 0.85f),
            text = Color.hsl(hue, saturation.coerceAtMost(0.02f), 0.12f),
            textSecondary = Color.hsl(hue, saturation.coerceAtMost(0.1f), 0.40f),
            textDisabled = Color.hsl(hue, saturation.coerceAtMost(0.2f), 0.65f),
            accent = Color.hsl(hue, saturation.coerceAtMost(0.5f), 0.5f),
            isDark = false
        )

        fun dark(hue: Float, saturation: Float, darkness: Settings.Darkness = Settings.Darkness.Normal) = Palette(
            background0 = if (darkness == Settings.Darkness.Normal) Color.hsl(hue, saturation.coerceAtMost(0.1f), 0.10f) else Color.Black,
            background1 = if (darkness == Settings.Darkness.Normal) Color.hsl(hue, saturation.coerceAtMost(0.3f), 0.15f) else Color.Black,
            background2 = if (darkness == Settings.Darkness.Normal) Color.hsl(hue, saturation.coerceAtMost(0.4f), 0.2f) else Color.Black,
            text = Color.hsl(hue, saturation.coerceAtMost(0.02f), 0.88f),
            textSecondary = Color.hsl(hue, saturation.coerceAtMost(0.1f), 0.65f),
            textDisabled = Color.hsl(hue, saturation.coerceAtMost(0.2f), 0.40f),
            accent = Color.hsl(hue, saturation.coerceAtMost(if (darkness == Settings.Darkness.AMOLED) 0.4f else 0.5f), 0.5f),
            isDark = true,
            amoled = darkness == Settings.Darkness.AMOLED
        )

        /** Like the phone's colorPaletteOf. */
        fun of(accent: Pair<Float, Float>?, darkness: Settings.Darkness, isDark: Boolean): Palette {
            val (h, s) = accent ?: defaultAccentHsl
            val palette = if (isDark) dark(h, s, darkness) else light(h, s)
            return palette.copy(isDefault = accent == null)
        }

        val Default: Palette = of(null, Settings.Darkness.AMOLED, true)
    }
}

val Palette.isPureBlack get() = background0 == Color.Black

/** The phone's "surface" colour: grey buttons and chips on pure black, background2 otherwise. */
val Palette.surface get() = if (isPureBlack) Color(0xff272727) else background2

/**
 * With AMOLED darkness the phone keeps the pages black but gives the player tinted colours
 * (ColorPalette.amoled() in the phone app). Used for the player bar, player and queue.
 */
fun Palette.forPlayer(): Palette {
    if (!isDark || !amoled) return this
    val (hue, saturation) = accent.hsl()
    return copy(
        background0 = Color.hsl(hue, saturation.coerceAtMost(0.1f), 0.10f),
        background1 = Color.hsl(hue, saturation.coerceAtMost(0.3f), 0.15f),
        background2 = Color.hsl(hue, saturation.coerceAtMost(0.4f), 0.2f)
    )
}

val LocalPalette = staticCompositionLocalOf { Palette.Default }

/** Font, roundness and font padding from Settings → Appearance, read while drawing. */
object Look {
    var fontFamily by mutableStateOf(fontFamilyOf(Settings.fontFamily.value))
    var fontPadding by mutableStateOf(Settings.applyFontPadding.value)
    var roundness by mutableStateOf(Settings.thumbnailRoundness.value.dp)
}

private fun bundledFont(file: String, weight: Int, variable: Boolean) = Font(
    identity = if (variable) "$file-$weight" else file,
    data = Palette::class.java.getResourceAsStream("/fonts/$file.ttf")!!.readBytes(),
    weight = FontWeight(weight),
    variationSettings = if (variable) FontVariation.Settings(FontVariation.weight(weight)) else FontVariation.Settings()
)

private val fontCache = HashMap<Settings.FontFamily, FontFamily>()

fun fontFamilyOf(choice: Settings.FontFamily): FontFamily = synchronized(fontCache) {
    fontCache.getOrPut(choice) {
        runCatching {
            when (choice) {
                Settings.FontFamily.Poppins -> FontFamily((300..700 step 100).map { bundledFont("poppins_w$it", it, false) })
                Settings.FontFamily.System -> FontFamily.Default
                else -> {
                    val file = choice.name.lowercase()
                    FontFamily((300..700 step 100).map { bundledFont(file, it, true) })
                }
            }
        }.getOrDefault(FontFamily.Default)
    }
}

val Poppins: FontFamily get() = fontFamilyOf(Settings.FontFamily.Poppins)

/** The phone app's text sizes (xxs 12, xs 14, s 16, m 18, l 20, xxl 32). */
object Type {
    private fun base(size: Int) = TextStyle(
        fontFamily = Look.fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = size.sp,
        // "Apply font padding" adds room above and below each line, like Android's font padding.
        lineHeight = if (Look.fontPadding) (size * 1.5f).sp else TextUnit.Unspecified,
        lineHeightStyle = if (Look.fontPadding) LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None) else null
    )
    val xxs get() = base(12)
    val xs get() = base(14)
    val s get() = base(16)
    val m get() = base(18)
    val l get() = base(20)
    val xxl get() = base(32)
}

val TextStyle.medium get() = copy(fontWeight = FontWeight.Medium)
val TextStyle.semiBold get() = copy(fontWeight = FontWeight.SemiBold)
val TextStyle.bold get() = copy(fontWeight = FontWeight.Bold)
fun TextStyle.color(c: Color) = copy(color = c)

/** Whether macOS is in dark mode, checked every few seconds (for the "System" colour mode). */
private val systemDark: StateFlow<Boolean> by lazy {
    val flow = MutableStateFlow(readSystemDark())
    CoroutineScope(Dispatchers.IO).launch {
        while (true) {
            delay(SYSTEM_THEME_POLL_MS)
            flow.value = readSystemDark()
        }
    }
    flow
}

private fun readSystemDark(): Boolean = runCatching {
    if (!System.getProperty("os.name").lowercase().contains("mac")) return@runCatching true
    val p = ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle").redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText()
    p.waitFor()
    out.contains("Dark", ignoreCase = true)
}.getOrDefault(true)

@Composable
fun VertuneTheme(content: @Composable () -> Unit) {
    val track by PlayerController.current.collectAsState()
    val source by Settings.colorSource.flow.collectAsState()
    val mode by Settings.colorMode.flow.collectAsState()
    val darkness by Settings.darkness.flow.collectAsState()
    val font by Settings.fontFamily.flow.collectAsState()
    val padding by Settings.applyFontPadding.flow.collectAsState()
    val roundness by Settings.thumbnailRoundness.flow.collectAsState()
    val sysDark by systemDark.collectAsState()
    val isDark = when (mode) {
        Settings.ColorMode.System -> sysDark
        Settings.ColorMode.Light -> false
        Settings.ColorMode.Dark -> true
    }

    SideEffect {
        Look.fontFamily = fontFamilyOf(font)
        Look.fontPadding = padding
        Look.roundness = roundness.dp
    }

    // Like the phone's Dynamic colour source, the accent follows the current song's album art.
    var accent by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    LaunchedEffect(track?.thumbnail, source, isDark) {
        val url = track?.thumbnail
        accent = if (source == Settings.ColorSource.Default || url == null) null
        else withContext(Dispatchers.IO) { runCatching { dominantHsl(url, isDark) }.getOrNull() }
    }
    val target = Palette.of(accent, darkness, isDark)

    val anim = tween<Color>(durationMillis = 600)
    val palette = target.copy(
        accent = animateColorAsState(target.accent, anim).value,
        background0 = animateColorAsState(target.background0, anim).value,
        background1 = animateColorAsState(target.background1, anim).value,
        background2 = animateColorAsState(target.background2, anim).value,
        text = animateColorAsState(target.text, anim).value,
        textSecondary = animateColorAsState(target.textSecondary, anim).value,
        textDisabled = animateColorAsState(target.textDisabled, anim).value
    )
    val family = Look.fontFamily

    MaterialTheme(
        colorScheme = (if (isDark) darkColorScheme() else lightColorScheme()).copy(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accent,
            background = palette.background0,
            onBackground = palette.text,
            surface = palette.background0,
            onSurface = palette.text,
            surfaceVariant = palette.background1,
            onSurfaceVariant = palette.textSecondary,
            surfaceContainer = palette.background2,
            surfaceContainerHigh = palette.background2,
            surfaceContainerLow = palette.background1,
            outline = palette.textDisabled,
            error = palette.red
        ),
        typography = Typography().let { t ->
            Typography(
                displayLarge = t.displayLarge.copy(fontFamily = family),
                headlineLarge = t.headlineLarge.copy(fontFamily = family),
                headlineMedium = t.headlineMedium.copy(fontFamily = family),
                headlineSmall = t.headlineSmall.copy(fontFamily = family),
                titleLarge = t.titleLarge.copy(fontFamily = family),
                titleMedium = t.titleMedium.copy(fontFamily = family),
                titleSmall = t.titleSmall.copy(fontFamily = family),
                bodyLarge = t.bodyLarge.copy(fontFamily = family),
                bodyMedium = t.bodyMedium.copy(fontFamily = family),
                bodySmall = t.bodySmall.copy(fontFamily = family),
                labelLarge = t.labelLarge.copy(fontFamily = family),
                labelMedium = t.labelMedium.copy(fontFamily = family),
                labelSmall = t.labelSmall.copy(fontFamily = family)
            )
        }
    ) {
        CompositionLocalProvider(
            LocalPalette provides palette,
            LocalContentColor provides palette.text,
            LocalTextStyle provides Type.s.color(palette.text),
            content = content
        )
    }
}

internal fun Color.hsl(): Pair<Float, Float> {
    val hsb = java.awt.Color.RGBtoHSB((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), null)
    return hsb[0] * 360f to hsl(red, green, blue).second
}

private fun hsl(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2
    val d = max - min
    if (d == 0f) return Triple(0f, 0f, l)
    val s = d / (1 - kotlin.math.abs(2 * l - 1))
    val h = when (max) {
        r -> 60 * (((g - b) / d) % 6)
        g -> 60 * ((b - r) / d + 2)
        else -> 60 * ((r - g) / d + 4)
    }.let { if (it < 0) it + 360 else it }
    return Triple(h, s.coerceIn(0f, 1f), l)
}

/**
 * The album art's main colour, like the phone's Palette-based accent: the most common
 * colour group, skipping yellow-green hues that look muddy on black, and a more colourful
 * group when the main one is nearly grey.
 */
private fun dominantHsl(url: String, isDark: Boolean = true): Pair<Float, Float>? {
    val image = URI(url).toURL().openStream().use(ImageIO::read) ?: return null
    data class Bucket(var count: Int = 0, var h: Double = 0.0, var s: Double = 0.0)
    val buckets = HashMap<Int, Bucket>()
    val step = maxOf(1, minOf(image.width, image.height) / 64)
    for (y in 0 until image.height step step) for (x in 0 until image.width step step) {
        val rgb = image.getRGB(x, y)
        val (h, s, l) = hsl(((rgb shr 16) and 0xff) / 255f, ((rgb shr 8) and 0xff) / 255f, (rgb and 0xff) / 255f)
        if (l < 0.08f || l > 0.95f) continue
        if (isDark && h in 36f..100f && s > 0.15f) continue
        val key = if (s < 0.08f) -1 else (h / 20).toInt()
        val b = buckets.getOrPut(key) { Bucket() }
        b.count++
        b.h += h
        b.s += s
    }
    val main = buckets.maxByOrNull { it.value.count } ?: return null
    val chosen = if (main.key == -1) {
        buckets.filterKeys { it != -1 }.maxByOrNull { it.value.s / it.value.count }?.value ?: return null
    } else main.value
    return (chosen.h / chosen.count).toFloat() to (chosen.s / chosen.count).toFloat()
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "2 h 13 min" style, for listening time. */
fun formatListened(ms: Long): String {
    val minutes = ms / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 -> "$h h $m min"
        minutes > 0 -> "$m min"
        else -> "${ms / 1000} s"
    }
}

private const val SYSTEM_THEME_POLL_MS = 5_000L
