package app.odyssey.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Scanning sixteen years of photographs.
 *
 * The cases that matter are the ones a real library actually contains: the same
 * place visited years apart, a day trip through two places, photos with no
 * location at all, and the imprecise fixes that phones wrote before about 2015.
 */
class SegmentationTest {

    private val canon = CanonV1.release
    private val zion = canon.byId.getValue("us-ut-zion")
    private val arches = canon.byId.getValue("us-ut-arches")

    private var seq = 0

    private fun at(
        entry: CanonEntry,
        epoch: Long,
        latOffset: Double = 0.0,
        own: Boolean = true,
        accuracy: Double? = null,
    ) = AssetPoint(
        assetId = "asset-${seq++}",
        location = LatLng(entry.centroid.lat + latOffset, entry.centroid.lng),
        epochSeconds = epoch,
        capturedByThisDevice = own,
        horizontalAccuracyMeters = accuracy,
    )

    /** A believable afternoon: several shots, spread out, moving. */
    private fun afternoon(entry: CanonEntry, start: Long, own: Boolean = true) = listOf(
        at(entry, start, own = own),
        at(entry, start + entry.minDwellSeconds / 2, latOffset = 0.0015, own = own),
        at(entry, start + entry.minDwellSeconds + 900, latOffset = 0.0030, own = own),
    )

    // ---------- clustering ----------

    @Test
    fun oneAfternoonBecomesOneVisit() {
        val out = segmentLibrary(afternoon(zion, 1_400_000_000), canon)
        assertEquals(1, out.size)
        assertEquals(zion.placeId, out[0].placeId)
        assertEquals(3, out[0].photoCount)
        assertEquals(ProposalStrength.VERIFIED, out[0].strength)
    }

    @Test
    fun theSamePlaceYearsApartBecomesSeparateVisits() {
        // 2014, 2019, 2024 — the case a sixteen-year library is full of.
        val assets = afternoon(zion, 1_400_000_000) +
            afternoon(zion, 1_560_000_000) +
            afternoon(zion, 1_720_000_000)
        val out = segmentLibrary(assets, canon)
        assertEquals(3, out.size, "three trips must not collapse into one")
        assertTrue(out.all { it.placeId == zion.placeId })
        assertTrue(out.all { it.strength == ProposalStrength.VERIFIED })
    }

    @Test
    fun twoPlacesInOneDayBecomeTwoVisits() {
        val morning = afternoon(zion, 1_500_000_000)
        val evening = afternoon(arches, 1_500_000_000 + 7 * 3600)
        val out = segmentLibrary(morning + evening, canon)
        assertEquals(2, out.size)
        assertEquals(listOf(zion.placeId, arches.placeId), out.map { it.placeId })
    }

    @Test
    fun returningToAPlaceLaterTheSameDayIsANewVisit() {
        val first = afternoon(zion, 1_500_000_000)
        val second = afternoon(zion, 1_500_000_000 + 9 * 3600)
        assertEquals(2, segmentLibrary(first + second, canon).size)
    }

    @Test
    fun proposalsComeBackInTimeOrder() {
        val assets = afternoon(arches, 1_700_000_000) + afternoon(zion, 1_400_000_000)
        val out = segmentLibrary(assets, canon)
        assertEquals(out.sortedBy { it.startEpochSec }, out)
    }

    // ---------- what gets ignored ----------

    @Test
    fun photosWithNoLocationAreIgnored() {
        val blind = AssetPoint("no-gps", null, 1_500_000_000, capturedByThisDevice = true)
        assertTrue(segmentLibrary(listOf(blind), canon).isEmpty())
    }

    @Test
    fun imprecisePreSmartphoneFixesAreIgnored() {
        // A 2 km accuracy radius says nothing about which side of a geofence you were on.
        val vague = afternoon(zion, 1_300_000_000).map {
            it.copy(horizontalAccuracyMeters = 2_000.0)
        }
        assertTrue(segmentLibrary(vague, canon).isEmpty())
    }

    @Test
    fun photosNowhereNearTheCanonAreIgnored() {
        val home = AssetPoint("home", LatLng(40.7608, -111.8910), 1_500_000_000, true)
        assertTrue(segmentLibrary(listOf(home), canon).isEmpty())
    }

