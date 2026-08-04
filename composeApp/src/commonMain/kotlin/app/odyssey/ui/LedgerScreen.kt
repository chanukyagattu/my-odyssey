package app.odyssey.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.odyssey.AppModel
import app.odyssey.engine.Codec
import app.odyssey.engine.Evidence
import app.odyssey.engine.EvidenceUpgraded
import app.odyssey.engine.LedgerEvent
import app.odyssey.engine.MediaAttached
import app.odyssey.engine.MediaDetached
import app.odyssey.engine.VisitRecorded
import app.odyssey.engine.VisitRevoked
import app.odyssey.engine.formatBytes
import app.odyssey.engine.formatDate

@Composable
fun LedgerScreen(model: AppModel) {
    val snap = model.snapshot
    val revoked = snap.events.filterIsInstance<VisitRevoked>().map { it.refEventId }.toSet()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                "Ledger",
                "The system of record. Append-only, replayable, and the only thing that is stored.",
            )
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionTitle("Fold output", "canon v${snap.result.canonVersion}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("${snap.events.size} events", Palette.Muted)
                        Pill("${snap.result.placesCredited.size}/${snap.result.placesDenominator} places", Palette.Verified)
                        Pill("${snap.regionsHereComplete}/${snap.regionsHereTotal} ${snap.regionNoun}", Palette.Pending)
                    }
                    Box(modifier = Modifier.height(12.dp))
                    Text(
                        "Corrections are compensating events. A revoked visit is not " +
                            "deleted — it stays in the log with a revocation appended after it, " +
                            "so the history remains auditable and the fold still replays.",
                        fontSize = 12.sp,
                        color = Palette.Muted,
                    )
                }
            }
        }

        item { LibraryPanel(model) }

        if (snap.events.isEmpty()) {
            item {
                Card {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("The log is empty.", color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Every number in the app is currently a fold over zero events.",
                            color = Palette.Muted,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        } else {
            item { SectionTitle("Raw log", "oldest first") }

            itemsIndexed(snap.events) { index, event ->
                EventRow(model, index, event, isRevoked = event.eventId in revoked || refOf(event) in revoked)
            }
        }
    }
}

private fun refOf(event: LedgerEvent): String = when (event) {
    is VisitRecorded -> event.eventId
    is VisitRevoked -> event.refEventId
    is EvidenceUpgraded -> event.refEventId
    is MediaAttached -> event.refEventId
    is MediaDetached -> event.refEventId
}

@Composable
private fun LibraryPanel(model: AppModel) {
    val stats = model.libraryStats()
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitle("Media library", "on this device only")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("${stats.itemCount} items", Palette.Muted)
                Pill(stats.humanSize, Palette.Verified)
                if (stats.missingBlobs > 0) Pill("${stats.missingBlobs} missing", Palette.Danger)
            }
            Box(modifier = Modifier.height(10.dp))
            Text(
                "${stats.photoCount} photos · ${stats.videoCount} videos. Copied into this " +
                    "app's private container, content-addressed by SHA-256. There is no " +
                    "upload path anywhere in the codebase.",
                fontSize = 12.sp,
                color = Palette.Muted,
            )
            Box(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (stats.excludedFromBackup) "Excluded from device backup" else "In your iCloud device backup",
                        fontSize = 13.sp,
                        color = Palette.Text,
                    )
                    Text(
                        if (stats.excludedFromBackup) {
                            "Saves your iCloud quota. Losing this phone loses the library."
                        } else {
                            "Your storage, never ours."
                        },
                        fontSize = 11.sp,
                        color = Palette.Muted,
                    )
                }
                GhostButton(if (stats.excludedFromBackup) "Include" else "Exclude") {
                    model.toggleBackupExclusion()
                }
            }
        }
    }
}

@Composable
private fun EventRow(model: AppModel, index: Int, event: LedgerEvent, isRevoked: Boolean) {
    val snap = model.snapshot
    Card {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "#${index + 1}",
                    fontSize = 11.sp,
                    color = Palette.Muted,
                    fontFamily = FontFamily.Monospace,
                )
                when (event) {
                    is VisitRecorded -> Pill("VisitRecorded", Palette.Verified)
                    is VisitRevoked -> Pill("VisitRevoked", Palette.Danger)
                    is EvidenceUpgraded -> Pill("EvidenceUpgraded", Palette.Pending)
                    is MediaAttached -> Pill("MediaAttached", Palette.Muted)
                    is MediaDetached -> Pill("MediaDetached", Palette.Danger)
                }
                if (isRevoked && event is VisitRecorded) Pill("revoked", Palette.Danger)
            }

            Box(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Palette.Ink)
                    .padding(10.dp),
            ) {
                Text(
                    Codec.encode(event),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Palette.Muted,
                )
            }

            if (event is VisitRecorded) {
                val entry = snap.canon.byId[event.placeId]
                Box(modifier = Modifier.height(8.dp))
                Text(
                    "${entry?.name ?: event.placeId} · ${event.dwellSeconds.asDuration()} · " +
                        snap.effectiveEvidence(event).label,
                    fontSize = 12.sp,
                    color = Palette.Text,
                )

                if (!isRevoked) {
                    Box(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val current = snap.effectiveEvidence(event)
                        if (current < Evidence.GPS_VERIFIED) {
                            val next = Evidence.entries[current.ordinal + 1]
                            GhostButton("Upgrade → ${next.label}") { model.upgrade(event.eventId, next) }
                        }
                        GhostButton("Revoke") { model.revoke(event.eventId) }
                    }
                }
            }

            if (event is VisitRevoked) {
                Box(modifier = Modifier.height(6.dp))
                Text("reason: ${event.reason}", fontSize = 12.sp, color = Palette.Muted)
            }

            if (event is MediaAttached) {
                Box(modifier = Modifier.height(8.dp))
                val present = model.repo.mediaExists(event.mediaId)
                Text(
                    "${event.kind.name.lowercase()} · ${formatBytes(event.byteSize)} · " +
                        if (present) "on device" else "bytes reclaimed (tombstone)",
                    fontSize = 12.sp,
                    color = if (present) Palette.Text else Palette.Pending,
                )
                event.exifGps?.let {
                    Text(
                        "EXIF ${it.lat} , ${it.lng}" +
                            (event.exifUtcEpochSeconds?.let { t -> " · ${formatDate(t)} UTC" } ?: ""),
                        fontSize = 11.sp,
                        color = Palette.Muted,
                    )
                }
                if (!snap.events.filterIsInstance<MediaDetached>().any { it.refEventId == event.eventId }) {
                    Box(modifier = Modifier.height(10.dp))
                    GhostButton("Detach and reclaim") { model.detachMedia(event.eventId) }
                }
            }
        }
    }
}
