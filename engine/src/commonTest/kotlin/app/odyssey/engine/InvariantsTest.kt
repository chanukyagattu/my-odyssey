package app.odyssey.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The invariant suite. These are not unit tests of methods; they are the
 * properties the product promises, expressed as executable statements.
 *
 * If one of these goes red, a user-visible number is wrong.
 */
class InvariantsTest {

    // ---------- fixtures ----------

    private val zion = CanonEntry(
        "p-ut-zion", "UT", "Zion", Lifecycle.ACTIVE, LatLng(37.2982, -113.0263), 3600,
    )
    private val arches = CanonEntry(
        "p-ut-arches", "UT", "Arches", Lifecycle.ACTIVE, LatLng(38.7331, -109.5925), 3600,
    )
    private val liberty = CanonEntry(
        "p-ny-liberty", "NY", "Statue of Liberty", Lifecycle.ACTIVE, LatLng(40.6892, -74.0445), 1800,
    )
    private val niagara = CanonEntry(
        "p-ny-niagara", "NY", "Niagara Falls", Lifecycle.ACTIVE, LatLng(43.0828, -79.0742), 1800,
    )

    private val canon = CanonRelease(1, listOf(zion, arches, liberty, niagara))

    private val user = "u1"
    private val day = 86_400L
    private val t0 = 1_700_000_000L

    private companion object {
        const val EPS = 1e-9
    }

    private fun visit(
        id: String,
        place: String,
        dayOffset: Long,
        dwell: Long = 7200,
        evidence: Evidence = Evidence.GPS_VERIFIED,
        device: String? = null,
        seq: Long? = null,
    ) = VisitRecorded(
        eventId = id,
        userId = user,
        placeId = place,
        startEpochSec = t0 + dayOffset * day,
        endEpochSec = t0 + dayOffset * day + dwell,
        evidence = evidence,
        deviceId = device,
        sourceSeq = seq,
    )

    private fun ledgerOf(vararg events: LedgerEvent): InMemoryLedger {
        val l = InMemoryLedger()
        events.forEach { assertTrue(l.ingest(it, canon).isAccepted, "setup event ${it.eventId} rejected") }
        return l
    }

    // ---------- 1. permutation independence ----------

    @Test
    fun foldIsIndependentOfIngestOrder() {
        val events = listOf(
            visit("e1", zion.placeId, 0),
            visit("e2", arches.placeId, 10),
            visit("e3", liberty.placeId, 40),
            visit("e4", niagara.placeId, 70),
        )
        val rng = Random(20260801)
        val reference = fold(ledgerOf(*events.toTypedArray()).events(), canon, user)

        repeat(25) {
            val shuffled = events.shuffled(rng)
            val l = InMemoryLedger()
            shuffled.forEach { assertTrue(l.ingest(it, canon).isAccepted, "order-dependent rejection: ${it.eventId}") }
            assertEquals(reference, fold(l.events(), canon, user), "fold differed under permutation")
        }
    }

    @Test
    fun foldIsIndependentOfOrderWithCompensatingEvents() {
        val v1 = visit("e1", zion.placeId, 0)
        val v2 = visit("e2", arches.placeId, 10, evidence = Evidence.SELF_REPORTED)
        val upgrade = EvidenceUpgraded("u1", "e2", Evidence.PHOTO_VERIFIED)
        val revoke = VisitRevoked("r1", "e1", "user says they never went")
        val all = listOf<LedgerEvent>(v1, v2, upgrade, revoke)

        val reference = fold(all, canon, user)
        val rng = Random(7)
        repeat(25) {
            assertEquals(reference, fold(all.shuffled(rng), canon, user))
        }
        // e1 revoked, e2 upgraded to photo -> exactly one credited place
        assertEquals(setOf(arches.placeId), reference.placesCredited)
    }

    // ---------- 2. idempotent ingestion ----------

    @Test
    fun duplicateEventIdIsANoOp() {
        val l = ledgerOf(visit("e1", zion.placeId, 0))
        assertEquals(IngestResult.DuplicateNoOp, l.ingest(visit("e1", zion.placeId, 0), canon))
        assertEquals(1, l.size())
    }