    // ---------- strength ----------

    @Test
    fun receivedPhotosStayAClaimWhenYouWereNeverThere() {
        // The donated-photo attack, at scale: someone else's album, imported,
        // with nothing of yours anywhere near it.
        val out = segmentLibrary(afternoon(zion, 1_500_000_000, own = false), canon)
        assertEquals(1, out.size)
        assertEquals(ProposalBasis.UNCORROBORATED, out[0].basis)
        assertEquals(ProposalStrength.CLAIM, out[0].strength)
        assertEquals(Evidence.IMPORT_VERIFIED, out[0].evidence)
        assertEquals(0, out[0].ownPhotoCount)
    }

    @Test
    fun someoneElsesPhotosVerifyWhenYourOwnCameraPutsYouNearby() {
        // The case that matters: your partner took the good photos at the
        // viewpoint, but you photographed the car park that morning.
        val theirs = afternoon(zion, 1_500_000_000, own = false)
        val yours = at(zion, 1_500_000_000 - 4 * 3600, latOffset = 0.25) // ~28 km away, same day

        val visit = segmentLibrary(theirs + yours, canon).first { it.placeId == zion.placeId && it.ownPhotoCount == 0 }
        assertEquals(ProposalBasis.CORROBORATED, visit.basis)
        assertEquals(ProposalStrength.VERIFIED, visit.strength)
    }

    @Test
    fun corroborationDoesNotStretchAcrossTheCountry() {
        val theirs = afternoon(zion, 1_500_000_000, own = false)
        // Your own photos that day were 500 km away. You were not at Zion.
        val elsewhere = AssetPoint("far", LatLng(43.8791, -103.4591), 1_500_000_000, capturedByThisDevice = true)
        val out = segmentLibrary(theirs + elsewhere, canon).first { it.placeId == zion.placeId }
        assertEquals(ProposalBasis.UNCORROBORATED, out.basis)
    }

    @Test
    fun corroborationDoesNotStretchAcrossTheYear() {
        val theirs = afternoon(zion, 1_500_000_000, own = false)
        // You were at Zion — but eight months earlier. That was a different trip.
        val lastYear = at(zion, 1_500_000_000 - 200L * 86_400, latOffset = 0.1)
        val out = segmentLibrary(theirs + lastYear, canon).first { it.ownPhotoCount == 0 }
        assertEquals(ProposalBasis.UNCORROBORATED, out.basis)
    }

    @Test
    fun yourOwnPhotosCarryTheVisitEvenWhenMixedWithBorrowedOnes() {
        // A group trip: some shots yours, some AirDropped from a friend. The
        // pattern is judged on yours; theirs ride along as media.
        val mixed = afternoon(zion, 1_500_000_000).toMutableList()
        mixed.add(at(zion, 1_500_000_000 + 300, latOffset = 0.0020, own = false))
        val out = segmentLibrary(mixed, canon).single()
        assertEquals(ProposalBasis.OWN_CAPTURE, out.basis)
        assertEquals(4, out.photoCount)
        assertEquals(3, out.ownPhotoCount)
    }

    @Test
    fun borrowedPhotosCannotManufactureThePatternOnTheirOwn() {
        // One photo of yours, padded out with a friend's. Your single photo is
        // not a pattern, so this must not verify on OWN_CAPTURE.
        val padded = listOf(
            at(zion, 1_500_000_000),
            at(zion, 1_500_000_000 + zion.minDwellSeconds, latOffset = 0.0015, own = false),
            at(zion, 1_500_000_000 + zion.minDwellSeconds + 900, latOffset = 0.0030, own = false),
        )
        assertEquals(ProposalBasis.CORROBORATED, segmentLibrary(padded, canon).single().basis)
    }

    @Test
    fun oneOwnPhotoIsNotEnoughToVerify() {
        val out = segmentLibrary(listOf(at(zion, 1_500_000_000)), canon)
        assertEquals(1, out.size)
        assertEquals(ProposalStrength.CLAIM, out[0].strength, "a single photo is not a pattern")
    }

