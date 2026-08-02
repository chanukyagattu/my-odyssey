package app.odyssey.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.odyssey.AppModel
import app.odyssey.Tab
import app.odyssey.engine.CanonV1
import app.odyssey.engine.Scope

@Composable
fun HomeScreen(model: AppModel) {
    val snap = model.snapshot
    val r = snap.result

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                "My Odyssey",
                "Canon release v${r.canonVersion} · ${r.placesDenominator} places · ${r.stateDenominator} states in play",
            )
        }

        item {
            Segmented(
                options = listOf("World", "United States", CanonV1.stateName(snap.selection.usState)),
                selectedIndex = when (snap.selection.scope) {
                    Scope.WORLD -> 0
                    Scope.COUNTRY -> 1
                    Scope.STATE -> 2
                },
                onSelect = {
                    model.setScope(
                        when (it) {
                            0 -> Scope.WORLD
                            1 -> Scope.COUNTRY
                            else -> Scope.STATE
                        },
                    )
                },
            )
        }

        item {
            Card {
                Column(modifier = Modifier.padding(20.dp)) {
                    when (snap.selection.scope) {
                        Scope.WORLD -> WorldRings(model)
                        Scope.COUNTRY -> CountryRings(model)
                        Scope.STATE -> StateRings(model)
                    }
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionTitle("Why these numbers")
                    Text(
                        "Nothing above is stored. Both percentages are a fold of your " +
                            "${snap.events.size}-event ledger against canon v${r.canonVersion}, " +
                            "recomputed from zero every time this screen draws.",
                        fontSize = 13.sp,
                        color = Palette.Muted,
                    )
                    Box(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("${r.placesCredited.size} credited", Palette.Verified)
                        Pill("${snap.visits.size - r.placesCredited.size} uncredited", Palette.Pending)
                        if (r.frozenStates.isNotEmpty()) {
                            Pill("${r.frozenStates.size} frozen", Palette.Muted)
                        }
                    }
                }
            }
        }

        item { SectionTitle("Recent activity", "newest first") }

        val recent = snap.visits.sortedByDescending { it.startEpochSec }.take(6)
        if (recent.isEmpty()) {
            item {
                Card {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("No visits yet.", color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Open Tracker, pick a place, and capture one. Only evidenced " +
                                "visits move a percentage.",
                            color = Palette.Muted,
                            fontSize = 13.sp,
                        )
                        Box(modifier = Modifier.height(14.dp))
                        PrimaryButton("Go to Tracker") { model.tab = Tab.TRACKER }
                    }
                }
            }
        } else {
            items(recent) { v ->
                val entry = snap.canon.byId[v.placeId]
                val evidence = snap.effectiveEvidence(v)
                val credited = r.isCredited(v.placeId)
                Card {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry?.name ?: v.placeId, fontSize = 15.sp, color = Palette.Text, fontWeight = FontWeight.Medium)
                            Text(
                                "${CanonV1.stateName(entry?.usState ?: "")} · ${v.dwellSeconds.asDuration()} dwell",
                                fontSize = 12.sp,
                                color = Palette.Muted,
                            )
                        }
                        Pill(
                            if (credited) evidence.label else evidence.label + " ·  no credit",
                            if (credited) Palette.Verified else Palette.Pending,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorldRings(model: AppModel) {
    val r = model.snapshot.result
    Column {
        Text("World", fontSize = 12.sp, color = Palette.Muted)
        Box(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ProgressRing(
                fraction = (r.stateCoveragePct / 100.0).toFloat(),
                label = r.stateCoveragePct.pct1(),
                caption = "United States\n${r.statesComplete.size}/${r.stateDenominator} states",
                accent = Palette.Verified,
            )
            ProgressRing(
                fraction = 1f / 195f,
                label = "1/195",
                caption = "Countries in canon\n(US only in v1)",
                accent = Palette.Pending,
            )
        }
    }
}

@Composable
private fun CountryRings(model: AppModel) {
    val r = model.snapshot.result
    Column {
        Text("United States", fontSize = 12.sp, color = Palette.Muted)
        Box(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ProgressRing(
                fraction = (r.stateCoveragePct / 100.0).toFloat(),
                label = r.stateCoveragePct.pct1(),
                caption = "States complete\n${r.statesComplete.size}/${r.stateDenominator}",
                accent = Palette.Verified,
            )
            ProgressRing(
                fraction = (r.placesCoveragePct / 100.0).toFloat(),
                label = r.placesCoveragePct.pct1(),
                caption = "Places credited\n${r.placesCredited.size}/${r.placesDenominator}",
                accent = Palette.Pending,
            )
        }
    }
}

@Composable
private fun StateRings(model: AppModel) {
    val snap = model.snapshot
    val code = snap.selection.usState
    val (done, total) = snap.result.stateProgress(code)
    Column {
        Text(CanonV1.stateName(code), fontSize = 12.sp, color = Palette.Muted)
        Box(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            ProgressRing(
                fraction = if (total == 0) 0f else done.toFloat() / total,
                label = "$done/$total",
                caption = if (code in snap.result.statesComplete) "Complete" else "Places credited",
                accent = if (code in snap.result.statesComplete) Palette.Verified else Palette.Pending,
            )
        }
        Box(modifier = Modifier.height(14.dp))
        PrimaryButton("Open ${CanonV1.stateName(code)} tracker") { model.tab = Tab.TRACKER }
    }
}
