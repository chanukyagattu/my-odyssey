package app.odyssey.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The media library: on-device, content-addressed, append-only at the ledger
 * level and reclaimable at the blob level. These tests pin the boundary between
 * those two — what may be deleted and what may never be.
 */
class MediaTest {

    private val canon = CanonV1.release
    private val zion = canon.byId.getValue("us-ut-zion")
    private val user = "u1"

    private fun freshLedger(): InMemoryLedger = InMemoryLedger()

    private fun visit(id: String = "v1", start: Long = 1_785_679_000L) = VisitRecorded(
        eventId = id,
        userId = user,
        placeId = zion.placeId,
        startEpochSec = start,
        endEpochSec = start + zion.minDwellSeconds + 600,
        evidence = Evidence.SELF_REPORTED,
    )

    private fun attach(
        eventId: String,
        ref: String,
        mediaId: String,
        kind: MediaKind = MediaKind.PHOTO,
        size: Long = 1_024,
    ) = MediaAttached(eventId, ref, mediaId, kind, size)

    // ---------- content addressing ----------

    @Test
    fun theSameBytesAlwaysProduceTheSameId() {
        val store = MediaStore()
        val bytes = "a photograph of Zion".encodeToByteArray()
        val a = store.put(bytes)
        val b = store.put(bytes.copyOf())
        assertEquals(a, b, "identical bytes must not create a second blob")
        assertEquals(mediaIdOf(bytes), a)
        assertTrue(store.exists(a))
        assertEquals(bytes.toList(), store.read(a)?.toList())
        store.delete(a)
    }

    @Test
    fun aBlobReadsBackByteForByte() {
        val store = MediaStore()
        val bytes = ByteArray(4_096) { (it * 31 % 251).toByte() }
        val id = store.put(bytes)
        val back = assertNotNull(store.read(id))
        assertEquals(bytes.toList(), back.toList())
        assertEquals(id, mediaIdOf(back), "the filename must still be the hash of the content")
        store.delete(id)
    }

    @Test
    fun readingAnUnknownIdReturnsNull() {
        assertNull(MediaStore().read("0".repeat(64)))
        assertFalse(MediaStore().exists("0".repeat(64)))
    }

    // ---------- ledger rules ----------

    @Test
    fun mediaMustReferenceAKnownVisit() {
        val l = freshLedger()
        val r = l.ingest(attach("m1", "nope", "abc"), canon)
        assertTrue(r is IngestResult.Rejected)
        assertEquals(0, l.size())
    }

    @Test
    fun reattachingTheSamePhotoToTheSameVisitIsANoOp() {
        val l = freshLedger()
        assertTrue(l.ingest(visit(), canon).isAccepted)
        assertTrue(l.ingest(attach("m1", "v1", "hash-a"), canon).isAccepted)
        assertEquals(IngestResult.DuplicateNoOp, l.ingest(attach("m2", "v1", "hash-a"), canon))
        assertEquals(2, l.size())
    }

    @Test
    fun theSamePhotoCanBelongToTwoDifferentVisits() {
        val l = freshLedger()
        assertTrue(l.ingest(visit("v1", 1_700_000_000), canon).isAccepted)
        assertTrue(l.ingest(visit("v2", 1_710_000_000), canon).isAccepted)
        assertTrue(l.ingest(attach("m1", "v1", "hash-a"), canon).isAccepted)
        assertTrue(l.ingest(attach("m2", "v2", "hash-a"), canon).isAccepted)
        assertEquals(4, l.size())
    }

    @Test
    fun detachingIsACompensatingEvent() {
        val l = freshLedger()
        l.ingest(visit(), canon)
        l.ingest(attach("m1", "v1", "hash-a"), canon)
        assertTrue(l.ingest(MediaDetached("x1", "m1", "removed"), canon).isAccepted)

        assertEquals(3, l.size(), "nothing is deleted from the log")
        assertNotNull(l.events().filterIsInstance<MediaAttached>().firstOrNull { it.eventId == "m1" })

        val snap = AppSnapshot(canon, l.events(), fold(l.events(), canon, user), Selection())
        assertTrue(snap.mediaFor("v1").isEmpty(), "but it is no longer live")
    }

