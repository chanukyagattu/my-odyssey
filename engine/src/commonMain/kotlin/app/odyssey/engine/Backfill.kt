package app.odyssey.engine

/**
 * Claiming a place you visited before you had the app.
 *
 * The honest starting point: **a photo proves a camera was there, not that you
 * were.** EXIF is user-writable and an AirDropped original keeps its
 * coordinates intact, so any rule built on a single file is forgeable by
 * anyone with a friend who has been there.
 *
 * Two responses, and the second matters more than the first.
 *
 * 1. Demand a *pattern* rather than an artefact. A real visit leaves several
 *    photos spread across the dwell period, taken from positions that move as
 *    the person walks. One donated image cannot produce that; assembling a
 *    convincing set means obtaining someone's whole afternoon.
 *
 * 2. Refuse to let it count the same. A backfilled claim earns [Evidence.IMPORT_VERIFIED],
 *    which the fold keeps out of the headline percentage entirely. A forged
 *    claim therefore inflates a number nobody is impressed by — which is what
 *    actually removes the incentive, because no evidence rule ever closes the
 *    gap completely.
 */
data class PhotoEvidence(
    val mediaId: String,
    val gps: LatLng?,
    val utcEpochSeconds: Long?,
    val byteSize: Long,
)

sealed interface BackfillCheck {
    data class Accepted(
        val startEpochSec: Long,
        val endEpochSec: Long,
        val photos: List<PhotoEvidence>,
        val spreadMeters: Double,
    ) : BackfillCheck

    data class Rejected(val reason: String) : BackfillCheck

    val reasonOrNull: String? get() = (this as? Rejected)?.reason
}

/** Three is enough to establish a pattern without being a chore for honest users. */
const val BACKFILL_MIN_PHOTOS = 3

/** You cannot stand perfectly still for an hour. Two shots must be this far apart. */
const val BACKFILL_MIN_SPREAD_METERS = 25.0

/** A visit is an afternoon, not a season. Guards against stitching separate trips together. */
const val BACKFILL_MAX_SPAN_SECONDS = 24L * 3600L

fun checkBackfill(
    entry: CanonEntry,
    photos: List<PhotoEvidence>,
    minPhotos: Int = BACKFILL_MIN_PHOTOS,
    minSpreadMeters: Double = BACKFILL_MIN_SPREAD_METERS,
): BackfillCheck {
    if (photos.size < minPhotos) {
        return BackfillCheck.Rejected(
            "Add at least $minPhotos photos from the visit — one photo cannot show you were there.",
        )
    }

    val dated = photos.filter { it.utcEpochSeconds != null && it.gps != null }
    if (dated.size < minPhotos) {
        return BackfillCheck.Rejected(
            "${photos.size - dated.size} of these have no embedded location or UTC time. " +
                "Screenshots and re-saved images usually lose it.",
        )
    }

    val outside = dated.filter { haversineMeters(it.gps!!, entry.centroid) > entry.geofenceRadiusMeters }
    if (outside.isNotEmpty()) {
        return BackfillCheck.Rejected(
            "${outside.size} photo(s) were taken outside ${entry.name}.",
        )
    }

    val times = dated.map { it.utcEpochSeconds!! }
    val start = times.min()
    val end = times.max()
    val span = end - start

    if (span < entry.minDwellSeconds) {
        return BackfillCheck.Rejected(
            "These span ${span / 60} minutes. ${entry.name} needs at least " +
                "${entry.minDwellSeconds / 60} minutes between the first and last photo.",
        )
    }
    if (span > BACKFILL_MAX_SPAN_SECONDS) {
        return BackfillCheck.Rejected(
            "These span more than a day, so they are not one visit. Claim each visit separately.",
        )
    }

    // Movement. Someone who was actually there walked around; a set of photos
    // taken from one spot within one second is a tripod or a forgery.
    var spread = 0.0
    for (i in dated.indices) {
        for (j in i + 1 until dated.size) {
            val d = haversineMeters(dated[i].gps!!, dated[j].gps!!)
            if (d > spread) spread = d
        }
    }
    if (spread < minSpreadMeters) {
        return BackfillCheck.Rejected(
            "Every photo was taken from the same spot. A visit should show some movement.",
        )
    }

    return BackfillCheck.Accepted(
        startEpochSec = start,
        endEpochSec = end,
        photos = dated,
        spreadMeters = spread,
    )
}
