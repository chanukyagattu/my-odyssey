package app.odyssey.engine

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max

/**
 * A must-go place, as reference data.
 *
 * Richer than [CanonEntry], which it will replace: a stable primary key, the
 * full administrative hierarchy, and room for the fields a real gazetteer
 * carries. `city` and `postalCode` are present but frequently empty — a
 * fabricated postcode for the Serengeti is worse than a blank one, because
 * nobody can tell it is wrong by looking.
 *
 * [regionCode] is ISO 3166-2 shaped (`US-UT`, `FR-IDF`, `JP-26`), which
 * generalises past the US-only `usState` the app still uses today.
 */
data class CanonPlace(
    /** Primary key. Stable forever, never reused, encodes its country. */
    val placeId: String,
    val country: String,
    val regionCode: String,
    val regionName: String,
    val name: String,
    val city: String,
    val postalCode: String,
    val centroid: LatLng,
    val minDwellSeconds: Long,
    val geofenceRadiusMeters: Double,
    val tags: List<String>,
    val lifecycle: Lifecycle = Lifecycle.ACTIVE,
    /** When this row entered the canon. A release is a set of these. */
    val addedAtEpochSeconds: Long = 0,
) {
    val isActive: Boolean get() = lifecycle == Lifecycle.ACTIVE
}

/**
 * The in-memory canon.
 *
 * Indexed rather than scanned, for one concrete reason: the photo-library scan
 * asks "which place contains this point?" once per geotagged asset. Against a
 * forty-thousand-photo library and 228 places that is nine million distance
 * calculations; at canon v3 it would be far worse. A one-degree spatial grid
 * turns each lookup into a handful of comparisons, and [containing] is the only
 * hot path in the app.
 */
class CanonDb(
    val version: Int,
    val places: List<CanonPlace>,
) {
    init {
        require(places.map { it.placeId }.toSet().size == places.size) {
            "duplicate placeId in canon release $version"
        }
    }

    private val byId: Map<String, CanonPlace> = places.associateBy { it.placeId }
    private val byCountry: Map<String, List<CanonPlace>> = places.groupBy { it.country }
    private val byRegion: Map<String, List<CanonPlace>> = places.groupBy { it.regionCode }

    /** One-degree cells. Latitude bands are ~111 km; longitude narrows toward the poles. */
    private val grid: Map<Long, List<CanonPlace>> =
        places.filter { it.isActive }.groupBy { cellKey(it.centroid.lat, it.centroid.lng) }

    val countries: Set<String> get() = byCountry.keys
    val regions: Set<String> get() = byRegion.keys
    val active: List<CanonPlace> = places.filter { it.isActive }

    operator fun get(placeId: String): CanonPlace? = byId[placeId]

    fun inCountry(country: String): List<CanonPlace> = byCountry[country].orEmpty()

    fun inRegion(regionCode: String): List<CanonPlace> = byRegion[regionCode].orEmpty()

    fun regionsIn(country: String): List<String> =
        inCountry(country).map { it.regionCode }.distinct().sorted()

    /** Free-text search across name, city and region. */
    fun search(query: String, limit: Int = 25): List<CanonPlace> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return places.asSequence()
            .filter {
                it.name.lowercase().contains(q) ||
                    it.city.lowercase().contains(q) ||
                    it.regionName.lowercase().contains(q)
            }
            .sortedBy { it.name }
            .take(limit)
            .toList()
    }

    /** Active places whose centroid lies within [radiusMeters] of [point]. */
    fun near(point: LatLng, radiusMeters: Double): List<CanonPlace> =
        candidates(point, radiusMeters)
            .map { it to haversineMeters(point, it.centroid) }
            .filter { it.second <= radiusMeters }
            .sortedBy { it.second }
            .map { it.first }

    /**
     * The place whose geofence contains [point], nearest first where geofences
     * overlap. Null when the point is nowhere in the canon.
     */
    fun containing(point: LatLng): CanonPlace? =
        candidates(point, maxGeofenceMeters)
            .map { it to haversineMeters(point, it.centroid) }
            .filter { (place, d) -> d <= place.geofenceRadiusMeters }
            .minByOrNull { it.second }
            ?.first

    private val maxGeofenceMeters: Double =
        places.filter { it.isActive }.maxOfOrNull { it.geofenceRadiusMeters } ?: 0.0

    /** Everything in the grid cells that could possibly be within range. */
    private fun candidates(point: LatLng, radiusMeters: Double): List<CanonPlace> {
        val latSpan = radiusMeters / 111_000.0
        // Longitude degrees shrink with latitude; guard the poles so the span
        // never divides by something near zero.
        val cosLat = max(0.01, abs(cos(point.lat * kotlin.math.PI / 180.0)))
        val lngSpan = radiusMeters / (111_000.0 * cosLat)

        val latSteps = ceil(latSpan).toInt().coerceAtLeast(1)
        val lngSteps = ceil(lngSpan).toInt().coerceAtLeast(1)

        val out = ArrayList<CanonPlace>()
        for (dLat in -latSteps..latSteps) {
            for (dLng in -lngSteps..lngSteps) {
                grid[cellKey(point.lat + dLat, point.lng + dLng)]?.let { out.addAll(it) }
            }
        }
        return out
    }

    private fun cellKey(lat: Double, lng: Double): Long {
        // Longitude wraps; latitude is clamped rather than wrapped.
        val la = floor(lat).toInt().coerceIn(-90, 90)
        val ln = floor(((lng + 180.0) % 360.0 + 360.0) % 360.0 - 180.0).toInt()
        return la.toLong() * 1_000L + ln.toLong()
    }

    /** Bridges to the shape the fold currently speaks, until the rename lands. */
    fun asRelease(): CanonRelease = CanonRelease(
        version = version,
        entries = places.map {
            CanonEntry(
                placeId = it.placeId,
                usState = it.regionCode,
                name = it.name,
                lifecycle = it.lifecycle,
                centroid = it.centroid,
                minDwellSeconds = it.minDwellSeconds,
                geofenceRadiusMeters = it.geofenceRadiusMeters,
                country = it.country,
            )
        },
    )
}