    @Test
    fun atLeastOnceProducerRetryIsANoOp() {
        // Same (user, device, sourceSeq); the producer retried with a fresh eventId.
        val first = visit("e1", zion.placeId, 0, device = "d1", seq = 42)
        val retry = visit("e2", zion.placeId, 0, device = "d1", seq = 42)
        val l = ledgerOf(first)
        assertEquals(IngestResult.DuplicateNoOp, l.ingest(retry, canon))
        assertEquals(1, l.size())
    }

    @Test
    fun offlineDumpReplayedTwiceChangesNothing() {
        val dump = listOf(
            visit("e1", zion.placeId, 0, device = "d1", seq = 1),
            visit("e2", arches.placeId, 10, device = "d1", seq = 2),
            visit("e3", liberty.placeId, 40, device = "d1", seq = 3),
        )
        val l = InMemoryLedger()
        dump.forEach { l.ingest(it, canon) }
        val after1 = fold(l.events(), canon, user)
        dump.forEach { l.ingest(it, canon) }
        assertEquals(3, l.size())
        assertEquals(after1, fold(l.events(), canon, user))
    }

    // ---------- 3. deterministic fold ----------

    @Test
    fun sameLedgerAndCanonAlwaysProduceSameOutput() {
        val l = ledgerOf(
            visit("e1", zion.placeId, 0),
            visit("e2", arches.placeId, 10),
        )
        val a = fold(l.events(), canon, user)
        repeat(10) { assertEquals(a, fold(l.events(), canon, user)) }
    }

    @Test
    fun completionIsDerivedNotStored() {
        // Utah completes. Then the canon gains a third Utah place: the SAME
        // ledger must now report Utah incomplete, with no write anywhere.
        val l = ledgerOf(
            visit("e1", zion.placeId, 0),
            visit("e2", arches.placeId, 10),
        )
        assertTrue("UT" in fold(l.events(), canon, user).regionsComplete)

        val bryce = CanonEntry(
            "p-ut-bryce", "UT", "Bryce Canyon", Lifecycle.ACTIVE, LatLng(37.5930, -112.1871), 3600,
        )
        val v2 = CanonRelease(2, canon.entries + bryce)
        val after = fold(l.events(), canon = v2, userId = user)
        assertFalse("UT" in after.regionsComplete)
        assertEquals(2 to 3, after.regionProgress("UT"))
    }

    // ---------- 4. corrections are compensating events only ----------

    @Test
    fun revocationAppendsAndNeverDeletes() {
        val l = ledgerOf(visit("e1", zion.placeId, 0))
        assertTrue(l.ingest(VisitRevoked("r1", "e1", "misattributed"), canon).isAccepted)

        assertEquals(2, l.size())
        assertNotNull(l.events().filterIsInstance<VisitRecorded>().firstOrNull { it.eventId == "e1" })
        assertTrue(fold(l.events(), canon, user).placesCredited.isEmpty())
    }

    @Test
    fun revokingAnUnknownEventIsRejected() {
        val l = InMemoryLedger()
        val r = l.ingest(VisitRevoked("r1", "nope", "x"), canon)
        assertTrue(r is IngestResult.Rejected)
        assertEquals(0, l.size())
    }

    // ---------- 5. suspend -> reactivate round trip ----------

    @Test
    fun suspensionShiftsTheDenominatorAndReactivationRestoresExactly() {
        val l = ledgerOf(visit("e1", zion.placeId, 0))
        val before = fold(l.events(), canon, user)
        assertEquals(4, before.placesDenominator)
        assertEquals(1 to 2, before.regionProgress("UT"))

        val suspended = canon.withLifecycle(setOf(arches.placeId), Lifecycle.SUSPENDED)
        val during = fold(l.events(), suspended, user)
        assertEquals(3, during.placesDenominator)
        assertEquals(1 to 1, during.regionProgress("UT"))
        assertTrue("UT" in during.regionsComplete, "Utah completes while Arches is closed")

        val reactivated = suspended.withLifecycle(setOf(arches.placeId), Lifecycle.ACTIVE)
        val after = fold(l.events(), reactivated, user)
        assertEquals(before.copy(canonVersion = after.canonVersion), after, "round trip was not lossless")
    }

