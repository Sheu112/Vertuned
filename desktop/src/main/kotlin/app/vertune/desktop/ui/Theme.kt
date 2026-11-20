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
import app.vertune.desktop.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import javax.imageio.ImageIO

/**
 * The phone app's look: its dark palette with a pure black background (the "AMOLED" setting),
 * an accent colour taken from the album art of the song that's playing, and the Poppins font.
 * Values are the same as core/ui ColorPalette.kt and Typography.kt in the phone app.
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
    val blue: Color = Color(0xff4472cf)
) {
    companion object {
        /** Same as the phone's default accent. */
        val DefaultAccent = Color(0xff3e44ce)

        fun dark(hue: Float, saturation: Float) = Palette(
            background0 = Color.Black,
            background1 = Color.hsl(hue, saturation.coerceAtMost(0.3f), 0.08f),
            background2 = Color.hsl(hue, saturation.coerceAtMost(0.4f), 0.2f),
            accent = Color.hsl(hue, saturation.coerceAtMost(0.4f), 0.5f),
            text = Color.hsl(hue, saturation.coerceAtMost(0.02f), 0.88f),
            textSecondary = Color.hsl(hue, saturation.coerceAtMost(0.1f), 0.65f),
            textDisabled = Color.hsl(hue, saturation.coerceAtMost(0.2f), 0.40f)
        )

        val Default: Palette = DefaultAccent.hsl().let { (h, s) -> dark(h, s) }
    }
}

val LocalPalette = staticCompositionLocalOf { Palette.Default }

val Poppins: FontFamily by lazy {
    fun font(weight: Int) = Font(
        identity = "poppins_w$weight",
        data = Palette::class.java.getResourceAsStream("/fonts/poppins_w$weight.ttf")!!.readBytes(),
        weight = FontWeight(weight)
    )
    runCatching { FontFamily(font(300), font(400), font(500), font(600), font(700)) }
        .getOrDefault(FontFamily.Default)
}

/** The phone app's text sizes (xxs 12, xs 14, s 16, m 18, l 20, xxl 32). */
object Type {
    private val base get() = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal)
    val xxs get() = base.copy(fontSize = 12.sp)
    val xs get() = base.copy(fontSize = 14.sp)
    val s get() = base.copy(fontSize = 16.sp)
    val m get() = base.copy(fontSize = 18.sp)
    val l get() = base.copy(fontSize = 20.sp)
    val xxl get() = base.copy(fontSize = 32.sp)
}

val TextStyle.medium get() = copy(fontWeight = FontWeight.Medium)
val TextStyle.semiBold get() = copy(fontWeight = FontWeight.SemiBold)
val TextStyle.bold get() = copy(fontWeight = FontWeight.Bold)
fun TextStyle.color(c: Color) = copy(color = c)

@Composable
fun VertuneTheme(content: @Composable () -> Unit) {
    val track by PlayerController.current.collectAsState()
    var target by remember { mutableStateOf(Palette.Default) }

    // Like the phone, the accent follows the album art of the current song.
    LaunchedEffect(track?.thumbnail) {
        val url = track?.thumbnail ?: return@LaunchedEffect
        val hsl = withContext(Dispatchers.IO) { runCatching { dominantHsl(url) }.getOrNull() }
        target = hsl?.let { (h, s) -> Palette.dark(h, s) } ?: Palette.Default
    }

    val anim = tween<Color>(durationMillis = 600)
    val accent by animateColorAsState(target.accent, anim)
    val background1 by animateColorAsState(target.background1, anim)
    val background2 by animateColorAsState(target.background2, anim)
    val palette = target.copy(accent = accent, background1 = background1, background2 = background2)

    MaterialTheme(
        colorScheme = darkColorScheme(
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
                displayLarge = t.displayLarge.copy(fontFamily = Poppins),
                headlineLarge = t.headlineLarge.copy(fontFamily = Poppins),
                headlineMedium = t.headlineMedium.copy(fontFamily = Poppins),
                headlineSmall = t.headlineSmall.copy(fontFamily = Poppins),
                titleLarge = t.titleLarge.copy(fontFamily = Poppins),
                titleMedium = t.titleMedium.copy(fontFamily = Poppins),
                titleSmall = t.titleSmall.copy(fontFamily = Poppins),
                bodyLarge = t.bodyLarge.copy(fontFamily = Poppins),
                bodyMedium = t.bodyMedium.copy(fontFamily = Poppins),
                bodySmall = t.bodySmall.copy(fontFamily = Poppins),
                labelLarge = t.labelLarge.copy(fontFamily = Poppins),
                labelMedium = t.labelMedium.copy(fontFamily = Poppins),
                labelSmall = t.labelSmall.copy(fontFamily = Poppins)
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

private fun Color.hsl(): Pair<Float, Float> {
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
private fun dominantHsl(url: String): Pair<Float, Float>? {
    val image = URI(url).toURL().openStream().use(ImageIO::read) ?: return null
    data class Bucket(var count: Int = 0, var h: Double = 0.0, var s: Double = 0.0)
    val buckets = HashMap<Int, Bucket>()
    val step = maxOf(1, minOf(image.width, image.height) / 64)
    for (y in 0 until image.height step step) for (x in 0 until image.width step step) {
        val rgb = image.getRGB(x, y)
        val (h, s, l) = hsl(((rgb shr 16) and 0xff) / 255f, ((rgb shr 8) and 0xff) / 255f, (rgb and 0xff) / 255f)
        if (l < 0.08f || l > 0.95f) continue
        if (h in 36f..100f && s > 0.15f) continue
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
