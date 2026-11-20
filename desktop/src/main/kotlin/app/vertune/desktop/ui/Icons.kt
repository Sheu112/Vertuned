package app.vertune.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.xml.sax.InputSource

/**
 * The phone app's own icons: its vector drawables (app/src/main/res/drawable) are copied into
 * desktop/src/main/resources/drawable and loaded here, so the icons look identical.
 */
object AppIcons {
    private val cache = HashMap<String, ImageVector>()

    @Synchronized
    fun get(name: String): ImageVector = cache.getOrPut(name) {
        AppIcons::class.java.getResourceAsStream("/drawable/$name.xml")!!.use {
            loadXmlImageVector(InputSource(it), Density(1f))
        }
    }
}

@Composable
fun AppIcon(name: String, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Image(
        imageVector = AppIcons.get(name),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size)
    )
}

/** Like the phone's IconButton: a round touch target around an 18–24 dp icon. */
@Composable
fun AppIconButton(
    name: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    enabled: Boolean = true
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp)
    ) {
        AppIcon(name, if (enabled) tint else tint.copy(alpha = 0.4f), size = size)
    }
}