    @Test
    fun aFrozenStateCannotCompleteForFree() {
        // Every NY entry suspended: NY leaves BOTH sides of state coverage.
        val frozenNy = canon.withLifecycle(setOf(liberty.placeId, niagara.placeId), Lifecycle.SUSPENDED)
        val l = ledgerOf(visit("e1", zion.placeId, 0), visit("e2", arches.placeId, 10))
        val r = fold(l.events(), frozenNy, user)

        assertEquals(setOf("NY"), r.frozenRegions)
        assertEquals(1, r.regionDenominator)
        assertFalse("NY" in r.regionsComplete)
        assertEquals(100.0, r.regionCoveragePct, EPS) // UT alone is 100% of the states in play
    }

    // ---------- 6. plausibility at the door ----------

    @Test
    fun teleportsAreRejectedAtIngest() {
        val l = ledgerOf(visit("e1", zion.placeId, 0))
        // Zion -> Statue of Liberty is ~3400km; one hour later is impossible.
        val teleport = VisitRecorded(
            "e2", user, liberty.placeId,
            startEpochSec = t0 + 7200 + 3600,
            endEpochSec = t0 + 7200 + 3600 + 3600,
            evidence = Evidence.GPS_VERIFIED,
        )
        val rejected = assertNotNull(l.ingest(teleport, canon) as? IngestResult.Rejected, "teleport was accepted")
        assertTrue(rejected.reason.contains("teleport"), rejected.reason)
        assertEquals(1, l.size())
    }

    @Test
    fun aRealisticCrossCountryTripIsAccepted() {
        val l = ledgerOf(visit("e1", zion.placeId, 0))
        assertTrue(l.ingest(visit("e2", liberty.placeId, 3), canon).isAccepted)
    }

    @Test
    fun overlappingVisitsToDifferentPlacesAreRejected() {
        val l = ledgerOf(visit("e1", zion.placeId, 0, dwell = 7200))
        val overlapping = VisitRecorded(
            "e2", user, arches.placeId,
            startEpochSec = t0 + 3600,
            endEpochSec = t0 + 10_800,
            evidence = Evidence.GPS_VERIFIED,
        )
        val rejected = assertNotNull(l.ingest(overlapping, canon) as? IngestResult.Rejected)
        assertTrue(rejected.reason.contains("overlaps"), rejected.reason)
    }

    @Test
    fun theLedgerNeverContainsAnImpossibleHistory() {
        // Fuzz: random visits, random order. Whatever survives ingest must be
        // physically consistent — no pair of accepted visits overlaps.
        val rng = Random(424242)
        val places = canon.entries
        val l = InMemoryLedger()
        repeat(300) { i ->
            val p = places[rng.nextInt(places.size)]
            val start = t0 + rng.nextLong(0, 120L * day)
            l.ingest(
                VisitRecorded(
                    "f$i", user, p.placeId, start, start + rng.nextLong(1800, 14_400),
                    Evidence.GPS_VERIFIED,
                ),
                canon,
            )
        }
        val accepted = l.events().filterIsInstance<VisitRecorded>()
        for (a in accepted) {
            for (b in accepted) {
                if (a.eventId >= b.eventId || a.placeId == b.placeId) continue
                val overlap = minOf(a.endEpochSec, b.endEpochSec) - maxOf(a.startEpochSec, b.startEpochSec)
                assertTrue(overlap <= 120, "accepted history has ${overlap}s overlap: ${a.eventId}/${b.eventId}")
            }
        }
    }

    // ---------- 7. policy edge cases ----------

    @Test
    fun proposedPlacesPreCountAndPayOutOnActivation() {
        val everest = CanonEntry(
            "p-ut-secret", "UT", "Not Yet Announced", Lifecycle.PROPOSED,
            LatLng(37.9000, -112.0000), 1800,
        )
        val withProposed = CanonRelease(2, canon.entries + everest)
        val l = ledgerOf(visit("e1", everest.placeId, 30, dwell = 3600))

        val before = fold(l.events(), withProposed, user)
        assertEquals(4, before.placesDenominator, "a proposed place is not yet in the denominator")
        assertFalse(before.isCredited(everest.placeId))

        val activated = withProposed.withLifecycle(setOf(everest.placeId), Lifecycle.ACTIVE)
        val after = fold(l.events(), activated, user)
        assertEquals(5, after.placesDenominator)
        assertTrue(after.isCredited(everest.placeId), "credit should appear with zero migration")
    }

