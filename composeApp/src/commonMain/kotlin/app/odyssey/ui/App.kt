package app.odyssey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.odyssey.AppModel
import app.odyssey.Tab

@Composable
fun OdysseyApp(model: AppModel) {
    OdysseyTheme {
        Box(modifier = Modifier.fillMaxSize().background(Palette.Ink)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                val capturing = model.capturing
                if (capturing != null) {
                    CaptureScreen(model, capturing, modifier = Modifier.weight(1f))
                } else {
                    Box(modifier = Modifier.weight(1f)) {
                        when (model.tab) {
                            Tab.HOME -> HomeScreen(model)
                            Tab.TRACKER -> TrackerScreen(model)
                            Tab.TIMELINE -> TimelineScreen(model)
                            Tab.LEDGER -> LedgerScreen(model)
                        }
                    }
                    BottomBar(model)
                }
            }

            val toast = model.toast
            if (toast != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 16.dp, vertical = 84.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.SurfaceHigh)
                            .clickable { model.toast = null }
                            .padding(14.dp),
                    ) {
                        Text(toast, fontSize = 13.sp, color = Palette.Text)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(model: AppModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TabButton("◎", "Odyssey", model.tab == Tab.HOME, Modifier.weight(1f)) { model.tab = Tab.HOME }
        TabButton("▤", "Tracker", model.tab == Tab.TRACKER, Modifier.weight(1f)) { model.tab = Tab.TRACKER }
        TabButton("▦", "Timeline", model.tab == Tab.TIMELINE, Modifier.weight(1f)) { model.tab = Tab.TIMELINE }
        TabButton("≡", "Ledger", model.tab == Tab.LEDGER, Modifier.weight(1f)) { model.tab = Tab.LEDGER }
    }
}

@Composable
private fun TabButton(
    glyph: String,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
    ) {
        Text(glyph, fontSize = 18.sp, color = if (selected) Palette.Verified else Palette.Muted)
        Box(modifier = Modifier.height(2.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Palette.Text else Palette.Muted,
        )
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
        Text(subtitle, fontSize = 13.sp, color = Palette.Muted)
    }
}
