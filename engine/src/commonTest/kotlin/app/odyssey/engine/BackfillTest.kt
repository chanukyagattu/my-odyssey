package app.odyssey.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Claiming past visits.
 *
 * The threat model is explicit: someone AirDrops you a photo they took at Zion.
 * It has real coordinates and a real timestamp. No inspection of that one file
 * can tell you the recipient was never there. These tests pin both defences —
 * the pattern requirement that makes a single donated photo useless, and the
 * scoring split that makes a successful forgery worthless.
 */
class BackfillTest {

    private val canon = CanonV1.release
    private val zion = canon.byId.getValue("us-ut-zion")
    private val user = "u1"
    private val t0 = 1_600_000_000L

    private fun photo(
        id: String,
        atSeconds: Long,
        lat: Double = zion.centroid.lat,
        lng: Double = zion.centroid.lng,
        gps: Boolean = true,
        dated: Boolean = true,
    ) = PhotoEvidence(
        mediaId = id,
        gps = if (gps) LatLng(lat, lng) else null,
        utcEpochSeconds = if (dated) t0 + atSeconds else null,
        byteSize = 2048,
    )

    /** A plausible afternoon: spread across the dwell floor, moving as you walk. */
    private fun goodSet(): List<PhotoEvidence> {
        val dwell = zion.minDwellSeconds
        return listOf(
            photo("a", 0),
            photo("b", dwell / 2, lat = zion.centroid.lat + 0.0015),
            photo("c", dwell + 600, lat = zion.centroid.lat + 0.0030),
        )
    }

    // ---------- the pattern requirement ----------

    @Test
    fun aGenuineSetIsAccepted() {
        val ok = assertNotNull(checkBackfill(zion, goodSet()) as? BackfillCheck.Accepted)
        assertEquals(3, ok.photos.size)
        assertEquals(t0, ok.startEpochSec)
        assertTrue(ok.spreadMeters > BACKFILL_MIN_SPREAD_METERS)
    }

    @Test
    fun oneDonatedPhotoIsNotEnough() {
        // The whole attack: a friend's photo with perfect metadata.
        val donated = photo("theirs", zion.minDwellSeconds)
        val r = assertNotNull(checkBackfill(zion, listOf(donated)) as? BackfillCheck.Rejected)
        assertTrue(r.reason.contains("at least"), r.reason)
    }

    @Test
    fun twoPhotosAreStillNotEnough() {
        assertTrue(checkBackfill(zion, goodSet().take(2)) is BackfillCheck.Rejected)
    }

    @Test
    fun photosFromOneSpotAreRejected() {
        // Same coordinates throughout: a tripod, a forgery, or a screenshot set.
        val dwell = zion.minDwellSeconds
        val still = listOf(photo("a", 0), photo("b", dwell / 2), photo("c", dwell + 600))
        val r = assertNotNull(checkBackfill(zion, still) as? BackfillCheck.Rejected)
        assertTrue(r.reason.contains("same spot"), r.reason)
    }

    @Test
    fun photosTakenTooCloseTogetherInTimeAreRejected() {
        val quick = listOf(
            photo("a", 0),
            photo("b", 60, lat = zion.centroid.lat + 0.0015),
            photo("c", 120, lat = zion.centroid.lat + 0.0030),
        )
        val r = assertNotNull(checkBackfill(zion, quick) as? BackfillCheck.Rejected)
        assertTrue(r.reason.contains("minutes"), r.reason)
    }

    @Test
    fun photosSpanningMoreThanADayAreNotOneVisit() {
        val stitched = listOf(
            photo("a", 0),
            photo("b", 3600, lat = zion.centroid.lat + 0.0015),
            photo("c", 40L * 3600, lat = zion.centroid.lat + 0.0030),
        )
        val r = assertNotNull(checkBackfill(zion, stitched) as? BackfillCheck.Rejected)
        assertTrue(r.reason.contains("more than a day"), r.reason)
    }

    @Test
    fun photosFromTheWrongPlaceAreRejected() {
        val elsewhere = goodSet().map { it.copy(gps = LatLng(40.7608, -111.8910)) }
        val r = assertNotNull(checkBackfill(zion, elsewhere) as? BackfillCheck.Rejected)
        assertTrue(r.reason.contains("outside"), r.reason)
    }

    @Test
    fun strippedMetadataIsRejectedWithAnExplanation() {
        val stripped = goodSet().map { it.copy(gps = null, utcEpochSeconds = null) }
        val r = assertNotNull(checkBackfill(zion, stripped) as? BackfillCheck.Rejected)
        assertTrue(r.reason.contains("no embedded location"), r.reason)
    }

    // ---------- the scoring split ----------

    private fun claimVisit(placeId: String = zion.placeId) = VisitRecorded(
        eventId = "claim-1",
        userId = user,
        placeId = placeId,
        startEpochSec = t0,
        endEpochSec = t0 + zion.minDwellSeconds + 600,
        evidence = Evidence.IMPORT_VERIFIED,
    )