    @Test
    fun aQuickStopDoesNotVerify() {
        // Inside the geofence, but nowhere near the dwell floor.
        val driveBy = listOf(
            at(zion, 1_500_000_000),
            at(zion, 1_500_000_120, latOffset = 0.0015),
        )
        assertEquals(ProposalStrength.CLAIM, segmentLibrary(driveBy, canon).single().strength)
    }

    @Test
    fun ownPhotosFromOneSpotStillVerify() {
        // No movement requirement here, unlike the manual claim path. Spread
        // exists to make donated photos hard to assemble; when the camera
        // itself vouches for the capture, two shots ninety minutes apart from
        // one bench are just someone who sat at a viewpoint.
        val bench = listOf(
            at(zion, 1_500_000_000),
            at(zion, 1_500_000_000 + zion.minDwellSeconds + 600),
        )
        assertEquals(ProposalBasis.OWN_CAPTURE, segmentLibrary(bench, canon).single().basis)
    }

    @Test
    fun aVisitCannotCorroborateItself() {
        // A single own photo must not prove its own case by being its own
        // regional evidence.
        val lone = segmentLibrary(listOf(at(zion, 1_500_000_000)), canon).single()
        assertEquals(ProposalBasis.UNCORROBORATED, lone.basis)
    }

    @Test
    fun aLonePhotoStillGetsAUsableWindow() {
        // No duration of its own, so it borrows the dwell floor and survives the
        // fold rather than being silently dropped for zero dwell.
        val out = segmentLibrary(listOf(at(zion, 1_500_000_000)), canon).single()
        assertEquals(zion.minDwellSeconds, out.endEpochSec - out.startEpochSec)
    }

    // ---------- the summary the scan screen shows ----------

    @Test
    fun theSummaryCountsWhatItSaw() {
        val assets = afternoon(zion, 1_400_000_000) +
            afternoon(arches, 1_500_000_000, own = false) +
            AssetPoint("no-gps", null, 1_600_000_000, true)

        val s = summariseScan(assets, canon)
        assertEquals(7, s.assetsSeen)
        assertEquals(6, s.assetsWithLocation)
        assertEquals(1, s.verified.size)
        assertEquals(1, s.claims.size)
        assertEquals(setOf(zion.placeId, arches.placeId), s.placesTouched)
        assertEquals(setOf(zion.placeId), s.placesVerifiable)
    }

    @Test
    fun anEmptyLibraryProposesNothing() {
        val s = summariseScan(emptyList(), canon)
        assertEquals(0, s.assetsSeen)
        assertTrue(s.proposals.isEmpty())
    }

    // ---------- proposals must survive the ledger ----------

    @Test
    fun clusteredVisitsSurviveThePlausibilityChecks() {
        // Feeding raw photos into the ledger would trip the teleport check on
        // any drive between two places. Clustering first is what avoids that.
        val assets = afternoon(zion, 1_500_000_000) + afternoon(arches, 1_500_000_000 + 8 * 3600)
        val ledger = InMemoryLedger()
        var accepted = 0
        for ((i, p) in segmentLibrary(assets, canon).withIndex()) {
            val r = ledger.ingest(
                VisitRecorded("import-$i", "u1", p.placeId, p.startEpochSec, p.endEpochSec, p.evidence),
                canon,
            )
            if (r.isAccepted) accepted++
        }
        assertEquals(2, accepted, "a real day trip must not be rejected as a teleport")
    }

    @Test
    fun aVerifiedProposalCountsAndAClaimDoesNot() {
        val verified = segmentLibrary(afternoon(zion, 1_500_000_000), canon).single()
        val claimed = segmentLibrary(afternoon(arches, 1_600_000_000, own = false), canon).single()

        val events = listOf(
            VisitRecorded("a", "u1", verified.placeId, verified.startEpochSec, verified.endEpochSec, verified.evidence),
            VisitRecorded("b", "u1", claimed.placeId, claimed.startEpochSec, claimed.endEpochSec, claimed.evidence),
        )
        val r = fold(events, canon, "u1")
        assertEquals(setOf(zion.placeId), r.placesCredited)
        assertEquals(setOf(arches.placeId), r.placesClaimed)
        assertFalse("UT" in r.statesComplete, "a claim cannot finish the state on its own")
    }
}
