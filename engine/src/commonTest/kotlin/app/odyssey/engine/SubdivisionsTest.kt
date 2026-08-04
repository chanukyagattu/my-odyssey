package app.odyssey.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * How big each country actually is.
 *
 * The bug these guard against shipped once: the country dial divided by
 * [FoldResult.regionDenominator], which counts regions across the *whole*
 * canon. Brazil therefore read "0/153 states" — a denominator belonging to no
 * country on earth, under a noun that is wrong in most of them.
 *
 * The coverage assertion is the one that will catch a future mistake: adding
 * places in more regions of a country than that country has is always a data
 * error, and it is silent otherwise.
 */
class SubdivisionsTest {

    private val db = CanonWorld.db

    @Test
    fun everyCountryInTheCanonHasASubdivisionEntry() {
        for (country in db.countries) {
            assertNotNull(Subdivisions[country], "$country is in the canon with no subdivision entry")
        }
    }

    @Test
    fun noCountryCoversMoreRegionsThanItHas() {
        for (country in db.countries) {
            val covered = db.regionsIn(country).size
            val total = Subdivisions.total(country, covered)
            assertTrue(
                covered <= total,
                "$country: canon covers $covered regions but the country only has $total",
            )
        }
    }

    @Test
    fun everyEntryIsUsable() {
        for (country in Subdivisions.countries) {
            val s = assertNotNull(Subdivisions[country])
            assertTrue(s.total in 1..200, "$country has an implausible total ${s.total}")
            assertTrue(s.plural.isNotBlank() && s.singular.isNotBlank(), "$country has a blank noun")
        }
    }

    @Test
    fun theCountsAreTheRealOnes() {
        assertEquals(50, Subdivisions.total("US", 0))
        assertEquals(27, Subdivisions.total("BR", 0), "26 states plus the Federal District")
        assertEquals(47, Subdivisions.total("JP", 0))
        assertEquals(13, Subdivisions.total("CA", 0), "10 provinces plus 3 territories")
        assertEquals(36, Subdivisions.total("IN", 0), "28 states plus 8 union territories")
        assertEquals(26, Subdivisions.total("CH", 0))
        assertEquals(9, Subdivisions.total("ZA", 0))
    }

    @Test
    fun theNounIsWhateverTheCountryCallsIt() {
        assertEquals("states", Subdivisions.plural("US"))
        assertEquals("prefectures", Subdivisions.plural("JP"))
        assertEquals("cantons", Subdivisions.plural("CH"))
        assertEquals("voivodeships", Subdivisions.plural("PL"))
        assertEquals("emirates", Subdivisions.plural("AE"))
        assertEquals("governorates", Subdivisions.plural("EG"))
        assertEquals("counties", Subdivisions.plural("KE"))
        assertEquals("prefecture", Subdivisions.singular("JP"))
    }

    @Test
    fun anUnknownCountryFallsBackToTheCanonRatherThanCrashing() {
        assertEquals(7, Subdivisions.total("XX", fallback = 7))
        assertEquals("regions", Subdivisions.plural("XX"))
        assertEquals("region", Subdivisions.singular("XX"))
    }

    // ---------- the snapshot reads them correctly ----------

    private fun snapshotFor(country: String): AppSnapshot {
        val canon = CanonWorld.release
        return AppSnapshot(
            canon = canon,
            events = emptyList(),
            result = fold(emptyList(), canon, "u1"),
            selection = Selection(country = country, regionCode = canon.regionsIn(country).first()),
        )
    }

    @Test
    fun theCountryDialDividesByTheCountryNotByTheWorld() {
        val brazil = snapshotFor("BR")
        assertEquals(27, brazil.regionsHereTotal)
        assertEquals("states", brazil.regionNoun)
        assertTrue(
            brazil.result.regionDenominator > 100,
            "the global denominator is still large — that is exactly why it must not be used here",
        )
    }

    @Test
    fun everyCountryReportsANonZeroDenominator() {
        // A zero denominator would render "0/0" and divide by zero in the ring.
        for (country in db.countries) {
            val snap = snapshotFor(country)
            assertTrue(snap.regionsHereTotal > 0, "$country would render 0/0")
            assertEquals(0.0, snap.regionCoverageHerePct, 1e-9, "$country starts at zero")
        }
    }

    @Test
    fun completingEveryCanonRegionStillDoesNotCompleteAPartlyCoveredCountry() {
        // Brazil has 2 of its 27 states in the canon. Finishing both must not
        // read as a finished Brazil.
        val canon = CanonWorld.release
        val events = canon.active()
            .filter { it.country == "BR" }
            .mapIndexed { i, e ->
                VisitRecorded(
                    eventId = "v$i",
                    userId = "u1",
                    placeId = e.placeId,
                    startEpochSec = 1_700_000_000L + i * 86_400L * 40,
                    endEpochSec = 1_700_000_000L + i * 86_400L * 40 + e.minDwellSeconds + 60,
                    evidence = Evidence.GPS_VERIFIED,
                )
            }
        val snap = AppSnapshot(
            canon = canon,
            events = events,
            result = fold(events, canon, "u1"),
            selection = Selection(country = "BR", regionCode = canon.regionsIn("BR").first()),
        )
        assertEquals(2, snap.regionsHereComplete)
        assertEquals(27, snap.regionsHereTotal)
        assertTrue(snap.regionCoverageHerePct < 100.0, "two cities are not a finished Brazil")
    }
}
