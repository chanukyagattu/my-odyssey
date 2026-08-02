package app.odyssey.engine

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Builds a minimal JPEG container carrying real EXIF GPS metadata.
 *
 * Development affordance: the Simulator has no camera roll, so without this
 * there is no way to exercise the photo-evidence path end to end. The output is
 * a genuine JPEG *container* (SOI, APP1, EOI) with spec-shaped EXIF, but no
 * image data — it is metadata standing in for a photograph, and the library
 * screen labels it as such.
 *
 * It also closes a loop: the parser is verified against an independent encoder
 * (real camera output), and separately against this writer. Agreement with both
 * is a stronger signal than either alone.
 */
object SyntheticJpeg {

    fun withGps(gps: LatLng, utcEpochSeconds: Long, salt: Int = 0): ByteArray {
        val tiff = buildTiff(gps, utcEpochSeconds, salt)
        val app1Length = 2 + 6 + tiff.size
        val out = ArrayList<Byte>(app1Length + 8)

        out.add(0xFF.toByte()); out.add(0xD8.toByte()) // SOI
        out.add(0xFF.toByte()); out.add(0xE1.toByte()) // APP1
        out.add(((app1Length ushr 8) and 0xFF).toByte())
        out.add((app1Length and 0xFF).toByte())
        for (c in "Exif") out.add(c.code.toByte())
        out.add(0); out.add(0)
        for (b in tiff) out.add(b)
        out.add(0xFF.toByte()); out.add(0xD9.toByte()) // EOI

        return out.toByteArray()
    }

    private fun buildTiff(gps: LatLng, utcEpochSeconds: Long, salt: Int): ByteArray {
        val date = civilFromEpochSeconds(utcEpochSeconds)
        val secondOfDay = ((utcEpochSeconds % 86_400L) + 86_400L) % 86_400L
        val hh = (secondOfDay / 3600).toInt()
        val mm = ((secondOfDay % 3600) / 60).toInt()
        val ss = (secondOfDay % 60).toInt()

        val gpsIfdOffset = 8 + 18
        val dataStart = gpsIfdOffset + 2 + 6 * 12 + 4
        val latOffset = dataStart
        val lngOffset = dataStart + 24
        val timeOffset = dataStart + 48
        val dateOffset = dataStart + 72
        val total = dateOffset + 12

        val b = ByteArray(total)
        var p = 0

        fun u8(v: Int) { b[p++] = (v and 0xFF).toByte() }
        fun u16(v: Int) { u8(v ushr 8); u8(v) }
        fun u32(v: Long) { u8((v ushr 24).toInt()); u8((v ushr 16).toInt()); u8((v ushr 8).toInt()); u8(v.toInt()) }
        fun entry(tag: Int, type: Int, count: Long, value: Long) {
            u16(tag); u16(type); u32(count); u32(value)
        }
        // TIFF header, big-endian
        u16(0x4D4D); u16(42); u32(8)

        // IFD0: one entry, the GPS pointer
        u16(1)
        entry(TAG_GPS, 4, 1, gpsIfdOffset.toLong())
        u32(0) // no next IFD

        // GPS IFD
        u16(6)
        // Refs are 2-byte ASCII, so they live inline in the value field.
        u16(1); u16(2); u32(2)
        u8(if (gps.lat >= 0) 'N'.code else 'S'.code); u8(0); u16(0)
        entry(2, 5, 3, latOffset.toLong())
        u16(3); u16(2); u32(2)
        u8(if (gps.lng >= 0) 'E'.code else 'W'.code); u8(0); u16(0)
        entry(4, 5, 3, lngOffset.toLong())
        entry(7, 5, 3, timeOffset.toLong())
        entry(0x001D, 2, 11, dateOffset.toLong())
        u32(0)

        fun writeDms(deg: Double) {
            val a = abs(deg)
            val d = a.toLong()
            val minutes = (a - d) * 60.0
            val m = minutes.toLong()
            val seconds = (minutes - m) * 60.0
            u32(d); u32(1)
            u32(m); u32(1)
            u32((seconds * 1000.0).roundToLong()); u32(1000)
        }

        writeDms(gps.lat)
        writeDms(gps.lng)
        u32(hh.toLong()); u32(1)
        u32(mm.toLong()); u32(1)
        u32(ss.toLong()); u32(1)

        val stamp = "${pad4(date.year)}:${pad2(date.month)}:${pad2(date.day)}"
        for (c in stamp) u8(c.code)
        u8(0)
        // One salt byte inside the padding so two synthetic photos of the same
        // place at the same second still hash differently.
        u8(salt and 0xFF)

        return b
    }

    private const val TAG_GPS = 0x8825

    private fun pad2(v: Int): String = if (v < 10) "0$v" else "$v"

    private fun pad4(v: Int): String = when {
        v >= 1000 -> "$v"
        v >= 100 -> "0$v"
        v >= 10 -> "00$v"
        else -> "000$v"
    }
}
