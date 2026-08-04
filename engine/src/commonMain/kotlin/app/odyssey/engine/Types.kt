package app.odyssey.engine

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Canon lifecycle state machine.
 *
 *   PROPOSED -> ACTIVE <-> SUSPENDED -> RETIRED
 *
 * Nothing about a user's completion is stored. When an entry changes state we
 * publish a new canon release and re-fold; there is never a migration.
 */
enum class Lifecycle { PROPOSED, ACTIVE, SUSPENDED, RETIRED }

/**
 * Evidence hierarchy. Ordinal order is load-bearing: comparisons and the
 * monotonic-upgrade rule both rely on it.
 *
 *   SELF_REPORTED < IMPORT_VERIFIED < PHOTO_VERIFIED < GPS_VERIFIED
 *
 * Only evidenced visits (anything above SELF_REPORTED) move a percentage.
 */
enum class Evidence { SELF_REPORTED, IMPORT_VERIFIED, PHOTO_VERIFIED, GPS_VERIFIED;

    val label: String
        get() = when (this) {
            SELF_REPORTED -> "Self-reported"
            IMPORT_VERIFIED -> "Imported"
            PHOTO_VERIFIED -> "Photo"
            GPS_VERIFIED -> "GPS verified"
        }

    val counts: Boolean get() = this != SELF_REPORTED

    /**
     * Earns the headline percentage. Live capture only.
     *
     * A retrospective claim structurally cannot meet the bar the product is
     * built on — "verified at the time" — so it is scored separately rather
     * than being scored generously.
     */
    val isVerified: Boolean get() = this >= PHOTO_VERIFIED

    /** Backfilled: real, shown, counted — just not toward the headline number. */
    val isClaimOnly: Boolean get() = this == IMPORT_VERIFIED
}

data class LatLng(val lat: Double, val lng: Double)

private const val DEG_TO_RAD = PI / 180.0

fun haversineMeters(a: LatLng, b: LatLng): Double {
    val r = 6_371_000.0
    val dLat = (b.lat - a.lat) * DEG_TO_RAD
    val dLng = (b.lng - a.lng) * DEG_TO_RAD
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(a.lat * DEG_TO_RAD) * cos(b.lat * DEG_TO_RAD) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * r * atan2(sqrt(h), sqrt(1 - h))
}

/**
 * A must-go place in the canon. [placeId] is stable forever and never reused.
 * [centroid] + [geofenceRadiusMeters] stand in for the boundary polygon in
 * milestone 0; real point-in-polygon attribution lands with the segmentation
 * engine (M1).
 */
data class CanonEntry(
    val placeId: String,
    /** ISO 3166-2 shaped: `US-UT`, `FR-IDF`, `JP-26`. */
    val regionCode: String,
    val name: String,
    val lifecycle: Lifecycle,
    val centroid: LatLng,
    val minDwellSeconds: Long,
    val geofenceRadiusMeters: Double = 3_000.0,
    /**
     * ISO country code. Defaults to US because canon v1 is US-only; the field
     * exists now so that adding a second country is a data change and not a
     * schema change.
     */
    val country: String = "US",
    /** Display name for [regionCode]: "Utah", "Île-de-France", "Kyoto". */
    val regionName: String = "",
)

/**
 * Immutable canon release. A new version is a full snapshot, never a patch in
 * place. Every user-facing number is a fold of the ledger *against a release*,
 * so the release id is part of the answer.
 */
data class CanonRelease(
    val version: Int,
    val entries: List<CanonEntry>,
) {
    init {
        require(entries.map { it.placeId }.toSet().size == entries.size) {
            "duplicate placeId in canon release $version"
        }
    }

    val byId: Map<String, CanonEntry> = entries.associateBy { it.placeId }

    fun active(): List<CanonEntry> = entries.filter { it.lifecycle == Lifecycle.ACTIVE }

    /**
     * Region names come from the canon in hand, not from a global lookup —
     * a US-only table cannot name Île-de-France, and the fold has to work for
     * whichever release it was handed.
     */
    private val regionNames: Map<String, String> =
        entries.filter { it.regionName.isNotEmpty() }.associate { it.regionCode to it.regionName }

    fun regionName(code: String): String = regionNames[code] ?: code

    /**
     * Regions belonging to one country. Now that the canon spans 46 of them,
     * an unfiltered region list would show French régions under the United
     * States.
     */
    fun regionsIn(country: String): List<String> = entries
        .filter { it.country == country }
        .filter { it.lifecycle == Lifecycle.ACTIVE || it.lifecycle == Lifecycle.SUSPENDED }
        .map { it.regionCode }
        .distinct()
        .sorted()

    fun countriesInPlay(): List<String> = entries
        .filter { it.lifecycle == Lifecycle.ACTIVE || it.lifecycle == Lifecycle.SUSPENDED }
        .map { it.country }
        .distinct()
        .sorted()

    /** States that are part of the game: at least one ACTIVE or SUSPENDED entry. */
    fun regionsInPlay(): List<String> = entries
        .filter { it.lifecycle == Lifecycle.ACTIVE || it.lifecycle == Lifecycle.SUSPENDED }
        .map { it.regionCode }
        .distinct()
        .sorted()

    fun entriesInRegion(regionCode: String): List<CanonEntry> = entries
        .filter { it.regionCode == regionCode }
        .filter { it.lifecycle != Lifecycle.RETIRED }
        .sortedBy { it.name }

    /** Returns a release with [placeIds] moved to [lifecycle], bumping the version. */
    fun withLifecycle(placeIds: Set<String>, lifecycle: Lifecycle): CanonRelease = CanonRelease(
        version = version + 1,
        entries = entries.map { if (it.placeId in placeIds) it.copy(lifecycle = lifecycle) else it },
    )
}

sealed interface LedgerEvent {
    val eventId: String
}

/**
 * Append-only record of a visit. `(userId, deviceId, sourceSeq)` is the
 * idempotency key for at-least-once producers replaying offline dumps.
 */
data class VisitRecorded(
    override val eventId: String,
    val userId: String,
    val placeId: String,
    val startEpochSec: Long,
    val endEpochSec: Long,
    val evidence: Evidence,
    val deviceId: String? = null,
    val sourceSeq: Long? = null,
) : LedgerEvent {
    init {
        require(endEpochSec > startEpochSec) { "visit must have positive dwell" }
    }

    val dwellSeconds: Long get() = endEpochSec - startEpochSec
}

/** Compensating event. The original is never mutated or deleted. */
data class VisitRevoked(
    override val eventId: String,
    val refEventId: String,
    val reason: String,
) : LedgerEvent

/** Evidence may only move up (invariant 2.4: evidence monotonicity). */
data class EvidenceUpgraded(
    override val eventId: String,
    val refEventId: String,
    val newEvidence: Evidence,
) : LedgerEvent
