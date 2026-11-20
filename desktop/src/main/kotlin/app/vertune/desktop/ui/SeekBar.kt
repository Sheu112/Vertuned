package app.vertune.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vertune.desktop.data.Settings
import kotlin.math.PI
import kotlin.math.sin

/**
 * The phone's seek bar (ui/components/SeekBar.kt): "Wavy" draws a moving wave over the played
 * part while the song plays, "Static" is a plain bar with a round handle. Click or drag to seek.
 * The times underneath show while dragging (or always, with [alwaysShowDuration]); clicking the
 * left time switches between elapsed and remaining, like on the phone.
 */
@Composable
fun SeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.text,
    backgroundColor: Color = LocalPalette.current.background2,
    alwaysShowDuration: Boolean = true,
    scrubberRadius: Dp = 6.dp
) {
    val style by Settings.seekBarStyle.flow.collectAsState()
    val range = 0L..duration.coerceAtLeast(1L)
    var scrubbing by remember { mutableStateOf<Long?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val animated by animateFloatAsState(position.toFloat())
    val shown = scrubbing ?: animated.toLong().coerceIn(range)
    val seek by rememberUpdatedState(onSeek)
    val currentRange by rememberUpdatedState(range)

    fun at(x: Float, width: Int) =
        (x / width.coerceAtLeast(1) * (currentRange.last - currentRange.first) + currentRange.first).toLong().coerceIn(currentRange)

    val input = Modifier
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { dragging = true; scrubbing = at(it.x, size.width) },
                onDragEnd = { dragging = false; scrubbing?.let(seek); scrubbing = null },
                onDragCancel = { dragging = false; scrubbing?.let(seek); scrubbing = null }
            ) { change, _ -> scrubbing = at(change.position.x, size.width) }
        }
        .pointerInput(Unit) {
            detectTapGestures { seek(at(it.x, size.width)) }
        }

    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(24.dp).then(input), contentAlignment = Alignment.Center) {
            when (style) {
                Settings.SeekBarStyle.Static -> StaticBar(shown, range, dragging, color, backgroundColor, scrubberRadius)
                Settings.SeekBarStyle.Wavy -> WavyBar(shown, range, dragging, isActive && duration > 0, color, backgroundColor, scrubberRadius)
            }
        }
        if (alwaysShowDuration || scrubbing != null) Times(shown, duration)
    }
}

@Composable
private fun StaticBar(position: Long, range: LongRange, dragging: Boolean, color: Color, background: Color, scrubberRadius: Dp) {
    val transition = updateTransition(dragging)
    val barHeight by transition.animateDp { if (it) scrubberRadius else 3.dp }
    val radius by transition.animateDp { if (it) 0.dp else scrubberRadius }
    val fraction = ((position - range.first).toFloat() / (range.last - range.first)).coerceIn(0f, 1f)
    Box(
        Modifier
            .padding(horizontal = scrubberRadius)
            .fillMaxWidth()
            .height(scrubberRadius * 2)
            .drawWithContent {
                drawContent()
                drawCircle(color, radius.toPx(), center.copy(x = fraction * size.width))
            }
    ) {
        Spacer(Modifier.height(barHeight).fillMaxWidth().background(background, RoundedCornerShape(8.dp)).align(Alignment.Center))
        Spacer(Modifier.height(barHeight).fillMaxWidth(fraction).background(color, RoundedCornerShape(8.dp)).align(Alignment.CenterStart))
    }
}

@Composable
private fun WavyBar(position: Long, range: LongRange, dragging: Boolean, isActive: Boolean, color: Color, background: Color, scrubberRadius: Dp) {
    val quality by Settings.wavySeekBarQuality.flow.collectAsState()
    val transition = updateTransition(dragging)
    val amplitude by transition.animateDp { if (it || !isActive) 0.dp else 2.dp }
    val scrubberHeight by transition.animateDp { if (it) 20.dp else 15.dp }
    val fraction = ((position - range.first).toFloat() / (range.last - range.first)).coerceIn(0f, 1f)
    val progress by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing))
    )

    Box(
        Modifier
            .padding(horizontal = scrubberRadius)
            .fillMaxWidth()
            .height(20.dp)
            .drawWithContent {
                drawContent()
                drawScrubber(fraction, color, scrubberHeight)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxWidth().height(6.dp)) {
            Spacer(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(1f - fraction)
                    .background(background, RoundedCornerShape(8.dp))
                    .align(Alignment.CenterEnd)
            )
            Canvas(Modifier.fillMaxWidth(fraction).height(amplitude).align(Alignment.CenterStart)) {
                drawPath(
                    path = wavePath(size, progress, quality.quality),
                    color = color,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

private fun ContentDrawScope.drawScrubber(fraction: Float, color: Color, height: Dp) {
    val x = fraction * size.width
    val w = 5.dp.toPx()
    val h = height.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(x - w / 2, (size.height - h) / 2f),
        size = Size(w, h),
        cornerRadius = CornerRadius(w / 2)
    )
}

private fun Density.wavePath(size: Size, progress: Float, quality: Float) = Path().apply {
    val width = size.width
    val height = size.height
    val progressTau = progress * 2 * PI.toFloat()
    val scale = 7.dp.toPx()
    fun f(x: Float) = (sin(x / scale + progressTau) + 0.5f) * height
    moveTo(0f, f(0f))
    var x = 0f
    while (x < width) {
        lineTo(x, f(x))
        x += quality
    }
    lineTo(width, f(width))
}

@Composable
private fun Times(position: Long, duration: Long) {
    val palette = LocalPalette.current
    val remaining by Settings.showRemaining.flow.collectAsState()
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        BasicText(
            if (remaining) "-${formatTime(duration - position)}" else formatTime(position),
            style = Type.xxs.semiBold.color(palette.text),
            modifier = Modifier.clickable { Settings.showRemaining.value = !remaining }
        )
        if (duration > 0) BasicText(formatTime(duration), style = Type.xxs.semiBold.color(palette.text))
    }
}
