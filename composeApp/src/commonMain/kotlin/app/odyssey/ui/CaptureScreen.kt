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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.odyssey.AppModel
import app.odyssey.engine.CanonEntry
import app.odyssey.engine.BACKFILL_MIN_PHOTOS
import app.odyssey.engine.Evidence
import app.odyssey.engine.LatLng
import app.odyssey.engine.formatBytes

private val DWELL_CHOICES = listOf(15L * 60, 45L * 60, 90L * 60, 180L * 60)

@Composable
fun CaptureScreen(model: AppModel, entry: CanonEntry, modifier: Modifier = Modifier) {
    var dwell by remember(entry.placeId) {
        mutableStateOf(DWELL_CHOICES.firstOrNull { it >= entry.minDwellSeconds } ?: entry.minDwellSeconds)
    }

    val distance = model.distanceTo(entry)
    val inside = model.insideGeofence(entry)
    val evidence = model.evidenceFor(entry)
    val meetsDwell = dwell >= entry.minDwellSeconds
    val wouldCredit = evidence.counts && meetsDwell

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostButton("← Back") { model.closeCapture() }
            }
        }

        item {
            Column {
                Text(entry.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                Text(
                    "${model.snapshot.canon.regionName(entry.regionCode)} · ${entry.placeId}",
                    fontSize = 12.sp,
                    color = Palette.Muted,
                )
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionTitle("Position", if (model.location.isSimulated) "simulated" else "CoreLocation")
                    val fix = model.location.fix
                    if (fix == null) {
                        Text("No fix yet.", color = Palette.Pending, fontSize = 14.sp)
                        Text(
                            "Without a fix this capture can only be self-reported, " +
                                "and self-reports never move a percentage.",
                            color = Palette.Muted,
                            fontSize = 12.sp,
                        )
                    } else {
                        KeyValue("Fix", "${fix.lat.trim5()}, ${fix.lng.trim5()}")
                        KeyValue("Accuracy", model.location.accuracyMeters?.asDistance() ?: "unknown")
                        KeyValue("Distance to centroid", distance?.asDistance() ?: "—")
                        KeyValue("Geofence", entry.geofenceRadiusMeters.asDistance())
                    }
                    Box(modifier = Modifier.height(12.dp))
                    Pill(
                        if (inside) "Inside geofence" else "Outside geofence",
                        if (inside) Palette.Verified else Palette.Danger,
                    )
                    Text(
                        model.location.status,
                        fontSize = 11.sp,
                        color = Palette.Muted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        if (model.location.isSimulated) {
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionTitle("Simulator controls", "dev only")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton("Stand at the place") { model.teleport(entry.centroid) }
                            GhostButton("Stand 40 km away") {
                                model.teleport(LatLng(entry.centroid.lat + 0.36, entry.centroid.lng))
                            }
                        }
                    }
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionTitle("Dwell", "floor ${entry.minDwellSeconds.asDuration()}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        DWELL_CHOICES.forEach { choice ->
                            Chip(
                                text = choice.asDuration(),
                                selected = choice == dwell,
                                ok = choice >= entry.minDwellSeconds,
                                modifier = Modifier.weight(1f),
                            ) { dwell = choice }
                        }
                    }
                    if (!meetsDwell) {
                        Box(modifier = Modifier.height(10.dp))
                        Text(
                            "Under the floor. The visit still gets written to the ledger — " +
                                "it just will not be credited. Drive-bys do not score.",
                            fontSize = 12.sp,
                            color = Palette.Pending,
                        )
                    }
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionTitle("Photos", "stays on this device")
                    Text(
                        "Photos are copied into this app's own library on your phone. " +
                            "They are never uploaded — there is no server to upload them to.",
                        fontSize = 12.sp,
                        color = Palette.Muted,
                    )
                    Box(modifier = Modifier.height(12.dp))

                    model.staged.forEach { item ->
                        val corroborates = model.stagedCorroborates(item, entry, dwell)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${item.mediaId.take(12)}… · ${formatBytes(item.bytes.size.toLong())}",
                                    fontSize = 12.sp,
                                    color = Palette.Text,
                                )
                                Text(
                                    when {
                                        item.exif.gps == null -> "No embedded location — will not verify"
                                        item.exif.utcEpochSeconds == null -> "No UTC timestamp — will not verify"
                                        corroborates -> "EXIF lands inside the geofence and window"
                                        else -> "EXIF does not match this visit"
                                    },
                                    fontSize = 11.sp,
                                    color = if (corroborates) Palette.Verified else Palette.Muted,
                                )
                            }
                            GhostButton("Remove") { model.unstage(item) }
                        }
                    }

                    GhostButton("Add ${model.mediaSource.label}") { model.stagePhoto(entry) }

                    if (model.staged.any { model.stagedCorroborates(it, entry, dwell) } &&
                        evidence == Evidence.SELF_REPORTED
                    ) {
                        Box(modifier = Modifier.height(10.dp))
                        Text(
                            "A corroborating photo will append an evidence upgrade to " +
                                "PHOTO_VERIFIED right after the visit — enough to count, " +
                                "though still below a live GPS fix.",
                            fontSize = 12.sp,
                            color = Palette.Pending,
                        )
                    }
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionTitle("What this will append")
                    KeyValue("Event", "VisitRecorded")
                    KeyValue("Evidence", evidence.label)
                    KeyValue("Dwell", dwell.asDuration())
                    if (model.staged.isNotEmpty()) {
                        KeyValue("Media attached", "${model.staged.size} (on device only)")
                    }
                    KeyValue("Counts toward %", if (wouldCredit) "yes" else "no")
                    Box(modifier = Modifier.height(12.dp))
                    Text(
                        if (evidence == Evidence.GPS_VERIFIED) {
                            "A GPS fix inside the geofence is the strongest evidence tier. " +
                                "You can never downgrade it later, only upgrade."
                        } else {
                            "No verified fix, so this is recorded as self-reported. " +
                                "It will show in your timeline and stay out of the numbers."
                        },
                        fontSize = 12.sp,
                        color = Palette.Muted,
                    )
                }
            }
        }

        item {
            PrimaryButton(
                text = if (wouldCredit) "Record verified visit" else "Record uncredited visit",
                accent = if (wouldCredit) Palette.Verified else Palette.Pending,
            ) { model.record(entry, dwell) }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionTitle("Been here before?", "claimed, not verified")
                    Text(
                        "A single photo cannot show you were somewhere — anyone can be sent " +
                            "one. Add at least $BACKFILL_MIN_PHOTOS from the visit, spread over " +
                            "${entry.minDwellSeconds / 60} minutes and taken from different spots.",
                        fontSize = 12.sp,
                        color = Palette.Muted,
                    )
                    Box(modifier = Modifier.height(10.dp))
                    val needed = model.photosStillNeeded()
                    Text(
                        if (needed > 0) {
                            "$needed more photo(s) needed."
                        } else {
                            "${model.staged.size} photos staged."
                        },
                        fontSize = 12.sp,
                        color = if (needed > 0) Palette.Pending else Palette.Verified,
                    )
                    Box(modifier = Modifier.height(12.dp))
                    PrimaryButton(
                        text = "Claim as a past visit",
                        enabled = needed == 0,
                        accent = Palette.Pending,
                    ) { model.claimPast(entry) }
                    Box(modifier = Modifier.height(10.dp))
                    Text(
                        "Claims are counted separately and can never complete a state. " +
                            "Only a live capture earns the headline percentage.",
                        fontSize = 11.sp,
                        color = Palette.Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, fontSize = 13.sp, color = Palette.Muted)
        Text(value, fontSize = 13.sp, color = Palette.Text, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    ok: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = if (ok) Palette.Verified else Palette.Pending
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else Palette.SurfaceHigh)
            .border(1.dp, if (selected) accent else Palette.Line, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
    ) {
        Text(text, fontSize = 12.sp, color = if (selected) accent else Palette.Muted)
    }
}

private fun Double.trim5(): String {
    val scaled = kotlin.math.round(this * 100_000) / 100_000.0
    return scaled.toString()
}
