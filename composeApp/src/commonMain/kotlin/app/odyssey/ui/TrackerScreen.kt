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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import app.odyssey.engine.CanonEntry
import app.odyssey.engine.CanonV1
import app.odyssey.engine.Lifecycle

@Composable
fun TrackerScreen(model: AppModel) {
    val snap = model.snapshot
    val code = snap.selection.usState
    val (done, total) = snap.result.stateProgress(code)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                ScreenHeader(
                    "Tracker",
                    "Selection is owned by the scope control — every other page reads it.",
                )
            }
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(snap.canon.statesInPlay()) { st ->
                    val (d, t) = snap.result.stateProgress(st)
                    StateChip(
                        code = st,
                        done = d,
                        total = t,
                        selected = st == code,
                        frozen = st in snap.result.frozenStates,
                        onClick = { model.selectState(st) },
                    )
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    CanonV1.stateName(code),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.Text,
                                )
                                Text(
                                    "$done of $total canon places credited",
                                    fontSize = 13.sp,
                                    color = Palette.Muted,
                                )
                            }
                            if (code in snap.result.statesComplete) {
                                Pill("Complete", Palette.Verified)
                            } else if (code in snap.result.frozenStates) {
                                Pill("Frozen", Palette.Muted)
                            } else {
                                Pill("In progress", Palette.Pending)
                            }
                        }
                        Box(modifier = Modifier.height(12.dp))
                        MiniBar(
                            fraction = if (total == 0) 0f else done.toFloat() / total,
                            accent = if (done == total && total > 0) Palette.Verified else Palette.Pending,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionTitle("Canon places", "dwell floor must be met")
            }
        }

        items(snap.entriesInSelectedState()) { entry ->
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                PlaceRow(model, entry)
            }
        }

        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Places outside the canon are not in the denominator — " +
                        "there is no way to inflate a percentage by visiting more things.",
                    fontSize = 12.sp,
                    color = Palette.Muted,
                )
            }
        }
    }
}

@Composable
private fun StateChip(
    code: String,
    done: Int,
    total: Int,
    selected: Boolean,
    frozen: Boolean,
    onClick: () -> Unit,
) {
    val accent = when {
        frozen -> Palette.Muted
        total > 0 && done == total -> Palette.Verified
        done > 0 -> Palette.Pending
        else -> Palette.Muted
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Palette.SurfaceHigh else Palette.Surface)
            .border(
                1.dp,
                if (selected) Palette.Verified else Palette.Line,
                RoundedCornerShape(14.dp),
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp),
    ) {
        Text(code, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
        Text("$done/$total", fontSize = 11.sp, color = accent)
        Box(modifier = Modifier.height(6.dp))
        MiniBar(
            fraction = if (total == 0) 0f else done.toFloat() / total,
            accent = accent,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PlaceRow(model: AppModel, entry: CanonEntry) {
    val snap = model.snapshot
    val credited = snap.result.isCredited(entry.placeId)
    val evidence = snap.result.creditedEvidence[entry.placeId]
    val attempts = snap.visitsFor(entry.placeId)
    val distance = model.distanceTo(entry)

    Card(onClick = { model.openCapture(entry) }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (credited) "✓" else "○",
                    fontSize = 18.sp,
                    color = if (credited) Palette.Verified else Palette.Muted,
                )
                Box(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
                    Text(
                        "Dwell floor ${entry.minDwellSeconds.asDuration()} · " +
                            "geofence ${entry.geofenceRadiusMeters.asDistance()}",
                        fontSize = 12.sp,
                        color = Palette.Muted,
                    )
                }
            }

            Box(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    entry.lifecycle == Lifecycle.SUSPENDED -> Pill("Suspended", Palette.Muted)
                    credited -> Pill(evidence?.label ?: "Credited", Palette.Verified)
                    attempts.isNotEmpty() -> Pill("${attempts.size} visit(s), no credit", Palette.Pending)
                    else -> Pill("Not visited", Palette.Muted)
                }
                if (distance != null) {
                    Pill(
                        "${distance.asDistance()} away",
                        if (model.insideGeofence(entry)) Palette.Verified else Palette.Muted,
                    )
                }
            }

            Box(modifier = Modifier.height(14.dp))
            PrimaryButton(if (credited) "Capture again" else "Capture visit") { model.openCapture(entry) }
        }
    }
}