    @Test
    fun afterDetachingThePhotoCanBeAttachedAgain() {
        val l = freshLedger()
        l.ingest(visit(), canon)
        l.ingest(attach("m1", "v1", "hash-a"), canon)
        l.ingest(MediaDetached("x1", "m1", "oops"), canon)
        assertTrue(l.ingest(attach("m2", "v1", "hash-a"), canon).isAccepted, "re-adding should work")
    }

    @Test
    fun detachingSomethingUnknownIsRejected() {
        assertTrue(freshLedger().ingest(MediaDetached("x1", "nope", "y"), canon) is IngestResult.Rejected)
    }

    @Test
    fun mediaOnARevokedVisitStopsBeingLive() {
        val l = freshLedger()
        l.ingest(visit(), canon)
        l.ingest(attach("m1", "v1", "hash-a"), canon)
        l.ingest(VisitRevoked("r1", "v1", "not me"), canon)

        val snap = AppSnapshot(canon, l.events(), fold(l.events(), canon, user), Selection())
        assertTrue(snap.liveMedia.isEmpty())
        assertEquals(3, l.size())
    }

    // ---------- media never moves a percentage on its own ----------

    @Test
    fun attachingMediaDoesNotChangeTheFold() {
        val l = freshLedger()
        l.ingest(visit(), canon)
        val before = fold(l.events(), canon, user)
        l.ingest(attach("m1", "v1", "hash-a"), canon)
        l.ingest(attach("m2", "v1", "hash-b", MediaKind.VIDEO, 50_000_000), canon)
        assertEquals(before, fold(l.events(), canon, user), "only evidence upgrades move numbers")
    }

    // ---------- persistence ----------

    @Test
    fun mediaEventsRoundTripThroughTheCodec() {
        val events: List<LedgerEvent> = listOf(
            MediaAttached("m1", "v1", "a".repeat(64), MediaKind.PHOTO, 1_234, 372_982_000, -1_130_263_000, 1_785_681_000),
            MediaAttached("m2", "v1", "b".repeat(64), MediaKind.VIDEO, 98_765_432),
            MediaDetached("x1", "m1", "removed | with a pipe"),
        )
        for (e in events) {
            assertEquals(e, Codec.decode(Codec.encode(e)), "round trip failed for $e")
        }
        assertEquals(events, Codec.decodeAll(Codec.encodeAll(events)))
    }

    @Test
    fun coordinatesSurvivePersistenceExactly() {
        // E7 integers, not doubles: the log must read identically on every platform.
        val attached = MediaAttached(
            "m1", "v1", "c".repeat(64), MediaKind.PHOTO, 10,
            exifLatE7 = 37.2982.toE7(),
            exifLngE7 = (-113.0263).toE7(),
            exifUtcEpochSeconds = 1_785_681_000,
        )
        val back = Codec.decode(Codec.encode(attached)) as MediaAttached
        assertEquals(attached, back)
        assertEquals(372_982_000L, back.exifLatE7)
        assertEquals(-1_130_263_000L, back.exifLngE7)
        val gps = assertNotNull(back.exifGps)
        assertEquals(37.2982, gps.lat, 1e-9)
        assertEquals(-113.0263, gps.lng, 1e-9)
    }

    @Test
    fun aMissingBlobIsATombstoneNotACrash() {
        // The blob store is reclaimable; the ledger is not. A memory whose bytes
        // were evicted must still render, and the fold must be untouched.
        val l = freshLedger()
        l.ingest(visit(), canon)
        l.ingest(attach("m1", "v1", "deadbeef".repeat(8)), canon)
        val snap = AppSnapshot(canon, l.events(), fold(l.events(), canon, user), Selection())

        assertEquals(1, snap.mediaFor("v1").size)
        assertFalse(MediaStore().exists("deadbeef".repeat(8)), "the bytes are gone")
        assertEquals(fold(l.events(), canon, user), snap.result, "the numbers do not care")
    }

    // ---------- accounting ----------

    @Test
    fun byteFormattingIsHumanReadable() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1 KB", formatBytes(1_024))
        assertEquals("1.0 MB", formatBytes(1_048_576))
        assertEquals("2.5 MB", formatBytes(2_621_440))
        assertEquals("1.0 GB", formatBytes(1_073_741_824))
        assertEquals("0 B", formatBytes(0))
    }
}
