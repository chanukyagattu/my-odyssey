package app.odyssey.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlin.math.roundToInt

fun Double.pct1(): String {
    val r = (this * 10).roundToInt()
    return "${r / 10}.${r % 10}%"
}

fun Double.asDistance(): String = when {
    this < 1_000 -> "${this.roundToInt()} m"
    this < 100_000 -> "${((this / 100).roundToInt()) / 10}.${((this / 100).roundToInt()) % 10} km"
    else -> "${(this / 1_000).roundToInt()} km"
}

fun Long.asDuration(): String {
    val h = this / 3600
    val m = (this % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/** A ring is the only chart in the app: one number, unmistakable. */
@Composable
fun ProgressRing(
    fraction: Float,
    label: String,
    caption: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    // Hoisted: a DrawScope lambda is not a composable, so it cannot read the
    // theme's composition local directly.
    val trackColor = Palette.SurfaceHigh
    val labelColor = Palette.Text
    val captionColor = Palette.Muted

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2
                val arcSize = androidx.compose.ui.geometry.Size(
                    size.width - stroke,
                    size.height - stroke,
                )
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (fraction > 0f) {
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = labelColor)
                Text(caption, fontSize = 11.sp, color = captionColor, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun MiniBar(fraction: Float, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Palette.SurfaceHigh),
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent),
            )
        }
    }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(Palette.Surface)
        .border(1.dp, Palette.Line, RoundedCornerShape(16.dp))
    Column(
        modifier = if (onClick != null) base.clickable { onClick() } else base,
        content = content,
    )
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SectionTitle(text: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), fontSize = 11.sp, letterSpacing = 1.2.sp, color = Palette.Muted, fontWeight = FontWeight.SemiBold)
        if (trailing != null) Text(trailing, fontSize = 11.sp, color = Palette.Muted)
    }
}

/** Hand-rolled segmented control: no experimental Material APIs, no surprises. */
@Composable
fun Segmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Line, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) Palette.Verified else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    option,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Palette.Ink else Palette.Muted,
                )
            }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    accent: Color = Palette.Verified,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) accent else Palette.SurfaceHigh)
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 15.dp),
    ) {
        Text(
            text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Palette.Ink else Palette.Muted,
        )
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, fontSize = 13.sp, color = Palette.Text)
    }
}