    private fun liveVisit(placeId: String, at: Long = 1_700_000_000L) = VisitRecorded(
        eventId = "live-$placeId",
        userId = user,
        placeId = placeId,
        startEpochSec = at,
        endEpochSec = at + canon.byId.getValue(placeId).minDwellSeconds + 600,
        evidence = Evidence.GPS_VERIFIED,
    )

    @Test
    fun aClaimNeverTouchesTheHeadlineNumber() {
        val r = fold(listOf(claimVisit()), canon, user)
        assertTrue(zion.placeId in r.placesClaimed)
        assertFalse(zion.placeId in r.placesCredited)
        assertEquals(0.0, r.placesCoveragePct, 1e-9, "a claim must not move the verified percentage")
        assertEquals(1.0, r.claimedCoveragePct, 1e-9)
    }

    @Test
    fun aClaimCanNeverCompleteAState() {
        // Both Utah places claimed, neither verified. Each window is built from
        // its own place's dwell floor — they differ across the canon, and a
        // borrowed constant silently turns this into a one-place test.
        val arches = canon.byId.getValue("us-ut-arches")
        val events = listOf(
            claimVisit("us-ut-zion"),
            claimVisit("us-ut-arches").copy(
                eventId = "claim-2",
                startEpochSec = t0 + 200_000,
                endEpochSec = t0 + 200_000 + arches.minDwellSeconds + 600,
            ),
        )
        val r = fold(events, canon, user)
        assertEquals(2, r.placesClaimed.size)
        assertFalse("UT" in r.statesComplete, "state completion is reserved for verified visits")
        assertEquals(0.0, r.stateCoveragePct, 1e-9)
    }

    @Test
    fun verifyingLaterPromotesTheClaimOutOfTheClaimedBucket() {
        val events = listOf(claimVisit(), liveVisit(zion.placeId))
        val r = fold(events, canon, user)
        assertTrue(zion.placeId in r.placesCredited, "the live visit counts")
        assertFalse(zion.placeId in r.placesClaimed, "and it is no longer merely claimed")
        assertEquals(1, r.placesCredited.size + r.placesClaimed.size, "never counted twice")
    }

    @Test
    fun theTwoBucketsNeverOverlap() {
        val events = listOf(
            claimVisit("us-ut-zion"),
            liveVisit("us-wy-yellowstone"),
        )
        val r = fold(events, canon, user)
        assertTrue(r.placesCredited.intersect(r.placesClaimed).isEmpty())
        assertEquals(setOf("us-wy-yellowstone"), r.placesCredited)
        assertEquals(setOf("us-ut-zion"), r.placesClaimed)
    }

    @Test
    fun aClaimUnderTheDwellFloorCountsForNothing() {
        val short = claimVisit().copy(endEpochSec = t0 + 60)
        val r = fold(listOf(short), canon, user)
        assertTrue(r.placesClaimed.isEmpty())
        assertTrue(r.placesCredited.isEmpty())
    }

    @Test
    fun revokingAClaimRemovesIt() {
        val events = listOf(claimVisit(), VisitRevoked("r1", "claim-1", "not mine"))
        assertTrue(fold(events, canon, user).placesClaimed.isEmpty())
    }

    // ---------- the repository path ----------

    @Test
    fun theRepositoryRefusesAThinClaimAndAcceptsAGenuineOne() {
        val repo = OdysseyRepository(userId = "claimer-${kotlin.random.Random.nextInt(100_000)}")
        val one = SyntheticJpeg.withGps(zion.centroid, t0, salt = 1)
        val refused = repo.claimPastVisit(zion.placeId, listOf(one))
        assertNotNull((refused as? OdysseyRepository.ClaimOutcome.Refused)?.reason)

        val dwell = zion.minDwellSeconds
        val genuine = listOf(
            SyntheticJpeg.withGps(zion.centroid, t0, salt = 2),
            SyntheticJpeg.withGps(LatLng(zion.centroid.lat + 0.0015, zion.centroid.lng), t0 + dwell / 2, salt = 3),
            SyntheticJpeg.withGps(LatLng(zion.centroid.lat + 0.0030, zion.centroid.lng), t0 + dwell + 600, salt = 4),
        )
        val ok = assertNotNull(
            repo.claimPastVisit(zion.placeId, genuine) as? OdysseyRepository.ClaimOutcome.Accepted,
        )
        assertEquals(3, ok.photos)

        val snap = repo.snapshot()
        assertTrue(zion.placeId in snap.result.placesClaimed)
        assertFalse(
            zion.placeId in snap.result.placesCredited,
            "attaching the claim's own photos must not promote it to verified",
        )
        assertEquals(3, snap.mediaFor(ok.visitEventId).size)
    }

    @Test
    fun duplicateFilesInAClaimCountOnce() {
        val repo = OdysseyRepository(userId = "dupes-${kotlin.random.Random.nextInt(100_000)}")
        val one = SyntheticJpeg.withGps(zion.centroid, t0, salt = 9)
        val outcome = repo.claimPastVisit(zion.placeId, listOf(one, one, one))
        assertNotNull((outcome as? OdysseyRepository.ClaimOutcome.Refused)?.reason)
    }
}
