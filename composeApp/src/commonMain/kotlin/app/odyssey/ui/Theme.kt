package app.odyssey.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Deep navy + a verified-green accent. Dark only: this is a field app. */
object Palette {
    val Ink = Color(0xFF0B1020)
    val Surface = Color(0xFF141B31)
    val SurfaceHigh = Color(0xFF1D2740)
    val Line = Color(0xFF2A3557)
    val Verified = Color(0xFF3DDC97)
    val Pending = Color(0xFFF2C14E)
    val Danger = Color(0xFFE5646E)
    val Text = Color(0xFFEDF1FA)
    val Muted = Color(0xFF8A97B8)
}

private val scheme = darkColorScheme(
    primary = Palette.Verified,
    onPrimary = Palette.Ink,
    secondary = Palette.Pending,
    background = Palette.Ink,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.Muted,
    error = Palette.Danger,
)

@Composable
fun OdysseyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
