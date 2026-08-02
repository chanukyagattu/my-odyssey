package app.odyssey

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.odyssey.engine.AppSnapshot
import app.odyssey.engine.CanonEntry
import app.odyssey.engine.Evidence
import app.odyssey.engine.ExifData
import app.odyssey.engine.ExploreGroup
import app.odyssey.engine.IngestResult
import app.odyssey.engine.LatLng
import app.odyssey.engine.LibraryStats
import app.odyssey.engine.MediaAttached
import app.odyssey.engine.MediaKind
import app.odyssey.engine.MemoryItem
import app.odyssey.engine.OdysseyRepository
import app.odyssey.engine.Scope
import app.odyssey.engine.VisitRecorded
import app.odyssey.engine.exploreGroups
import app.odyssey.engine.haversineMeters
import app.odyssey.engine.mediaIdOf
import app.odyssey.engine.memories
import app.odyssey.engine.nowEpochSeconds
import app.odyssey.engine.parseJpegExif
import app.odyssey.engine.photoCorroborates
import app.odyssey.engine.toE7

enum class Tab { HOME, TRACKER, TIMELINE, LEDGER }

/** The two tabs on P7 / P8 / P9. They partition the canon: what you did, what is left. */
enum class TimelineTab { MEMORIES, EXPLORE }

/**
 * A plain state holder, not a framework ViewModel — the app has no async work
 * to own. Every mutation goes through the repository and then replaces the
 * snapshot wholesale, so the UI can never observe a half-applied write.
 */
/** A photo chosen but not yet committed — it lands in the ledger with the visit. */
data class StagedMedia(
    val bytes: ByteArray,
    val kind: MediaKind,
    val exif: ExifData,
) {
    val mediaId: String get() = mediaIdOf(bytes)

    override fun equals(other: Any?): Boolean = other is StagedMedia && other.mediaId == mediaId

    override fun hashCode(): Int = mediaId.hashCode()
}

