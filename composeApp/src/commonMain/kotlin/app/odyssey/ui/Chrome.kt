package app.odyssey.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.odyssey.engine.ThemeMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The wireframe's shared furniture: the bar across the top, the TRACKER /
 * TIMELINE tabs, the tracker dials, and the "share on…" row.
 */

@Composable
fun OdysseyBar(
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    leadingLabel: String? = null,
    initials: String? = null,
    onAvatar: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.CenterStart) {
            when {
                onBack != null -> Text(
                    "←",
                    fontSize = 22.sp,
                    color = Palette.Text,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onBack() }.padding(4.dp),
                )

                onMenu != null -> Text(
                    "☰",
                    fontSize = 20.sp,
                    color = Palette.Text,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onMenu() }.padding(4.dp),
                )
            }
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Palette.Line, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text("MY ODYSSEY", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                }
                if (leadingLabel != null) {
                    Text(leadingLabel, fontSize = 11.sp, color = Palette.Muted, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) {
            if (initials != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Palette.SurfaceHigh)
                        .border(1.dp, Palette.Verified, RoundedCornerShape(18.dp))
                        .clickable(enabled = onAvatar != null) { onAvatar?.invoke() },
                ) {
                    Text(initials, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Verified)
                }
            }
        }
    }
}

@Composable
fun TopTabs(trackerSelected: Boolean, onTracker: () -> Unit, onTimeline: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TabPill("TRACKER", trackerSelected, Modifier.weight(1f), onTracker)
        TabPill("TIMELINE", !trackerSelected, Modifier.weight(1f), onTimeline)
    }
}

@Composable
private fun TabPill(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Palette.Verified else Color.Transparent)
            .border(1.dp, if (selected) Palette.Verified else Palette.Line, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 9.dp),
    ) {
        Text(
            text,
            fontSize = 12.sp,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Palette.Ink else Palette.Muted,
        )
    }
}

/**
 * One ring, drawn identically everywhere: the app icon, the big dials on
 * P3–P6, and the small W/C/S selector on P7–P9. Every progress circle in the
 * product is this function.
 */
private fun DrawScope.progressRing(
    fraction: Float,
    track: Color,
    from: Color,
    to: Color,
    marker: Color,
    strokeWidth: Float,
    alpha: Float = 1f,
) {
    val inset = strokeWidth / 2
    val topLeft = Offset(inset, inset)
    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
    val sweep = 360f * fraction.coerceIn(0f, 1f)

    // The track is thinner than the progress so the filled part dominates.
    drawArc(
        color = track.copy(alpha = alpha),
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokeWidth * 0.62f),
    )

    if (sweep <= 0f) return

    // Rotating the canvas rather than the gradient: a sweep gradient begins at
    // three o'clock, and the arc begins at twelve, so without this the colours
    // land a quarter-turn away from where the stroke actually starts.
    rotate(degrees = -90f) {
        drawArc(
            brush = Brush.sweepGradient(
                0f to from,
                (sweep / 360f).coerceIn(0.01f, 1f) to to,
                1f to to,
                center = center,
            ),
            startAngle = 0f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            alpha = alpha,
        )
    }

    // Where progress began.
    drawCircle(
        color = marker.copy(alpha = alpha),
        radius = strokeWidth * 0.26f,
        center = Offset(size.width / 2, inset),
    )
}

/**
 * The circles from the wireframe. The caption inside says what the dial counts;
 * the pill underneath is the scope it counts within.
 */
@Composable
fun TrackerDial(
    diameter: Int,
    caption: String,
    value: String,
    detail: String,
    scopeLabel: String,
    fraction: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
) {
    // A DrawScope lambda is not a composable, so theme colours are read here.
    val trackColor = Palette.Remaining
    val markerColor = Palette.Text
    val far = if (accent == Palette.Verified) Palette.VerifiedFar else accent

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(diameter.dp)
                .clip(RoundedCornerShape(diameter.dp))
                .clickable(enabled = onClick != null) { onClick?.invoke() },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                progressRing(
                    fraction = fraction,
                    track = trackColor,
                    from = accent,
                    to = far,
                    marker = markerColor,
                    strokeWidth = (diameter / 11f).dp.toPx(),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = (diameter / 7).dp),
            ) {
                Text(
                    caption,
                    fontSize = if (diameter > 180) 12.sp else 10.sp,
                    color = Palette.Muted,
                    textAlign = TextAlign.Center,
                    lineHeight = if (diameter > 180) 15.sp else 12.sp,
                )
                Box(modifier = Modifier.height(6.dp))
                Text(
                    value,
                    fontSize = if (diameter > 180) 34.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Text,
                )
                Text(
                    detail,
                    fontSize = if (diameter > 180) 12.sp else 9.sp,
                    color = Palette.Muted,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Box(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, Palette.Line, RoundedCornerShape(6.dp))
                .padding(horizontal = 14.dp, vertical = 5.dp),
        ) {
            Text(
                scopeLabel.uppercase(),
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                color = Palette.Text,
            )
        }

        if (onShare != null) {
            Box(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Palette.Verified.copy(alpha = 0.16f))
                    .clickable { onShare() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("↗  Share card", fontSize = 11.sp, color = Palette.Verified, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * The W / C / S selector on P7 / P8 / P9 — the same pie as everywhere else,
 * shrunk. Two slices: done and still to go.
 *
 * Green and orange are the strongest pairing for "finished / not finished", but
 * they are also the classic red-green confusion pair, so the two are separated
 * by luminance as well as hue — bright mint against mid orange stays legible in
 * greyscale. Selection is carried by a ring and a filled letter, never by
 * colour alone.
 */
@Composable
fun ScopePie(
    letter: String,
    fraction: Float,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val done = Palette.Verified
    val togo = Palette.Remaining
    val marker = Palette.Text
    val dim = if (selected) 1f else 0.38f

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(29.dp))
                .clickable { onClick() },
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                val stroke = size.minDimension * 0.20f
                // The track is thinner and quieter than the progress, so the
                // eye lands on what has been done rather than what has not.
                val trackStroke = stroke * 0.62f
                val inset = stroke / 2
                val arc = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)
                val sweep = 360f * fraction.coerceIn(0f, 1f)

                drawArc(
                    color = togo.copy(alpha = dim),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arc,
                    style = Stroke(width = trackStroke),
                )
                if (sweep > 0f) {
                    drawArc(
                        color = done.copy(alpha = dim),
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arc,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    // "You started here", matching the app icon.
                    drawCircle(
                        color = marker.copy(alpha = dim),
                        radius = stroke * 0.26f,
                        center = Offset(size.width / 2, inset),
                    )
                }
            }
            Text(
                letter,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Palette.Text else Palette.Muted,
            )
        }

        // Selection is a dot, not a colour — the pies are already carrying
        // two colours of their own.
        Box(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (selected) Palette.Verified else Color.Transparent),
        )
    }
}

