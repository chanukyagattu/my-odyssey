package app.odyssey.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityTest {

    private val canon = CanonV1.release
    private val zion = canon.byId.getValue("us-ut-zion")
    private val user = "u1"
    private val t0 = 1_785_628_800L

    private fun snap(events: List<LedgerEvent>) =
        AppSnapshot(canon, events, fold(events, canon, user), Selection(usState = "UT"))

    private fun visit(id: String = "v1", dwellBonus: Long = 600, evidence: Evidence = Evidence.GPS_VERIFIED) =
        VisitRecorded(id, user, zion.placeId, t0, t0 + zion.minDwellSeconds + dwellBonus, evidence)

    @Test
    fun aCreditedVisitReadsAsVerified() {
        val entry = activityFeed(snap(listOf(visit()))).single()
        assertEquals(ActivityKind.VERIFIED, entry.kind)
        assertTrue(entry.headline.contains("Zion National Park"))
        assertTrue(entry.detail.contains("Utah"))
        assertEquals("2 Aug 2026", entry.dateLabel)
    }

    @Test
    fun anUncreditedVisitSaysSo() {
        val entry = activityFeed(snap(listOf(visit(evidence = Evidence.SELF_REPORTED)))).single()
        assertEquals(ActivityKind.RECORDED, entry.kind)
        assertTrue(entry.detail.contains("does not count"), entry.detail)
    }

    @Test
    fun newestFirstByLogPosition() {
        val events = listOf(
            visit("v1"),
            EvidenceUpgraded("u1", "v1", Evidence.GPS_VERIFIED),
            VisitRevoked("r1", "v1", "misattributed"),
        )
        val feed = activityFeed(snap(events))
        assertEquals(listOf(2, 1, 0), feed.map { it.index })
        assertEquals(ActivityKind.REVOKED, feed[0].kind)
    }

    @Test
    fun compensatingEventsCarryNoWallClock() {
        // Only VisitRecorded has a timestamp; the rest record that, not when.
        val events = listOf(visit("v1"), VisitRevoked("r1", "v1", "nope"))
        val feed = activityFeed(snap(events))
        assertNull(feed[0].dateLabel)
        assertEquals("2 Aug 2026", feed[1].dateLabel)
    }

    @Test
    fun everyEntryResolvesBackToItsPlace() {
        val events = listOf(
            visit("v1"),
            MediaAttached("m1", "v1", "a".repeat(64), MediaKind.PHOTO, 2048),
            MediaDetached("x1", "m1", "removed"),
            EvidenceUpgraded("u1", "v1", Evidence.GPS_VERIFIED),
            VisitRevoked("r1", "v1", "misattributed"),
        )
        for (entry in activityFeed(snap(events))) {
            assertTrue(
                entry.detail.contains("Zion National Park") || entry.kind == ActivityKind.VERIFIED ||
                    entry.kind == ActivityKind.RECORDED,
                "entry did not resolve to a place: $entry",
            )
        }
    }

    @Test
    fun mediaLinesStateWhereTheBytesLive() {
        val events = listOf(visit("v1"), MediaAttached("m1", "v1", "b".repeat(64), MediaKind.PHOTO, 2048))
        val entry = activityFeed(snap(events)).first()
        assertEquals(ActivityKind.MEDIA_ADDED, entry.kind)
        assertTrue(entry.detail.contains("stays on this device"), entry.detail)
    }

    @Test
    fun detachingSaysTheEventSurvives() {
        val events = listOf(
            visit("v1"),
            MediaAttached("m1", "v1", "c".repeat(64), MediaKind.PHOTO, 2048),
            MediaDetached("x1", "m1", "removed"),
        )
        val entry = activityFeed(snap(events)).first()
        assertEquals(ActivityKind.MEDIA_REMOVED, entry.kind)
        assertTrue(entry.detail.contains("event kept"), entry.detail)
    }

    @Test
    fun anEmptyLedgerIsAnEmptyFeed() {
        assertTrue(activityFeed(snap(emptyList())).isEmpty())
    }
}
