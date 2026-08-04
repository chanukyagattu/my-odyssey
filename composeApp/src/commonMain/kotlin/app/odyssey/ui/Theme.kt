package app.odyssey.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.odyssey.engine.ThemeMode

/**
 * Colour carries meaning here, never decoration:
 *
 *   verified — counted toward a percentage
 *   pending  — recorded but not counted
 *   danger   — revoked, or a compensating event
 *   muted    — untouched
 *
 * The same four run from a pill on a place row all the way to the share card,
 * so the vocabulary is learned once. Both schemes keep those roles identical;
 * only the surfaces flip.
 */
data class OdysseyColors(
    val ink: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val line: Color,
    val verified: Color,
    val pending: Color,
    /**
     * "Still to visit" — the unfilled arc of a pie.
     *
     * Distinct from [pending] on purpose: pending means *recorded but not
     * counted*, which is a thing that happened. Remaining means nothing has
     * happened yet. Sharing a colour would have made the pies lie.
     *
     * Pale rather than a second hue, so done-versus-remaining separates by
     * luminance. That survives greyscale and every form of colour blindness,
     * which a green/orange pairing does not.
     */
    val remaining: Color,
    val danger: Color,
    val text: Color,
    val muted: Color,
    val isDark: Boolean,
)

/** Deep navy. The default, because this is a field app used outdoors on battery. */
val DarkColors = OdysseyColors(
    ink = Color(0xFF0B1020),
    surface = Color(0xFF141B31),
    surfaceHigh = Color(0xFF1D2740),
    line = Color(0xFF2A3557),
    verified = Color(0xFF3DDC97),
    pending = Color(0xFFF2C14E),
    remaining = Color(0xFFDDE6F2),
    danger = Color(0xFFE5646E),
    text = Color(0xFFEDF1FA),
    muted = Color(0xFF8A97B8),
    isDark = true,
)

/**
 * Accents are darkened rather than reused. The dark-scheme mint fails contrast
 * as text on white, and these percentages are the whole product — they have to
 * be readable in direct sunlight at a trailhead.
 */
val LightColors = OdysseyColors(
    ink = Color(0xFFF4F7FC),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFE4EAF5),
    line = Color(0xFFD2DBEA),
    verified = Color(0xFF0E8A5F),
    pending = Color(0xFFA9760A),
    remaining = Color(0xFFB7C6DC),
    danger = Color(0xFFC22F3B),
    text = Color(0xFF0F1626),
    muted = Color(0xFF5A6781),
    isDark = false,
)

val LocalOdysseyColors = staticCompositionLocalOf { DarkColors }

/**
 * Read through a composition local so every screen re-themes for free. The
 * call sites stay `Palette.Verified`, which is why switching themes did not
 * mean editing thirteen files.
 */
object Palette {
    val Ink: Color @Composable get() = LocalOdysseyColors.current.ink
    val Surface: Color @Composable get() = LocalOdysseyColors.current.surface
    val SurfaceHigh: Color @Composable get() = LocalOdysseyColors.current.surfaceHigh
    val Line: Color @Composable get() = LocalOdysseyColors.current.line
    val Verified: Color @Composable get() = LocalOdysseyColors.current.verified
    val Pending: Color @Composable get() = LocalOdysseyColors.current.pending
    val Remaining: Color @Composable get() = LocalOdysseyColors.current.remaining
    val Danger: Color @Composable get() = LocalOdysseyColors.current.danger
    val Text: Color @Composable get() = LocalOdysseyColors.current.text
    val Muted: Color @Composable get() = LocalOdysseyColors.current.muted
}

private fun OdysseyColors.material(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = verified,
        onPrimary = ink,
        secondary = pending,
        background = ink,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = muted,
        error = danger,
    )
} else {
    lightColorScheme(
        primary = verified,
        onPrimary = ink,
        secondary = pending,
        background = ink,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = muted,
        error = danger,
    )
}

@Composable
fun OdysseyTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalOdysseyColors provides colors) {
        MaterialTheme(colorScheme = colors.material(), content = content)
    }
}