/**
 * Appearance control for the hamburger menu: light, dark, follow the phone.
 *
 * Icons are drawn rather than typed. A sun, a crescent and a display are
 * unambiguous at 18dp in any locale, whereas the corresponding glyphs render
 * inconsistently and emoji would drag in a colour font.
 */
@Composable
fun ThemeToggle(mode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Line, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ThemeIcon(ThemeMode.LIGHT, mode, onSelect)
        ThemeIcon(ThemeMode.DARK, mode, onSelect)
        ThemeIcon(ThemeMode.SYSTEM, mode, onSelect)
    }
}

@Composable
private fun ThemeIcon(target: ThemeMode, current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val selected = target == current
    val chipColor = if (selected) Palette.Verified else Color.Transparent
    val tint = if (selected) Palette.Ink else Palette.Muted
    // The crescent is carved by overdrawing in the chip's own colour, so the
    // cut-out has to know what it is sitting on.
    val carve = if (selected) Palette.Verified else Palette.Surface

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 52.dp, height = 38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(chipColor)
            .clickable { onSelect(target) },
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2
            when (target) {
                ThemeMode.LIGHT -> {
                    drawCircle(color = tint, radius = r * 0.42f, center = c)
                    repeat(8) { i ->
                        val a = (i * 45.0) * (PI / 180.0)
                        drawLine(
                            color = tint,
                            start = Offset(c.x + (r * 0.66f * cos(a)).toFloat(), c.y + (r * 0.66f * sin(a)).toFloat()),
                            end = Offset(c.x + (r * 0.98f * cos(a)).toFloat(), c.y + (r * 0.98f * sin(a)).toFloat()),
                            strokeWidth = r * 0.16f,
                            cap = StrokeCap.Round,
                        )
                    }
                }

                ThemeMode.DARK -> {
                    drawCircle(color = tint, radius = r * 0.86f, center = c)
                    drawCircle(
                        color = carve,
                        radius = r * 0.74f,
                        center = Offset(c.x + r * 0.46f, c.y - r * 0.34f),
                    )
                }

                ThemeMode.SYSTEM -> {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(c.x - r * 0.92f, c.y - r * 0.78f),
                        size = Size(r * 1.84f, r * 1.28f),
                        cornerRadius = CornerRadius(r * 0.22f, r * 0.22f),
                        style = Stroke(width = r * 0.18f),
                    )
                    drawLine(
                        color = tint,
                        start = Offset(c.x - r * 0.42f, c.y + r * 0.82f),
                        end = Offset(c.x + r * 0.42f, c.y + r * 0.82f),
                        strokeWidth = r * 0.18f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
fun ShareRow(onShare: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("share on…", fontSize = 11.sp, color = Palette.Muted)
        Box(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            // The system sheet decides which of these actually appear, so every
            // one of them routes to the same place rather than pretending to
            // integrate with five networks.
            listOf("X", "I", "F", "W").forEach { glyph ->
                ShareChip(glyph, Modifier.weight(1f), onShare)
            }
            ShareChip("Other", Modifier.weight(1.6f), onShare)
        }
    }
}

@Composable
private fun ShareChip(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 9.dp),
    ) {
        Text(label, fontSize = 12.sp, color = Palette.Text)
    }
}

/** Pill-shaped field, matching the rounded inputs drawn on P1 and P2. */
@Composable
fun OdysseyField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Line, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Palette.Text, fontSize = 15.sp),
            cursorBrush = SolidColor(Palette.Verified),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 15.sp, color = Palette.Muted)
                }
                inner()
            },
        )
    }
}
