package app.odyssey.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * P7 / P8 / P9 behaviour.
 *
 * Explore is a reference list of what is LEFT. The two tabs partition the
 * canon: the moment a place earns credit it leaves Explore and appears in
 * Memories. These tests hold that partition and the ordering contract.
 */
class ExploreTest {

    private val canon = CanonV1.release
    private val user = "u1"

    private fun creditedLedger(vararg placeIds: String): List<LedgerEvent> {
        var t = 1_700_000_000L
        return placeIds.map { id ->
            val entry = canon.byId.getValue(id)
            val e = VisitRecorded(
                eventId = "v-$id",
                userId = user,
                placeId = id,
                startEpochSec = t,
                endEpochSec = t + entry.minDwellSeconds + 60,
                evidence = Evidence.GPS_VERIFIED,
            )
            t += 30L * 86_400
            e
        }
    }

    private fun groupsFor(
        scope: Scope,
        events: List<LedgerEvent> = emptyList(),
        state: String = "UT",
        fix: LatLng? = null,
    ): List<ExploreGroup> = exploreGroups(
        canon = canon,
        result = fold(events, canon, user),
        scope = scope,
        selectedCountry = "US",
        selectedState = state,
        fix = fix,
    )

    // ---------- grouping granularity ----------

    @Test
    fun p7GroupsTheWorldByCountry() {
        val groups = groupsFor(Scope.WORLD)
        assertEquals(1, groups.size, "canon v1 covers one country")
        assertEquals("US", groups[0].key)
        assertEquals("United States", groups[0].title)
        assertEquals(100, groups[0].remaining)
        assertEquals(100, groups[0].total)
    }

    @Test
    fun p8GroupsACountryByState() {
        val groups = groupsFor(Scope.COUNTRY)
        assertEquals(50, groups.size, "one group per state")
        assertTrue(groups.all { it.total == 2 })
        assertEquals(100, groups.sumOf { it.items.size })
    }

    @Test
    fun p9IsASingleStateGroup() {
        val groups = groupsFor(Scope.STATE, state = "WY")
        assertEquals(1, groups.size)
        assertEquals("Wyoming", groups[0].title)
        assertEquals(2, groups[0].items.size)
        assertTrue(groups[0].items.all { it.entry.usState == "WY" })
    }

    // ---------- the two tabs partition the canon ----------

    @Test
    fun aCreditedPlaceLeavesExploreAndEntersMemories() {
        val events = creditedLedger("us-ut-zion")
        val groups = groupsFor(Scope.COUNTRY, events)
        val utah = assertNotNull(groups.firstOrNull { it.key == "UT" })

        assertEquals(1, utah.remaining, "Zion is gone from Explore")
        assertEquals(2, utah.total)
        assertEquals(1, utah.credited)
        assertFalse(utah.items.any { it.entry.placeId == "us-ut-zion" })

        val repo = AppSnapshot(canon, events, fold(events, canon, user), Selection(usState = "UT"))
        val mem = memories(repo, Scope.STATE, "US", "UT")
        assertEquals(1, mem.size)
        assertEquals("us-ut-zion", mem[0].visit.placeId)
        assertTrue(mem[0].credited)
    }

    @Test
    fun exploreAndMemoriesNeverShowTheSamePlace() {
        val events = creditedLedger("us-ut-zion", "us-wy-yellowstone", "us-ny-niagara-falls")
        val snapshot = AppSnapshot(canon, events, fold(events, canon, user), Selection())

        val exploring = groupsFor(Scope.WORLD, events).flatMap { g -> g.items.map { it.entry.placeId } }
        val remembering = memories(snapshot, Scope.WORLD, "US", "UT").map { it.visit.placeId }

        assertTrue(exploring.intersect(remembering.toSet()).isEmpty(), "a place is in exactly one tab")
        assertEquals(97, exploring.size)
        assertEquals(3, remembering.size)
    }

    @Test
    fun anUncreditedVisitStaysInBothTabs() {
        // Self-reported: a real memory that does not move a percentage, so the
        // place is still on the to-do list. This is the one deliberate overlap.
        val zion = canon.byId.getValue("us-ut-zion")
        val events = listOf(
            VisitRecorded(
                "v1", user, zion.placeId, 1_700_000_000,
                1_700_000_000 + zion.minDwellSeconds + 60, Evidence.SELF_REPORTED,
            ),
        )
        val snapshot = AppSnapshot(canon, events, fold(events, canon, user), Selection())

        assertTrue(
            groupsFor(Scope.STATE, events, state = "UT").single().items.any { it.entry.placeId == zion.placeId },
            "still to do",
        )
        val mem = memories(snapshot, Scope.STATE, "US", "UT").single()
        assertFalse(mem.credited)
        assertEquals(Evidence.SELF_REPORTED, mem.evidence)
    }

