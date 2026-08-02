package app.odyssey.engine

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The fixtures are real JPEGs produced by an independent encoder (Pillow), not
 * bytes this project wrote. A parser tested only against its own writer proves
 * nothing about camera output.
 */
class ExifTest {

    private fun hex(s: String): ByteArray {
        val out = ByteArray(s.length / 2)
        for (i in out.indices) {
            val hi = HEX.indexOf(s[i * 2])
            val lo = HEX.indexOf(s[i * 2 + 1])
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private val zionJpeg get() = hex(ZION_WITH_GPS_HEX)
    private val plainJpeg get() = hex(NO_METADATA_HEX)

    @Test
    fun readsCoordinatesFromARealJpeg() {
        val exif = parseJpegExif(zionJpeg)
        val gps = assertNotNull(exif.gps, "no GPS block found")
        assertTrue(abs(gps.lat - 37.2982) < 1e-5, "lat was ${gps.lat}")
        assertTrue(abs(gps.lng - (-113.0263)) < 1e-5, "lng was ${gps.lng}")
    }

    @Test
    fun readsTheUtcGpsTimestamp() {
        // 2026-08-02 14:30:00 UTC
        assertEquals(1_785_681_000L, parseJpegExif(zionJpeg).utcEpochSeconds)
    }

    @Test
    fun keepsTheZonelessCameraTimestampAsTextOnly() {
        assertEquals("2026:08:02 07:30:00", parseJpegExif(zionJpeg).cameraTimestampText)
    }

    @Test
    fun aJpegWithoutMetadataYieldsNothing() {
        val exif = parseJpegExif(plainJpeg)
        assertNull(exif.gps)
        assertNull(exif.utcEpochSeconds)
        assertTrue(exif.isEmpty)
    }

    @Test
    fun garbageInputNeverThrows() {
        assertTrue(parseJpegExif(ByteArray(0)).isEmpty)
        assertTrue(parseJpegExif(byteArrayOf(-1, -40)).isEmpty)
        assertTrue(parseJpegExif("not a jpeg at all".encodeToByteArray()).isEmpty)
        assertTrue(parseJpegExif(ByteArray(5_000) { 0xFF.toByte() }).isEmpty)
    }

    @Test
    fun aTornFileDoesNotThrow() {
        val full = zionJpeg
        for (cut in listOf(4, 20, 64, 200, full.size / 2, full.size - 1)) {
            parseJpegExif(full.copyOfRange(0, cut))
        }
    }

    @Test
    fun corruptedExifDoesNotThrow() {
        val bytes = zionJpeg
        var i = 30
        while (i < minOf(bytes.size, 400)) {
            bytes[i] = (bytes[i].toInt() xor 0xFF).toByte()
            i += 7
        }
        parseJpegExif(bytes)
    }

    // ---------- the evidence rule ----------

    private val zion = CanonV1.release.byId.getValue("us-ut-zion")
    private val takenAt = 1_785_681_000L

    private fun visitAround(center: Long, span: Long = 3600) = VisitRecorded(
        eventId = "v1",
        userId = "u1",
        placeId = zion.placeId,
        startEpochSec = center - span,
        endEpochSec = center + span,
        evidence = Evidence.SELF_REPORTED,
    )

    private fun attachmentFrom(bytes: ByteArray, ref: String = "v1"): MediaAttached {
        val exif = parseJpegExif(bytes)
        return MediaAttached(
            eventId = "m1",
            refEventId = ref,
            mediaId = mediaIdOf(bytes),
            kind = MediaKind.PHOTO,
            byteSize = bytes.size.toLong(),
            exifLatE7 = exif.gps?.lat?.toE7(),
            exifLngE7 = exif.gps?.lng?.toE7(),
            exifUtcEpochSeconds = exif.utcEpochSeconds,
        )
    }

    @Test
    fun aPhotoInsideTheGeofenceAndWindowCorroborates() {
        assertTrue(photoCorroborates(attachmentFrom(zionJpeg), visitAround(takenAt), zion))
    }

    @Test
    fun theSamePhotoDoesNotCorroborateADifferentPlace() {
        val yellowstone = CanonV1.release.byId.getValue("us-wy-yellowstone")
        assertTrue(!photoCorroborates(attachmentFrom(zionJpeg), visitAround(takenAt), yellowstone))
    }

    @Test
    fun rightPlaceWrongDayEarnsNothing() {
        assertTrue(!photoCorroborates(attachmentFrom(zionJpeg), visitAround(takenAt + 86_400), zion))
    }

    @Test
    fun aPhotoWithNoMetadataNeverUpgradesEvidence() {
        assertTrue(!photoCorroborates(attachmentFrom(plainJpeg), visitAround(takenAt), zion))
    }

    @Test
    fun theToleranceAbsorbsASlightlyLateShutter() {
        val visit = visitAround(takenAt - 1200, span = 600) // photo 600s after the visit ended
        assertTrue(photoCorroborates(attachmentFrom(zionJpeg), visit, zion))
        val tooLate = visitAround(takenAt - 4000, span = 600)
        assertTrue(!photoCorroborates(attachmentFrom(zionJpeg), tooLate, zion))
    }

    @Test
    fun mediaAttachedToAnotherVisitIsIgnored() {
        val attachment = attachmentFrom(zionJpeg, ref = "some-other-visit")
        assertTrue(!photoCorroborates(attachment, visitAround(takenAt), zion))
    }

    @Test
    fun videosNeverCorroborate() {
        val asVideo = attachmentFrom(zionJpeg).copy(kind = MediaKind.VIDEO)
        assertTrue(!photoCorroborates(asVideo, visitAround(takenAt), zion))
    }

    private companion object {
        const val HEX = "0123456789abcdef"

        val ZION_WITH_GPS_HEX = listOf(
            "ffd8ffe000104a46494600010100000100010000ffe100f64578696600004d4d002a0000000800028769000400000001",
            "0000002688250004000000010000004c00000000000190030002000000140000003800000000323032363a30383a3032",
            "2030373a33303a303000000600010002000000024e00000000020005000000030000009a000300020000000257000000",
            "0004000500000003000000b20007000500000003000000ca001d00020000000b000000e2000000000000002500000001",
            "00000011000000010000053a000000190000007100000001000000010000000100000363000000190000000e00000001",
            "0000001e000000010000000000000001323032363a30383a30320000ffdb004300140e0f120f0d14121012171514181e",
            "32211e1c1c1e3d2c2e243249404c4b47404645505a736250556d5645466488656d777b8182814e608d978c7d96737e81",
            "7cffdb0043011517171e1a1e3b21213b7c5346537c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c",
            "7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7cffc00011080001000103012200021101031101ffc4001f000001",
            "0501010101010100000000000000000102030405060708090a0bffc400b5100002010303020403050504040000017d01",
            "020300041105122131410613516107227114328191a1082342b1c11552d1f02433627282090a161718191a2526272829",
            "2a3435363738393a434445464748494a535455565758595a636465666768696a737475767778797a838485868788898a",
            "92939495969798999aa2a3a4a5a6a7a8a9aab2b3b4b5b6b7b8b9bac2c3c4c5c6c7c8c9cad2d3d4d5d6d7d8d9dae1e2e3",
            "e4e5e6e7e8e9eaf1f2f3f4f5f6f7f8f9faffc4001f010003010101010101010101000000000000010203040506070809",
            "0a0bffc400b51100020102040403040705040400010277000102031104052131061241510761711322328108144291a1",
            "b1c109233352f0156272d10a162434e125f11718191a262728292a35363738393a434445464748494a53545556575859",
            "5a636465666768696a737475767778797a82838485868788898a92939495969798999aa2a3a4a5a6a7a8a9aab2b3b4b5",
            "b6b7b8b9bac2c3c4c5c6c7c8c9cad2d3d4d5d6d7d8d9dae2e3e4e5e6e7e8e9eaf2f3f4f5f6f7f8f9faffda000c030100",
            "02110311003f00e628a28ad883ffd9",
        ).joinToString("")

        val NO_METADATA_HEX = listOf(
            "ffd8ffe000104a46494600010100000100010000ffe100164578696600004d4d002a00000008000000000000ffdb0043",
            "00140e0f120f0d14121012171514181e32211e1c1c1e3d2c2e243249404c4b47404645505a736250556d564546648865",
            "6d777b8182814e608d978c7d96737e817cffdb0043011517171e1a1e3b21213b7c5346537c7c7c7c7c7c7c7c7c7c7c7c",
            "7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7cffc00011080001000103",
            "012200021101031101ffc4001f0000010501010101010100000000000000000102030405060708090a0bffc400b51000",
            "02010303020403050504040000017d01020300041105122131410613516107227114328191a1082342b1c11552d1f024",
            "33627282090a161718191a25262728292a3435363738393a434445464748494a535455565758595a636465666768696a",
            "737475767778797a838485868788898a92939495969798999aa2a3a4a5a6a7a8a9aab2b3b4b5b6b7b8b9bac2c3c4c5c6",
            "c7c8c9cad2d3d4d5d6d7d8d9dae1e2e3e4e5e6e7e8e9eaf1f2f3f4f5f6f7f8f9faffc4001f0100030101010101010101",
            "010000000000000102030405060708090a0bffc400b51100020102040403040705040400010277000102031104052131",
            "061241510761711322328108144291a1b1c109233352f0156272d10a162434e125f11718191a262728292a3536373839",
            "3a434445464748494a535455565758595a636465666768696a737475767778797a82838485868788898a929394959697",
            "98999aa2a3a4a5a6a7a8a9aab2b3b4b5b6b7b8b9bac2c3c4c5c6c7c8c9cad2d3d4d5d6d7d8d9dae2e3e4e5e6e7e8e9ea",
            "f2f3f4f5f6f7f8f9faffda000c03010002110311003f00e628a28ad883ffd9",
        ).joinToString("")
    }
}
