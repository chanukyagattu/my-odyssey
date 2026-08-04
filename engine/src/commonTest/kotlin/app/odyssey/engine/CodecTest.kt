package app.odyssey.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The on-disk log is the system of record. If the codec is not exactly
 * round-trip faithful, the fold is computing over a different history than the
 * one that happened.
 */
class CodecTest {

    private val sample: List<LedgerEvent> = listOf(
        VisitRecorded("v-1", "u1", "us-ut-zion", 1_700_000_000, 1_700_007_200, Evidence.GPS_VERIFIED, "d1", 1),
        VisitRecorded("v-2", "u1", "us-ny-liberty", 1_700_100_000, 1_700_103_600, Evidence.SELF_REPORTED),
        VisitRevoked("r-1", "v-2", "wrong place | with a pipe\nand a newline"),
        EvidenceUpgraded("u-1", "v-2", Evidence.PHOTO_VERIFIED),
    )

    @Test
    fun everyEventTypeRoundTrips() {
        for (event in sample) {
            assertEquals(event, Codec.decode(Codec.encode(event)), "round trip failed for $event")
        }
    }

    @Test
    fun theWholeLogRoundTrips() {
        assertEquals(sample, Codec.decodeAll(Codec.encodeAll(sample)))
    }

    @Test
    fun delimitersInsideAFieldSurvive() {
        val nasty = VisitRevoked("r-9", "v-9", "a|b\\c\nd||e")
        val line = Codec.encode(nasty)
        assertTrue(line.split("|").size == 4, "escaping leaked a delimiter: $line")
        assertEquals(nasty, Codec.decode(line))
    }

    @Test
    fun optionalProducerKeysRoundTripAsNull() {
        val v = sample[1] as VisitRecorded
        val back = Codec.decode(Codec.encode(v)) as VisitRecorded
        assertNull(back.deviceId)
        assertNull(back.sourceSeq)
    }

    @Test
    fun aTornTailIsDroppedNotFatal() {
        // Simulates a crash mid-append: the last line is truncated garbage.
        val text = Codec.encodeAll(sample) + "\nV|v-3|u1|us-ut-arch"
        val decoded = Codec.decodeAll(text)
        assertEquals(sample, decoded, "a partial write must not lose the history before it")
    }

    @Test
    fun headerAndBlankLinesAreIgnored() {
        assertNull(Codec.decode(Codec.HEADER))
        assertNull(Codec.decode("   "))
        assertNull(Codec.decode("# a comment"))
    }

    @Test
    fun aPersistedLedgerFoldsIdenticallyAfterReload() {
        val canon = CanonV1.release
        val original = InMemoryLedger()
        val events = listOf(
            VisitRecorded("v-1", "u1", "us-ut-zion", 1_700_000_000, 1_700_007_200, Evidence.GPS_VERIFIED),
            VisitRecorded("v-2", "u1", "us-ut-arches", 1_701_000_000, 1_701_007_200, Evidence.GPS_VERIFIED),
        )
        events.forEach { assertTrue(original.ingest(it, canon).isAccepted) }

        val reloaded = InMemoryLedger()
        reloaded.restore(Codec.decodeAll(Codec.encodeAll(original.events())))

        assertEquals(fold(original.events(), canon, "u1"), fold(reloaded.events(), canon, "u1"))
        assertTrue("UT" in fold(reloaded.events(), canon, "u1").regionsComplete)
    }
}