    @Test
    fun revokedVisitsAppearInNeitherTab() {
        val events = creditedLedger("us-ut-zion") + VisitRevoked("r1", "v-us-ut-zion", "not me")
        val snapshot = AppSnapshot(canon, events, fold(events, canon, user), Selection())

        assertTrue(memories(snapshot, Scope.WORLD, "US", "UT").isEmpty())
        assertTrue(
            groupsFor(Scope.STATE, events, state = "UT").single().items.any { it.entry.placeId == "us-ut-zion" },
            "revoking puts the place back on the to-do list",
        )
    }

    // ---------- ordering ----------

    @Test
    fun withAFixTheNearestPlaceIsFirst() {
        // Standing in Salt Lake City.
        val slc = LatLng(40.7608, -111.8910)
        val groups = groupsFor(Scope.COUNTRY, fix = slc)

        val flat = groups.flatMap { it.items }
        val first = flat.first()
        val nearest = flat.minByOrNull { it.distanceMeters ?: Double.MAX_VALUE }
        assertEquals(nearest?.entry?.placeId, first.entry.placeId)

        groups.forEach { g ->
            val d = g.items.mapNotNull { it.distanceMeters }
            assertEquals(d.sorted(), d, "${g.title} is not sorted by distance")
        }
        val heads = groups.mapNotNull { it.items.firstOrNull()?.distanceMeters }
        assertEquals(heads.sorted(), heads, "groups are not ordered by their nearest member")
        assertEquals("Utah", groups.first().title, "Utah should lead from Salt Lake City")
    }

    @Test
    fun withNoFixTheMostCompleteGroupLeads() {
        val events = creditedLedger("us-wy-yellowstone")
        val groups = groupsFor(Scope.COUNTRY, events, fix = null)

        assertEquals("Wyoming", groups.first().title, "the state you have started leads")
        assertEquals(1, groups.first().credited)
        assertTrue(groups.drop(1).all { it.credited == 0 })
        val rest = groups.drop(1).map { it.title }
        assertEquals(rest.sorted(), rest, "the untouched remainder is alphabetical")
        assertTrue(groups.all { g -> g.items.map { it.entry.name }.let { it.sorted() == it } })
        assertTrue(groups.all { g -> g.items.all { it.distanceMeters == null } })
    }

    // ---------- the completes-a-state annotation ----------

    @Test
    fun theLastPlaceInAStateIsFlagged() {
        val events = creditedLedger("us-ut-zion")
        val utah = groupsFor(Scope.COUNTRY, events).first { it.key == "UT" }
        assertEquals(1, utah.items.size)
        assertTrue(utah.items.single().completesState, "Arches now completes Utah")

        val untouched = groupsFor(Scope.COUNTRY, events).first { it.key == "WY" }
        assertTrue(untouched.items.none { it.completesState }, "two left means neither one finishes it")
    }

    @Test
    fun theFlagIsComputedAgainstTheCanonNotTheCurrentFilter() {
        // In P9 the filter is a single state; the annotation must still mean
        // "last one left", not "last one on this screen".
        val groups = groupsFor(Scope.STATE, state = "UT")
        assertTrue(groups.single().items.none { it.completesState })
    }

    // ---------- scope filtering of Memories ----------

    @Test
    fun memoriesRespectScopeButStayFlat() {
        val events = creditedLedger("us-ut-zion", "us-wy-yellowstone")
        val snapshot = AppSnapshot(canon, events, fold(events, canon, user), Selection())

        assertEquals(2, memories(snapshot, Scope.WORLD, "US", "UT").size)
        assertEquals(2, memories(snapshot, Scope.COUNTRY, "US", "UT").size)
        assertEquals(1, memories(snapshot, Scope.STATE, "US", "UT").size)
        assertEquals(0, memories(snapshot, Scope.STATE, "US", "AK").size)
    }

    @Test
    fun memoriesAreNewestFirst() {
        val events = creditedLedger("us-ut-zion", "us-wy-yellowstone", "us-ny-niagara-falls")
        val snapshot = AppSnapshot(canon, events, fold(events, canon, user), Selection())
        val stamps = memories(snapshot, Scope.WORLD, "US", "UT").map { it.visit.startEpochSec }
        assertEquals(stamps.sortedDescending(), stamps)
    }
}
