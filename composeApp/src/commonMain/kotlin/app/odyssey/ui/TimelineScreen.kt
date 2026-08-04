package app.odyssey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import app.odyssey.Route
import app.odyssey.TimelineTab
import app.odyssey.engine.Countries
import app.odyssey.engine.ExploreGroup
import app.odyssey.engine.ExploreItem
import app.odyssey.engine.MemoryItem
import app.odyssey.engine.Scope
import app.odyssey.engine.formatDate

/**
 * P7 / P8 / P9 in one composable — the W/C/S pills choose which one you are
 * looking at, exactly as in the flow.
 *
 * Memories is flat at every level: your visits, newest first. Explore changes
 * granularity with the scope and is strictly a reference list — nothing in it
 * is tappable, because tapping would mean writing selection, and selection has
 * one owner.
 */
@Composable
fun TimelineScreen(model: AppModel) {
    val snap = model.snapshot
    val scope = snap.selection.scope

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            ScreenHeader(
                when (scope) {
                    Scope.WORLD -> "World"
                    Scope.COUNTRY -> Countries.name(snap.selection.country)
                    Scope.STATE -> app.odyssey.engine.CanonV1.stateName(snap.selection.usState)
                },
                when (scope) {
                    Scope.WORLD -> "P7 · every canon place, grouped by country"
                    Scope.COUNTRY -> "P8 · every canon place in the country, grouped by state"
                    Scope.STATE -> "P9 · every canon place in the state"
                },
            )

            val (stateDone, stateTotal) = snap.result.stateProgress(snap.selection.usState)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ScopePie(
                    letter = "W",
                    fraction = (snap.result.placesCoveragePct / 100.0).toFloat(),
                    selected = scope == Scope.WORLD,
                ) { model.setScope(Scope.WORLD) }
                ScopePie(
                    letter = "C",
                    fraction = (snap.result.stateCoveragePct / 100.0).toFloat(),
                    selected = scope == Scope.COUNTRY,
                ) { model.setScope(Scope.COUNTRY) }
                ScopePie(
                    letter = "S",
                    fraction = if (stateTotal == 0) 0f else stateDone.toFloat() / stateTotal,
                    selected = scope == Scope.STATE,
                ) { model.setScope(Scope.STATE) }
            }

            Box(modifier = Modifier.height(14.dp))

            Segmented(
                options = listOf("Memories", "Explore"),
                selectedIndex = if (model.timelineTab == TimelineTab.MEMORIES) 0 else 1,
                onSelect = {
                    model.timelineTab = if (it == 0) TimelineTab.MEMORIES else TimelineTab.EXPLORE
                },
            )

            Box(modifier = Modifier.height(12.dp))
        }

        Box(modifier = Modifier.weight(1f)) {
            if (model.timelineTab == TimelineTab.MEMORIES) {
                MemoriesTab(model)
            } else {
                ExploreTab(model)
            }
        }

        FeedMessageBar(model)
    }
}

// ---------------------------------------------------------------- Memories

@Composable
private fun MemoriesTab(model: AppModel) {
    val feed = model.memoryFeed()

    if (feed.isEmpty()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Card {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("No memories at this scope yet.", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                    Text(
                        "Capture a visit from the Tracker and it lands here, credited or not.",
                        fontSize = 13.sp,
                        color = Palette.Muted,
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(feed.chunked(3)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    MemoryCell(item, Modifier.weight(1f))
                }
                repeat(3 - row.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
        item { Box(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun MemoryCell(item: MemoryItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface)
            .border(
                1.dp,
                if (item.credited) Palette.Verified.copy(alpha = 0.5f) else Palette.Line,
                RoundedCornerShape(12.dp),
            )
            .padding(10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Palette.SurfaceHigh),
        ) {
            Text(
                if (item.mediaCount > 0) "▣ ${item.mediaCount}" else if (item.credited) "✓" else "◦",
                fontSize = if (item.mediaCount > 0) 14.sp else 20.sp,
                color = if (item.credited) Palette.Verified else Palette.Muted,
            )
        }
        Box(modifier = Modifier.height(8.dp))
        Text(
            item.entry?.name ?: item.visit.placeId,
            fontSize = 11.sp,
            color = Palette.Text,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
        )
        Text(item.entry?.usState ?: "", fontSize = 10.sp, color = Palette.Muted)
        Text(formatDate(item.visit.startEpochSec), fontSize = 10.sp, color = Palette.Muted)
        Box(modifier = Modifier.height(6.dp))
        Pill(
            if (item.credited) "counts" else "no credit",
            if (item.credited) Palette.Verified else Palette.Pending,
        )
    }
}

// ----------------------------------------------------------------- Explore

@Composable
private fun ExploreTab(model: AppModel) {
    val groups = model.explore()
    val hasFix = model.location.fix != null
    val scope = model.snapshot.selection.scope

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                if (hasFix) {
                    "Nearest first, from your current fix."
                } else {
                    "No location fix — showing the states you have already started first."
                },
                fontSize = 11.sp,
                color = Palette.Muted,
            )
        }

        groups.forEach { group ->
            item(key = "h-${group.key}") { ExploreGroupHeader(group) }
            items(group.items, key = { it.entry.placeId }) { item -> ExploreRow(item) }
        }

        if (scope == Scope.WORLD) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Palette.Line, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    Text(
                        "${Countries.TOTAL_IN_WORLD - groups.size} more countries arrive with " +
                            "future canon releases. They are not in any denominator today, so " +
                            "they cannot quietly dilute your percentage.",
                        fontSize = 12.sp,
                        color = Palette.Muted,
                    )
                }
            }
        }

        item { Box(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun ExploreGroupHeader(group: ExploreGroup) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(group.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
            Text("${group.remaining} left of ${group.total}", fontSize = 11.sp, color = Palette.Muted)
        }
        Box(modifier = Modifier.height(6.dp))
        MiniBar(
            fraction = if (group.total == 0) 0f else group.credited.toFloat() / group.total,
            accent = if (group.remaining == 0) Palette.Verified else Palette.Pending,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ExploreRow(item: ExploreItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.entry.name, fontSize = 14.sp, color = Palette.Text)
            Text(
                buildString {
                    append(item.entry.usState)
                    item.distanceMeters?.let { append(" · ${it.asDistance()}") }
                    append(" · ${item.entry.minDwellSeconds.asDuration()} dwell")
                },
                fontSize = 11.sp,
                color = Palette.Muted,
            )
        }
        if (item.completesState) {
            Pill("completes state", Palette.Verified)
        }
    }
}

// ------------------------------------------------------------------ footer

/**
 * The footer from the wireframe. Both buttons are placeholders for now — their
 * original jobs (other people's travels, messaging them) need a server that
 * does not exist yet, so neither claims to work.
 *
 * The activity feed they will eventually surface is already built and tested;
 * it lives under the hamburger as "Your activity" until these two get a
 * decision.
 */
@Composable
private fun FeedMessageBar(model: AppModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf("FEED", "MESSAGE").forEach { label ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Palette.Line, RoundedCornerShape(10.dp))
                    .clickable {
                        model.toast = "$label is a placeholder — still an open decision (flow spec §8)."
                    }
                    .padding(vertical = 10.dp),
            ) {
                Text(label, fontSize = 12.sp, color = Palette.Muted)
            }
        }
    }
}
