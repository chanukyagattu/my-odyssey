package app.odyssey.engine

import kotlin.math.round

enum class MediaKind { PHOTO, VIDEO }

/**
 * Media lives on the device, in this app's own container. It is never uploaded.
 *
 * The bytes are **not** in the ledger — only a content address is. That keeps
 * the log small, replayable and diffable while the blobs sit in a sidecar store
 * that can be verified byte-for-byte against its own filename.
 *
 * Coordinates are stored as E7 integers (degrees x 10^7, ~1 cm) rather than
 * doubles so the on-disk log round-trips exactly on every platform. A float
 * whose text form differs between Kotlin/JVM and Kotlin/Native would mean two
 * devices disagreeing about the same history.
 */
data class MediaAttached(
    override val eventId: String,
    /** The VisitRecorded this media belongs to. */
    val refEventId: String,
    /** sha256 of the bytes, lowercase hex. */
    val mediaId: String,
    val kind: MediaKind,
    val byteSize: Long,
    val exifLatE7: Long? = null,
    val exifLngE7: Long? = null,
    /** UTC, from the EXIF GPS datestamp only. Null means "not verifiable". */
    val exifUtcEpochSeconds: Long? = null,
) : LedgerEvent {
    val exifGps: LatLng?
        get() = if (exifLatE7 != null && exifLngE7 != null) {
            LatLng(exifLatE7 / 1e7, exifLngE7 / 1e7)
        } else {
            null
        }
}

/** Compensating event. The blob may be reclaimed; the fact that it existed is not erased. */
data class MediaDetached(
    override val eventId: String,
    /** The MediaAttached eventId. */
    val refEventId: String,
    val reason: String,
) : LedgerEvent

fun Double.toE7(): Long = round(this * 1e7).toLong()

/** Fifteen minutes: enough for a slow shutter or a clock drifting, not enough to fake a trip. */
const val PHOTO_EVIDENCE_TOLERANCE_SECONDS = 900L

/**
 * Does this photo corroborate this visit?
 *
 * Requires embedded coordinates inside the place's geofence AND a UTC instant
 * inside the visit window. Anything missing means no upgrade: verification
 * fails closed, because a photo with no metadata is indistinguishable from a
 * photo taken off the internet.
 */
fun photoCorroborates(
    media: MediaAttached,
    visit: VisitRecorded,
    entry: CanonEntry,
    toleranceSeconds: Long = PHOTO_EVIDENCE_TOLERANCE_SECONDS,
): Boolean {
    if (media.kind != MediaKind.PHOTO) return false
    if (media.refEventId != visit.eventId) return false
    val gps = media.exifGps ?: return false
    val taken = media.exifUtcEpochSeconds ?: return false
    if (haversineMeters(gps, entry.centroid) > entry.geofenceRadiusMeters) return false
    return taken >= visit.startEpochSec - toleranceSeconds &&
        taken <= visit.endEpochSec + toleranceSeconds
}

/** What the library screen reports. Derived from the log, cross-checked against the blob store. */
data class LibraryStats(
    val itemCount: Int,
    val photoCount: Int,
    val videoCount: Int,
    val ledgerBytes: Long,
    val onDiskBytes: Long,
    val missingBlobs: Int,
    val excludedFromBackup: Boolean,
) {
    val humanSize: String get() = formatBytes(onDiskBytes)
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_048_576 -> "${bytes / 1_024} KB"
    bytes < 1_073_741_824 -> {
        val tenths = (bytes * 10 / 1_048_576)
        "${tenths / 10}.${tenths % 10} MB"
    }
    else -> {
        val tenths = (bytes * 10 / 1_073_741_824)
        "${tenths / 10}.${tenths % 10} GB"
    }
}
