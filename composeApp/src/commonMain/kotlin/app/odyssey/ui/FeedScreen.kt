package app.odyssey.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.odyssey.AppModel
import app.odyssey.engine.ActivityKind
import app.odyssey.engine.activityFeed

/**
 * FEED — your own history, in plain language.
 *
 * The original idea was other people's travels, which a device-only app cannot
 * honestly provide. This is the version it can: the ledger, read back as
 * sentences, newest first. Same fold, different rendering.
 */
@Composable
fun FeedScreen(model: AppModel) {
    val entries = activityFeed(model.snapshot)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader("Your feed", "Everything that has happened, in the order the ledger recorded it.")
        }

        if (entries.isEmpty()) {
            item {
                Card {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Nothing has happened yet.", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                        Text(
                            "Capture a visit and it appears here — credited or not.",
                            fontSize = 13.sp,
                            color = Palette.Muted,
                        )
                    }
                }
            }
        }

        items(entries) { entry ->
            val accent: Color = when (entry.kind) {
                ActivityKind.VERIFIED -> Palette.Verified
                ActivityKind.RECORDED -> Palette.Pending
                ActivityKind.UPGRADED -> Palette.Verified
                ActivityKind.REVOKED -> Palette.Danger
                ActivityKind.MEDIA_ADDED -> Palette.Muted
                ActivityKind.MEDIA_REMOVED -> Palette.Muted
            }
            val glyph = when (entry.kind) {
                ActivityKind.VERIFIED -> "✓"
                ActivityKind.RECORDED -> "○"
                ActivityKind.UPGRADED -> "▲"
                ActivityKind.REVOKED -> "✕"
                ActivityKind.MEDIA_ADDED -> "▣"
                ActivityKind.MEDIA_REMOVED -> "▢"
            }

            Card {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
                    Text(glyph, fontSize = 15.sp, color = accent)
                    Box(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.headline, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
                        Box(modifier = Modifier.height(3.dp))
                        Text(entry.detail, fontSize = 12.sp, color = Palette.Muted)
                    }
                    entry.dateLabel?.let {
                        Text(it, fontSize = 11.sp, color = Palette.Muted)
                    }
                }
            }
        }

        item {
            Text(
                "No one else appears here. Nothing you record leaves this device — " +
                    "the only thing that travels is a share card you send yourself.",
                fontSize = 11.sp,
                color = Palette.Muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
