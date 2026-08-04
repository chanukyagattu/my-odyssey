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
import app.odyssey.engine.CanonEntry
import app.odyssey.engine.CanonV1
import app.odyssey.engine.Countries
import app.odyssey.engine.Lifecycle
import app.odyssey.engine.Scope

/**
 * P4 / P5 / P6 — one screen, three scopes.
 *
 * Each is the same shape: a dial for the level you are on, then the things that
 * level is made of. P5's rows drill into P6; P6's rows are where a visit is
 * actually captured.
 */
@Composable
fun TrackerDetailScreen(model: AppModel, scope: Scope) {
    val snap = model.snapshot
    val r = snap.result
    val stateCode = snap.selection.usState
    val (stateDone, stateTotal) = r.stateProgress(stateCode)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Box(modifier = Modifier.height(4.dp)) }

        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (scope) {
                    Scope.WORLD -> TrackerDial(
                        diameter = 230,
                        value = "${model.countriesVisited()}/${Countries.TOTAL_IN_WORLD}",
                        detail = "countries",
                        scopeLabel = "World",
                        fraction = model.countriesVisited().toFloat() / Countries.TOTAL_IN_WORLD,
                        accent = Palette.Pending,
                        onShare = { model.shareCard(Scope.WORLD) },
                    )

                    Scope.COUNTRY -> TrackerDial(
                        diameter = 230,
                        value = "${r.statesComplete.size}/${r.stateDenominator}",
                        detail = "states",
                        scopeLabel = Countries.name(snap.selection.country),
                        fraction = (r.stateCoveragePct / 100.0).toFloat(),
                        accent = Palette.Verified,
                        onShare = { model.shareCard(Scope.COUNTRY) },
                        onScope = { model.go(Route.COUNTRY_PICKER) },
                    )

                    Scope.STATE -> TrackerDial(
                        diameter = 230,
                        value = "$stateDone/$stateTotal",
                        detail = "places",
                        scopeLabel = model.stateName,
                        fraction = if (stateTotal == 0) 0f else stateDone.toFloat() / stateTotal,
                        accent = if (stateCode in r.statesComplete) Palette.Verified else Palette.Pending,
                        onShare = { model.shareCard(Scope.STATE) },
                        onScope = { model.go(Route.STATE_PICKER) },
                    )
                }
            }
        }

        item { ShareRow { model.shareCurrent() } }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionTitle(
                    when (scope) {
                        Scope.WORLD -> "Countries"
                        Scope.COUNTRY -> "States"
                        Scope.STATE -> "Places"
                    },
                    when (scope) {
                        Scope.WORLD -> "canon v1 covers one"
                        Scope.COUNTRY -> "tap to open a state"
                        Scope.STATE -> "tap to capture"
                    },
                )
            }
        }

        when (scope) {
            Scope.WORLD -> {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Card {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("United States", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
                                    Text(
                                        "${r.placesCredited.size}/${r.placesDenominator} places",
                                        fontSize = 12.sp,
                                        color = Palette.Muted,
                                    )
                                }
                                Box(modifier = Modifier.height(10.dp))
                                MiniBar(
                                    fraction = (r.placesCoveragePct / 100.0).toFloat(),
                                    accent = Palette.Verified,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Box(modifier = Modifier.height(12.dp))
                                Text(
                                    "${Countries.TOTAL_IN_WORLD - 1} more countries arrive with future " +
                                        "canon releases. They are in no denominator today, so they " +
                                        "cannot quietly dilute your percentage.",
                                    fontSize = 12.sp,
                                    color = Palette.Muted,
                                )
                            }
                        }
                    }
                }
            }

            Scope.COUNTRY -> {
                items(snap.canon.statesInPlay()) { code ->
                    val (done, total) = r.stateProgress(code)
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        StateRow(
                            code = code,
                            done = done,
                            total = total,
                            complete = code in r.statesComplete,
                            frozen = code in r.frozenStates,
                            selected = code == stateCode,
                        ) {
                            model.selectState(code)
                            model.openTracker(Scope.STATE)
                        }
                    }
                }
            }

            Scope.STATE -> {
                items(snap.entriesInSelectedState()) { entry ->
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        PlaceRow(model, entry)
                    }
                }
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Places outside the canon are not in the denominator — there is no " +
                                "way to inflate a percentage by visiting more things.",
                            fontSize = 12.sp,
                            color = Palette.Muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StateRow(
    code: String,
    done: Int,
    total: Int,
    complete: Boolean,
    frozen: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = when {
        frozen -> Palette.Muted
        complete -> Palette.Verified
        done > 0 -> Palette.Pending
        else -> Palette.Muted
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface)
            .border(
                1.dp,
                if (selected) Palette.Verified else Palette.Line,
                RoundedCornerShape(12.dp),
            )
            .clickable { onClick() }
            .padding(14.dp),
    ) {
        Text(code, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Text, modifier = Modifier.width(34.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(CanonV1.stateName(code), fontSize = 14.sp, color = Palette.Text)
            Box(modifier = Modifier.height(6.dp))
            MiniBar(
                fraction = if (total == 0) 0f else done.toFloat() / total,
                accent = accent,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(modifier = Modifier.width(12.dp))
        Text("$done/$total", fontSize = 12.sp, color = accent)
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
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                    snap.result.isClaimed(entry.placeId) -> Pill("Claimed, not verified", Palette.Pending)
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
