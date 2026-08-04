package app.odyssey.engine

/**
 * Milestone 1, arriving early and fed by photographs.
 *
 * Smartphones have been writing coordinates and timestamps into photos since
 * about 2010, so a new user's library is already a sixteen-year location trace.
 * Claiming places one at a time from that is unusable — the interesting
 * operation is a scan: read the library's *metadata index*, cluster it into
 * stay-points, and propose the visits it implies.
 *
 * This never touches pixels. Only `location`, `creationDate` and whether the
 * asset was captured by this device. That is what makes the permission ask
 * defensible and what makes scanning forty thousand assets fast.
 *
 * The device-capture flag does the heavy lifting on trust: a photo the camera
 * took is evidence, a photo that arrived from someone else is a claim. It is
 * the strongest anti-donation signal available on iOS, and it is exactly what a
 * bulk import needs.
 */
data class AssetPoint(
    val assetId: String,
    val location: LatLng?,
    val epochSeconds: Long,
    /** PHAsset.sourceType == typicalUserInitiated: this camera took it. */
    val capturedByThisDevice: Boolean,
    val horizontalAccuracyMeters: Double? = null,
)

enum class ProposalStrength {
    /** Counts toward the headline. */
    VERIFIED,

    /** Real history, weaker provenance. Counted separately, never completes a state. */
    CLAIM,
}

/** Why a proposal was graded the way it was. Shown to the user verbatim. */
enum class ProposalBasis {
    /** Enough of your own camera's photos at the place, patterned. */
    OWN_CAPTURE,

    /**
     * The photos came from someone else's camera, but your own put you in the
     * region that day. A partner's photos of a viewpoint you both stood at.
     */
    CORROBORATED,

    /** Nothing of yours establishes you were there. Could be anyone's afternoon. */
    UNCORROBORATED,
}

data class ProposedVisit(
    val placeId: String,
    val startEpochSec: Long,
    val endEpochSec: Long,
    val assetIds: List<String>,
    val basis: ProposalBasis,
    val spreadMeters: Double,
    val ownPhotoCount: Int,
) {
    val photoCount: Int get() = assetIds.size

    val strength: ProposalStrength
        get() = if (basis == ProposalBasis.UNCORROBORATED) ProposalStrength.CLAIM else ProposalStrength.VERIFIED

    val evidence: Evidence
        get() = if (strength == ProposalStrength.VERIFIED) Evidence.PHOTO_VERIFIED else Evidence.IMPORT_VERIFIED

    val explanation: String
        get() = when (basis) {
            ProposalBasis.OWN_CAPTURE -> "$ownPhotoCount photos from your camera, here"
            ProposalBasis.CORROBORATED ->
                if (ownPhotoCount > 0) {
                    "Your camera was here, and these photos cover the visit"
                } else {
                    "Someone else's photos — but your camera puts you nearby that day"
                }

            ProposalBasis.UNCORROBORATED ->
                if (ownPhotoCount > 0) "Too brief to count as a visit" else "Not your camera, and nothing of yours nearby"
        }
}

/** A gap longer than this ends one visit and begins another. */
const val SEGMENT_MAX_GAP_SECONDS = 6L * 3600L

/**
 * Photos from before roughly 2015 are often wildly imprecise. A fix this bad
 * says nothing about which side of a geofence you were on.
 */
const val SEGMENT_MAX_ACCURACY_METERS = 500.0

/** Two device-captured photos are the minimum pattern worth calling verified. */
const val SEGMENT_MIN_VERIFIED_PHOTOS = 2

/**
 * How far from a place your own camera can be and still corroborate someone
 * else's photos of it. Wide enough to cover the hotel, the car park and the
 * town you ate in; far too narrow to cover a different trip.
 */
const val CORROBORATION_RADIUS_METERS = 50_000.0

/** And how far either side in time. A day trip's photos bracket the day. */
const val CORROBORATION_WINDOW_SECONDS = 24L * 3600L

/**
 * Groups [assets] into the visits they imply.
 *
 * Deliberately produces *proposals* rather than ledger events: the clustering
 * runs before ingest so the plausibility invariants judge finished visits
 * rather than raw points. Feeding individual photos into the ledger would trip
 * the teleport check on any drive between two places.
 */
