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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.odyssey.AppModel
import app.odyssey.Route
import app.odyssey.engine.Countries
import app.odyssey.engine.Scope

/**
 * P3 — the home tracker.
 *
 * One dial per level of the hierarchy: the world by countries, the country by
 * states, the selected state by must-go places. Tapping a dial drills into
 * P4 / P5 / P6.
 */
@Composable
fun HomeScreen(model: AppModel) {
    val snap = model.snapshot
    val r = snap.result
    val stateCode = snap.selection.regionCode
    val (stateDone, stateTotal) = r.regionProgress(stateCode)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { Box(modifier = Modifier.height(4.dp)) }

        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TrackerDial(
                    diameter = 210,
                    value = "${model.countriesVisited()}/${Countries.TOTAL_IN_WORLD}",
                    detail = "countries",
                    scopeLabel = "World",
                    fraction = model.countriesVisited().toFloat() / Countries.TOTAL_IN_WORLD,
                    accent = Palette.Pending,
                    onClick = { model.openTracker(Scope.WORLD) },
                    onShare = { model.shareCard(Scope.WORLD) },
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TrackerDial(
                    diameter = 132,
                    value = "${r.regionsComplete.size}/${r.regionDenominator}",
                    detail = "states",
                    scopeLabel = Countries.name(snap.selection.country),
                    fraction = (r.regionCoveragePct / 100.0).toFloat(),
                    accent = Palette.Verified,
                    onClick = { model.openTracker(Scope.COUNTRY) },
                    onShare = { model.shareCard(Scope.COUNTRY) },
                    onScope = { model.go(Route.COUNTRY_PICKER) },
                )
                TrackerDial(
                    diameter = 132,
                    value = "$stateDone/$stateTotal",
                    detail = "places",
                    scopeLabel = model.regionName,
                    fraction = if (stateTotal == 0) 0f else stateDone.toFloat() / stateTotal,
                    accent = if (stateCode in r.regionsComplete) Palette.Verified else Palette.Pending,
                    onClick = { model.openTracker(Scope.STATE) },
                    onShare = { model.shareCard(Scope.STATE) },
                    onScope = { model.go(Route.STATE_PICKER) },
                )
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionTitle("Why these numbers", "canon v${r.canonVersion}")
                        Text(
                            "Nothing above is stored. Every dial is a fold of your " +
                                "${snap.events.size}-event ledger against canon v${r.canonVersion}, " +
                                "recomputed from zero each time this screen draws.",
                            fontSize = 12.sp,
                            color = Palette.Muted,
                        )
                        Box(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("${r.placesCredited.size} verified", Palette.Verified)
                            if (r.placesClaimed.isNotEmpty()) {
                                Pill("${r.placesClaimed.size} claimed", Palette.Pending)
                            }
                            Pill(
                                "${r.placesDenominator - r.placesCredited.size - r.placesClaimed.size} to go",
                                Palette.Muted,
                            )
                        }
                        if (r.placesClaimed.isNotEmpty()) {
                            Box(modifier = Modifier.height(8.dp))
                            Text(
                                "Claimed places came from imported photos. They are real history, " +
                                    "but only a live capture earns the percentage above.",
                                fontSize = 11.sp,
                                color = Palette.Muted,
                            )
                        }
                    }
                }
            }
        }

        item { ShareRow { model.shareCurrent() } }
    }
}