    @Test
    fun driveBysUnderTheDwellFloorDoNotScore() {
        val l = ledgerOf(visit("e1", zion.placeId, 0, dwell = 1800)) // floor is 3600
        val r = fold(l.events(), canon, user)
        assertFalse(r.isCredited(zion.placeId))
        assertEquals(1, r.uncreditedVisits[zion.placeId])
    }

    @Test
    fun selfReportsRenderButNeverMoveAPercentage() {
        val l = ledgerOf(visit("e1", zion.placeId, 0, evidence = Evidence.SELF_REPORTED))
        val r = fold(l.events(), canon, user)
        assertEquals(1, l.size(), "the visit is in the ledger")
        assertFalse(r.isCredited(zion.placeId), "but it does not count")
        assertEquals(0.0, r.placesCoveragePct, EPS)
    }

    @Test
    fun evidenceIsUpgradeOnly() {
        val l = ledgerOf(visit("e1", zion.placeId, 0, evidence = Evidence.PHOTO_VERIFIED))
        val down = l.ingest(EvidenceUpgraded("u1", "e1", Evidence.SELF_REPORTED), canon)
        assertTrue(down is IngestResult.Rejected)
        val same = l.ingest(EvidenceUpgraded("u2", "e1", Evidence.PHOTO_VERIFIED), canon)
        assertTrue(same is IngestResult.Rejected)
        val up = l.ingest(EvidenceUpgraded("u3", "e1", Evidence.GPS_VERIFIED), canon)
        assertTrue(up.isAccepted)
        assertEquals(Evidence.GPS_VERIFIED, fold(l.events(), canon, user).creditedEvidence[zion.placeId])
    }

    @Test
    fun anUpgradeCanTurnAnUncreditedVisitIntoACreditedOne() {
        val l = ledgerOf(visit("e1", zion.placeId, 0, evidence = Evidence.SELF_REPORTED))
        assertEquals(0.0, fold(l.events(), canon, user).placesCoveragePct, EPS)
        assertTrue(l.ingest(EvidenceUpgraded("u1", "e1", Evidence.PHOTO_VERIFIED), canon).isAccepted)
        assertEquals(25.0, fold(l.events(), canon, user).placesCoveragePct, EPS)
    }

    @Test
    fun oneUsersLedgerNeverLeaksIntoAnother() {
        val l = ledgerOf(visit("e1", zion.placeId, 0))
        assertTrue(fold(l.events(), canon, "someone-else").placesCredited.isEmpty())
    }

    // ---------- 8. the shipped canon ----------

    @Test
    fun canonV1IsWellFormed() {
        val r = CanonV1.release
        assertEquals(100, r.entries.size)
        assertEquals(100, r.entries.map { it.placeId }.toSet().size)
        assertEquals(50, r.regionsInPlay().size)
        r.entries.groupBy { it.regionCode }.forEach { (state, entries) ->
            assertEquals(2, entries.size, "$state should have exactly 2 canon places")
            assertTrue(CanonV1.regionName(state) != state, "$state has no display name")
        }
        r.entries.forEach {
            assertTrue(it.centroid.lat in 18.0..72.0, "${it.placeId} latitude out of range")
            assertTrue(it.centroid.lng in -180.0..-60.0, "${it.placeId} longitude out of range")
            assertTrue(it.minDwellSeconds >= 600, "${it.placeId} dwell floor too low")
        }
    }

    @Test
    fun anEmptyLedgerIsZeroPercentNotACrash() {
        val r = fold(emptyList(), CanonV1.release, user)
        assertEquals(0.0, r.placesCoveragePct, EPS)
        assertEquals(0.0, r.regionCoveragePct, EPS)
        assertEquals(100, r.placesDenominator)
        assertEquals(50, r.regionDenominator)
    }
}
