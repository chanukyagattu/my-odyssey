package app.odyssey.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The canon as reference data.
 *
 * The index tests matter more than they look: [CanonDb.containing] is the only
 * hot path in the app, called once per geotagged asset during a library scan.
 * A spatial index that returns *nearly* the same answer as a full scan is a bug
 * that would show up as places quietly missing from someone's import, so the
 * grid is checked against brute force over every place in the canon.
 */
class CanonDbTest {

    private val db = CanonWorld.db

    // ---------- shape ----------

    @Test
    fun theCanonIsWellFormed() {
        assertEquals(228, db.places.size)
        assertEquals(228, db.places.map { it.placeId }.toSet().size, "placeIds must be unique")
        assertTrue(db.countries.size >= 45)
        assertTrue(db.regions.size >= 150)
    }

    @Test
    fun everyRowIsInternallyConsistent() {
        for (p in db.places) {
            assertTrue(p.placeId.startsWith(p.country.lowercase() + "-"), "${p.placeId} hides its country")
            assertTrue(p.regionCode.startsWith(p.country + "-"), "${p.placeId} region ${p.regionCode} is foreign")
            assertTrue(p.name.isNotBlank(), "${p.placeId} has no name")
            assertTrue(p.regionName.isNotBlank(), "${p.placeId} has no region name")
            assertTrue(p.minDwellSeconds in 900..28_800, "${p.placeId} dwell ${p.minDwellSeconds}")
            assertTrue(p.geofenceRadiusMeters in 500.0..60_000.0, "${p.placeId} geofence")
            assertTrue(p.centroid.lat in -90.0..90.0 && p.centroid.lng in -180.0..180.0)
        }
    }

    @Test
    fun theUnitedStatesCanonSurvivedTheMerge() {
        val us = db.inCountry("US")
        assertEquals(100, us.size)
        assertEquals(50, db.regionsIn("US").size)
        us.groupBy { it.regionCode }.forEach { (region, places) ->
            assertEquals(2, places.size, "$region should still have exactly 2 places")
        }
    }

    @Test
    fun theWorldCanonReachesEveryContinent() {
        for (country in listOf("JP", "AU", "BR", "EG", "ZA", "IN", "FR", "PE", "NZ", "CA")) {
            assertTrue(db.inCountry(country).isNotEmpty(), "$country is missing from the canon")
        }
    }

    // ---------- indices ----------

    @Test
    fun lookupByPrimaryKeyWorks() {
        val taj = assertNotNull(db["in-up-tajmahal"])
        assertEquals("Taj Mahal", taj.name)
        assertEquals("Agra", taj.city)
        assertEquals("IN-UP", taj.regionCode)
        assertNull(db["no-such-place"])
    }

    @Test
    fun countryAndRegionIndicesAgreeWithAFullScan() {
        for (country in db.countries) {
            assertEquals(
                db.places.filter { it.country == country }.toSet(),
                db.inCountry(country).toSet(),
                "country index disagrees for $country",
            )
        }
        for (region in db.regions) {
            assertEquals(
                db.places.filter { it.regionCode == region }.toSet(),
                db.inRegion(region).toSet(),
                "region index disagrees for $region",
            )
        }
    }

    // ---------- the spatial grid ----------

    @Test
    fun theGridAgreesWithBruteForceAtEveryPlace() {
        // Standing exactly on each place: the index must find what a full scan
        // would find, everywhere, including across the antimeridian and at
        // high latitudes where longitude degrees compress.
        for (p in db.active) {
            val bruteForce = db.active
                .filter { haversineMeters(p.centroid, it.centroid) <= it.geofenceRadiusMeters }
                .minByOrNull { haversineMeters(p.centroid, it.centroid) }
            assertEquals(
                bruteForce?.placeId,
                db.containing(p.centroid)?.placeId,
                "index and scan disagree at ${p.placeId}",
            )
        }
    }

    @Test
    fun theGridAgreesWithBruteForceOnAWorldwideSweep() {
        var checked = 0
        var lat = -80.0
        while (lat <= 80.0) {
            var lng = -180.0
            while (lng < 180.0) {
                val point = LatLng(lat, lng)
                val expected = db.active
                    .filter { haversineMeters(point, it.centroid) <= it.geofenceRadiusMeters }
                    .minByOrNull { haversineMeters(point, it.centroid) }
                assertEquals(expected?.placeId, db.containing(point)?.placeId, "disagreement at $lat,$lng")
                checked++
                lng += 7.5
            }
            lat += 10.0
        }
        assertTrue(checked > 700, "sweep was too sparse to mean anything ($checked points)")
    }

    @Test
    fun nearAgreesWithBruteForce() {
        val probes = listOf(
            LatLng(48.8584, 2.2945), // Paris
            LatLng(35.6595, 139.7005), // Tokyo
            LatLng(-33.8568, 151.2153), // Sydney
            LatLng(64.0, -19.0), // Iceland, high latitude
            LatLng(-27.11, -109.35), // Rapa Nui, mid-Pacific
        )
        for (point in probes) {
            for (radius in listOf(5_000.0, 100_000.0, 1_000_000.0)) {
                val expected = db.active
                    .filter { haversineMeters(point, it.centroid) <= radius }
                    .map { it.placeId }
                    .toSet()
                assertEquals(expected, db.near(point, radius).map { it.placeId }.toSet(), "$point @ ${radius}m")
            }
        }
    }

    @Test
    fun aPointInTheOceanBelongsToNoPlace() {
        assertNull(db.containing(LatLng(0.0, -140.0)))
        assertNull(db.containing(LatLng(-60.0, 40.0)))
    }

    // ---------- search ----------

    @Test
    fun searchFindsByNameCityAndRegion() {
        assertTrue(db.search("taj").any { it.placeId == "in-up-tajmahal" })
        assertTrue(db.search("kyoto").any { it.placeId == "jp-26-fushimi" }, "matched on city")
        assertTrue(db.search("tuscany").any { it.city == "Florence" }, "matched on region name")
        assertTrue(db.search("").isEmpty())
        assertTrue(db.search("zzzzzz").isEmpty())
    }

    @Test
    fun searchIsCaseInsensitiveAndBounded() {
        assertEquals(db.search("PARIS").size, db.search("paris").size)
        assertTrue(db.search("a", limit = 5).size <= 5)
    }

    // ---------- the bridge to the current fold ----------

    @Test
    fun theReleaseBridgePreservesEverything() {
        val release = db.asRelease()
        assertEquals(db.places.size, release.entries.size)
        val taj = assertNotNull(release.byId["in-up-tajmahal"])
        assertEquals("IN", taj.country)
        assertEquals("IN-UP", taj.usState, "region rides in the field the fold still calls usState")
        assertEquals(db["in-up-tajmahal"]!!.geofenceRadiusMeters, taj.geofenceRadiusMeters)
    }

    @Test
    fun anEmptyLedgerFoldsCleanlyOverTheWorldCanon() {
        val r = fold(emptyList(), CanonWorld.release, "u1")
        assertEquals(228, r.placesDenominator)
        assertEquals(0.0, r.placesCoveragePct, 1e-9)
        assertTrue(r.stateDenominator >= 150, "every region is in play")
    }
}
