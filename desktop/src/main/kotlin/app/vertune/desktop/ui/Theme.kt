package app.vertune.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colours taken from the Vertune icon. */
object VertuneColors {
    val Background = Color(0xFF08041D)
    val Surface = Color(0xFF110C2B)
    val SurfaceHigh = Color(0xFF1A1440)
    val Green = Color(0xFF01AB6D)
    val Blue = Color(0xFF1B3E66)
    val Text = Color(0xFFF2F1F8)
    val TextDim = Color(0xFFA8A5BF)
}

@Composable
fun VertuneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = VertuneColors.Green,
            onPrimary = Color.White,
            secondary = VertuneColors.Blue,
            onSecondary = Color.White,
            background = VertuneColors.Background,
            onBackground = VertuneColors.Text,
            surface = VertuneColors.Surface,
            onSurface = VertuneColors.Text,
            surfaceVariant = VertuneColors.SurfaceHigh,
            onSurfaceVariant = VertuneColors.TextDim,
            surfaceContainer = VertuneColors.Surface,
            surfaceContainerHigh = VertuneColors.SurfaceHigh,
            outline = Color(0xFF3A3560)
        ),
        content = content
    )
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
