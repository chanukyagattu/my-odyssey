package app.odyssey.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The card is the only thing that ever leaves the device. These are privacy
 * assertions before they are formatting assertions.
 */
class ShareCardTest {

    private val canon = CanonV1.release
    private val user = "traveller"

    private fun snapshotWith(vararg placeIds: String, state: String = "UT"): AppSnapshot {
        var t = 1_700_000_000L
        val events: List<LedgerEvent> = placeIds.map { id ->
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
        return AppSnapshot(canon, events, fold(events, canon, user), Selection(usState = state))
    }

    // ---------- privacy ----------

    @Test
    fun noCanonPlaceNameEverAppearsOnACard() {
        val snap = snapshotWith("us-ut-zion", "us-ut-arches", "us-wy-yellowstone")
        val names = canon.entries.map { it.name }

        for (scope in Scope.entries) {
            val text = shareCardFor(snap, scope, user).allText.joinToString(" ")
            for (name in names) {
                assertFalse(text.contains(name), "card leaked place name '$name' at $scope")
            }
        }
    }

    @Test
    fun noStateNameOrCodeEverAppearsOnACard() {
        val snap = snapshotWith("us-ut-zion", "us-ut-arches")
        for (scope in Scope.entries) {
            val text = shareCardFor(snap, scope, user).allText.joinToString(" ")
            for ((code, name) in CanonV1.stateNames) {
                assertFalse(text.contains(name), "card leaked state name '$name' at $scope")
                assertFalse(text.contains(" $code "), "card leaked state code '$code' at $scope")
            }
        }
    }

    @Test
    fun noCoordinatesOrTimestampsEverAppearOnACard() {
        val snap = snapshotWith("us-ut-zion")
        for (scope in Scope.entries) {
            val text = shareCardFor(snap, scope, user).allText.joinToString(" ")
            // Latitudes and longitudes always carry a decimal point with 3+
            // digits after it; nothing legitimate on a card looks like that.
            assertFalse(
                Regex("""-?\d+\.\d{3,}""").containsMatchIn(text),
                "card looks like it contains a coordinate at $scope: $text",
            )
            // Epoch seconds, or anything else long enough to be one.
            assertFalse(
                Regex("""\d{9,}""").containsMatchIn(text),
                "card looks like it contains a timestamp at $scope: $text",
            )
        }
    }

    @Test
    fun theStateCardDoesNotNameTheState() {
        val snap = snapshotWith("us-ut-zion", state = "UT")
        val card = shareCardFor(snap, Scope.STATE, user)
        assertFalse(card.allText.joinToString(" ").contains("Utah"))
        assertEquals("ONE STATE DOWN", card.scopeLabel)
    }

    // ---------- numbers come from the fold ----------

    @Test
    fun theWorldCardCountsPlaces() {
        val snap = snapshotWith("us-ut-zion", "us-wy-yellowstone")
        val card = shareCardFor(snap, Scope.WORLD, user)
        assertEquals("2/100", card.bigValue)
        assertTrue(card.stats.any { it.value == "0/50" && it.label == "states complete" })
    }

    @Test
    fun theCountryCardCountsCompletedStates() {
        val snap = snapshotWith("us-ut-zion", "us-ut-arches")
        val card = shareCardFor(snap, Scope.COUNTRY, user)
        assertEquals("2%", card.bigValue, "1 of 50 states complete")
        assertTrue(card.stats.any { it.value == "1/50" })
    }

    @Test
    fun theStateCardCountsPlacesInTheSelectedState() {
        val snap = snapshotWith("us-ut-zion", state = "UT")
        assertEquals("1/2", shareCardFor(snap, Scope.STATE, user).bigValue)
    }

    @Test
    fun aCompleteStateSaysSo() {
        val snap = snapshotWith("us-ut-zion", "us-ut-arches", state = "UT")
        val card = shareCardFor(snap, Scope.STATE, user)
        assertEquals("2/2", card.bigValue)
        assertTrue(card.verifiedLine.contains("complete"))
    }

    @Test
    fun anEmptyLedgerRendersZeroNotACrash() {
        val snap = snapshotWith()
        for (scope in Scope.entries) {
            val card = shareCardFor(snap, scope, user)
            assertEquals(0f, card.fraction, 1e-6f)
            assertTrue(card.allText.all { it.isNotBlank() }, "blank field on an empty card at $scope")
        }
    }

    // ---------- formatting ----------

    @Test
    fun percentagesDropAPointlessDecimal() {
        val snap = snapshotWith("us-ut-zion", "us-ut-arches")
        assertEquals("2%", shareCardFor(snap, Scope.COUNTRY, user).bigValue)
    }

    @Test
    fun fractionsTrackTheFold() {
        val snap = snapshotWith("us-ut-zion", "us-ut-arches")
        val world = shareCardFor(snap, Scope.WORLD, user)
        assertEquals(0.02f, world.fraction, 1e-6f)
        assertEquals(1f, shareCardFor(snap, Scope.STATE, user).fraction, 1e-6f)
    }

    @Test
    fun aSignedOutCardStillRenders() {
        val card = shareCardFor(snapshotWith(), Scope.WORLD, null)
        assertEquals("my odyssey", card.handle)
    }

    @Test
    fun theHandleIsPrefixed() {
        assertEquals("@traveller", shareCardFor(snapshotWith(), Scope.WORLD, user).handle)
    }
}