fun segmentLibrary(
    assets: List<AssetPoint>,
    canon: CanonRelease,
    maxGapSeconds: Long = SEGMENT_MAX_GAP_SECONDS,
): List<ProposedVisit> {
    val active = canon.active()
    if (active.isEmpty()) return emptyList()

    // Metadata only, usable fixes only, in time order.
    val usable = assets
        .filter { it.location != null }
        .filter { (it.horizontalAccuracyMeters ?: 0.0) <= SEGMENT_MAX_ACCURACY_METERS }
        .sortedBy { it.epochSeconds }

    // Attribute each point to the nearest canon place whose geofence contains
    // it. Nearest matters where two geofences overlap.
    data class Attributed(val point: AssetPoint, val entry: CanonEntry, val distance: Double)

    val attributed = usable.mapNotNull { point ->
        val here = point.location!!
        active
            .map { it to haversineMeters(here, it.centroid) }
            .filter { (entry, d) -> d <= entry.geofenceRadiusMeters }
            .minByOrNull { it.second }
            ?.let { (entry, d) -> Attributed(point, entry, d) }
    }

    val proposals = mutableListOf<ProposedVisit>()
    var bucket = mutableListOf<Attributed>()

    // Every fix this device took, for corroborating other people's photos.
    val ownTrace = usable.filter { it.capturedByThisDevice }

    /**
     * Was *this device* demonstrably in the region around this window?
     *
     * [exclude] is the cluster's own assets: a visit must not corroborate
     * itself, or a single photo would prove its own case.
     */
    fun regionallyCorroborated(
        entry: CanonEntry,
        start: Long,
        end: Long,
        exclude: Set<String>,
    ): Boolean = ownTrace.any {
        it.assetId !in exclude &&
            it.epochSeconds >= start - CORROBORATION_WINDOW_SECONDS &&
            it.epochSeconds <= end + CORROBORATION_WINDOW_SECONDS &&
            haversineMeters(it.location!!, entry.centroid) <= CORROBORATION_RADIUS_METERS
    }

    fun flush() {
        if (bucket.isEmpty()) return
        val entry = bucket.first().entry

        // Evidence comes from your own camera. Other people's photos ride along
        // as media but never establish the pattern themselves.
        val own = bucket.filter { it.point.capturedByThisDevice }
        val times = bucket.map { it.point.epochSeconds }
        val start = times.min()
        val end = times.max()

        fun spreadOf(points: List<Attributed>): Double {
            var max = 0.0
            for (i in points.indices) {
                for (j in i + 1 until points.size) {
                    val d = haversineMeters(points[i].point.location!!, points[j].point.location!!)
                    if (d > max) max = d
                }
            }
            return max
        }

        // Note there is no movement requirement here, unlike the manual claim
        // path. Spread exists to make donated photos hard to assemble; when the
        // camera itself vouches for the capture, two shots ninety minutes apart
        // from one bench are simply someone who sat at a viewpoint.
        val ownTimes = own.map { it.point.epochSeconds }
        val ownPattern = own.size >= SEGMENT_MIN_VERIFIED_PHOTOS &&
            (ownTimes.max() - ownTimes.min()) >= entry.minDwellSeconds

        val ids = bucket.map { it.point.assetId }.toSet()
        val clusterCoversDwell = (end - start) >= entry.minDwellSeconds

        val basis = when {
            ownPattern -> ProposalBasis.OWN_CAPTURE

            // Your camera was here, and the group's photos establish how long
            // the visit lasted. One of your own shots cannot be donated.
            own.isNotEmpty() && clusterCoversDwell -> ProposalBasis.CORROBORATED

            // None of these are yours — but were *you* in the region that day?
            // A partner's photos of a viewpoint you both stood at are real
            // history; a stranger's photos of a place you have never been are
            // not. Your own trace separates them and cannot be borrowed.
            regionallyCorroborated(entry, start, end, ids) -> ProposalBasis.CORROBORATED

            else -> ProposalBasis.UNCORROBORATED
        }

        proposals.add(
            ProposedVisit(
                placeId = entry.placeId,
                startEpochSec = start,
                // A lone photo has no duration. Give the visit the place's dwell
                // floor so it survives the fold, and let its basis — not a
                // fabricated timestamp — be what limits it.
                endEpochSec = if (end > start) end else start + entry.minDwellSeconds,
                assetIds = bucket.map { it.point.assetId },
                basis = basis,
                spreadMeters = spreadOf(bucket),
                ownPhotoCount = own.size,
            ),
        )
        bucket = mutableListOf()
    }

    for (a in attributed) {
        val last = bucket.lastOrNull()
        val newPlace = last != null && last.entry.placeId != a.entry.placeId
        val bigGap = last != null && a.point.epochSeconds - last.point.epochSeconds > maxGapSeconds
        if (newPlace || bigGap) flush()
        bucket.add(a)
    }
    flush()

    return proposals.sortedBy { it.startEpochSec }
}

/** What the scan screen reports before anything is written. */
data class ScanSummary(
    val assetsSeen: Int,
    val assetsWithLocation: Int,
    val proposals: List<ProposedVisit>,
) {
    val verified: List<ProposedVisit> get() = proposals.filter { it.strength == ProposalStrength.VERIFIED }
    val claims: List<ProposedVisit> get() = proposals.filter { it.strength == ProposalStrength.CLAIM }
    val placesTouched: Set<String> get() = proposals.map { it.placeId }.toSet()
    val placesVerifiable: Set<String> get() = verified.map { it.placeId }.toSet()
}

fun summariseScan(assets: List<AssetPoint>, canon: CanonRelease): ScanSummary = ScanSummary(
    assetsSeen = assets.size,
    assetsWithLocation = assets.count { it.location != null },
    proposals = segmentLibrary(assets, canon),
)