class AppModel(
    val repo: OdysseyRepository,
    val location: LocationSource,
    val mediaSource: MediaSource,
) {
    var snapshot: AppSnapshot by mutableStateOf(repo.snapshot())
        private set

    var tab: Tab by mutableStateOf(Tab.HOME)

    var timelineTab: TimelineTab by mutableStateOf(TimelineTab.MEMORIES)

    /**
     * P7 / P8 / P9 Explore. Read-only by design — this list never writes
     * selection, so W/C/S keeps exactly one owner in P3.
     */
    fun explore(): List<ExploreGroup> = exploreGroups(
        canon = snapshot.canon,
        result = snapshot.result,
        scope = snapshot.selection.scope,
        selectedCountry = snapshot.selection.country,
        selectedState = snapshot.selection.usState,
        fix = location.fix,
    )

    fun memoryFeed(): List<MemoryItem> = memories(
        snapshot = snapshot,
        scope = snapshot.selection.scope,
        selectedCountry = snapshot.selection.country,
        selectedState = snapshot.selection.usState,
    )

    /** Non-null when the capture screen is open for a place. */
    var capturing: CanonEntry? by mutableStateOf(null)

    var toast: String? by mutableStateOf(null)

    private fun refresh() {
        snapshot = repo.snapshot()
    }

    fun setScope(scope: Scope) {
        snapshot = repo.select(scope)
    }

    fun selectState(usState: String) {
        snapshot = repo.selectState(usState)
    }

    fun openCapture(entry: CanonEntry) {
        capturing = entry
        toast = null
        staged = emptyList()
        if (location.isSimulated) location.teleportTo(entry.centroid)
    }

    fun closeCapture() {
        capturing = null
        staged = emptyList()
    }

    /** Distance from the current fix to a place, or null if we have no fix. */
    fun distanceTo(entry: CanonEntry): Double? =
        location.fix?.let { haversineMeters(it, entry.centroid) }

    fun insideGeofence(entry: CanonEntry): Boolean =
        distanceTo(entry)?.let { it <= entry.geofenceRadiusMeters } == true

    /**
     * The evidence a capture would earn right now. GPS credit requires a fix
     * inside the geofence — anything else is self-reported and will render in
     * the timeline without moving a percentage.
     */
    fun evidenceFor(entry: CanonEntry): Evidence =
        if (insideGeofence(entry)) Evidence.GPS_VERIFIED else Evidence.SELF_REPORTED

    // ---------- media ----------

    var staged: List<StagedMedia> by mutableStateOf(emptyList())
        private set

    /** Pulls a photo in from the device. Nothing here can send one out. */
    fun stagePhoto(entry: CanonEntry) {
        mediaSource.pick(
            kind = MediaKind.PHOTO,
            near = location.fix ?: entry.centroid,
            atEpochSeconds = nowEpochSeconds(),
        ) { bytes ->
            if (bytes == null) {
                toast = "No photo selected."
            } else {
                val exif = parseJpegExif(bytes)
                val item = StagedMedia(bytes, MediaKind.PHOTO, exif)
                staged = if (staged.any { it.mediaId == item.mediaId }) staged else staged + item
            }
        }
    }

    fun unstage(item: StagedMedia) {
        staged = staged.filterNot { it.mediaId == item.mediaId }
    }

    fun clearStaged() {
        staged = emptyList()
    }

    /** Would this staged photo corroborate a visit of [dwellSeconds] ending now? */
    fun stagedCorroborates(item: StagedMedia, entry: CanonEntry, dwellSeconds: Long): Boolean {
        val end = nowEpochSeconds()
        val probeVisit = VisitRecorded(
            eventId = "probe",
            userId = repo.userId,
            placeId = entry.placeId,
            startEpochSec = end - dwellSeconds,
            endEpochSec = end,
            evidence = Evidence.SELF_REPORTED,
        )
        val probeMedia = MediaAttached(
            eventId = "probe-m",
            refEventId = "probe",
            mediaId = item.mediaId,
            kind = item.kind,
            byteSize = item.bytes.size.toLong(),
            exifLatE7 = item.exif.gps?.lat?.toE7(),
            exifLngE7 = item.exif.gps?.lng?.toE7(),
            exifUtcEpochSeconds = item.exif.utcEpochSeconds,
        )
        return photoCorroborates(probeMedia, probeVisit, entry)
    }

    fun libraryStats(): LibraryStats = repo.libraryStats()

    fun toggleBackupExclusion() {
        val now = repo.libraryStats().excludedFromBackup
        repo.setExcludedFromBackup(!now)
        refresh()
        toast = if (!now) {
            "Media excluded from your device backup. Losing this phone loses the library."
        } else {
            "Media included in your own iCloud device backup. It still never touches our servers."
        }
    }

    fun detachMedia(attachmentEventId: String) {
        val r = repo.detachMedia(attachmentEventId)
        refresh()
        toast = r.message ?: "Detached. The attachment event stays in the log; the bytes are reclaimed."
    }

    fun record(entry: CanonEntry, dwellSeconds: Long) {
        val end = nowEpochSeconds()
        val outcome = repo.recordVisit(
            placeId = entry.placeId,
            startEpochSec = end - dwellSeconds,
            endEpochSec = end,
            evidence = evidenceFor(entry),
        )
        val result = outcome.result
        outcome.visitEventId?.let { visitId ->
            staged.forEach { item -> repo.attachMedia(visitId, item.bytes, item.kind) }
        }
        refresh()
        toast = when (result) {
            is IngestResult.Accepted -> {
                val credited = snapshot.result.isCredited(entry.placeId)
                if (credited) {
                    "Credited — ${entry.name}"
                } else {
                    "Recorded, not credited: " +
                        if (evidenceFor(entry) == Evidence.SELF_REPORTED) {
                            "no GPS fix inside the geofence"
                        } else {
                            "under the ${entry.minDwellSeconds / 60}-minute dwell floor"
                        }
                }
            }

            is IngestResult.DuplicateNoOp -> "Already in the ledger — no-op"
            is IngestResult.Rejected -> "Rejected: ${result.reason}"
        }
        if (result.isAccepted) {
            clearStaged()
            capturing = null
        }
    }

    fun revoke(eventId: String) {
        val r = repo.revokeVisit(eventId, "revoked by user")
        refresh()
        toast = r.message ?: "Revoked — the original event is still in the log"
    }

    fun upgrade(eventId: String, to: Evidence) {
        val r = repo.upgradeEvidence(eventId, to)
        refresh()
        toast = r.message ?: "Evidence upgraded to ${to.label}"
    }

    fun teleport(target: LatLng) {
        location.teleportTo(target)
    }
}
