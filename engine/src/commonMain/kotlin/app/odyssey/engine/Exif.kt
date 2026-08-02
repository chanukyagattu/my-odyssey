package app.odyssey.engine

/**
 * Minimal JPEG/APP1 EXIF reader, in common Kotlin.
 *
 * This is what turns PHOTO_VERIFIED from a label into evidence: a photo whose
 * embedded coordinates land inside a place's geofence, at a UTC instant inside
 * the visit window, corroborates that visit.
 *
 * Two deliberate choices:
 *
 *  - **Only the GPS timestamp counts.** `DateTimeOriginal` has no time zone, so
 *    it cannot be compared to a visit window without guessing an offset.
 *    `GPSDateStamp` + `GPSTimeStamp` are UTC by specification. Without them we
 *    decline to upgrade evidence — verification fails closed.
 *  - **Parsed here, not through ImageIO.** One implementation, testable on the
 *    JVM, no platform interop in the trust path. It also means the parser is
 *    reading attacker-controlled bytes, so every read is bounds-checked and any
 *    malformation returns "no data" rather than throwing.
 *
 * EXIF is user-writable, which is exactly why photo evidence ranks below GPS
 * in the hierarchy rather than beside it.
 */
data class ExifData(
    val gps: LatLng? = null,
    /** UTC, from GPSDateStamp + GPSTimeStamp only. */
    val utcEpochSeconds: Long? = null,
    /** DateTimeOriginal as written by the camera. Zoneless — display only, never trusted. */
    val cameraTimestampText: String? = null,
) {
    val isEmpty: Boolean get() = gps == null && utcEpochSeconds == null && cameraTimestampText == null
}

private const val TAG_EXIF_IFD = 0x8769
private const val TAG_GPS_IFD = 0x8825
private const val TAG_DATETIME_ORIGINAL = 0x9003
private const val GPS_LAT_REF = 0x0001
private const val GPS_LAT = 0x0002
private const val GPS_LNG_REF = 0x0003
private const val GPS_LNG = 0x0004
private const val GPS_TIMESTAMP = 0x0007
private const val GPS_DATESTAMP = 0x001D

private class Tiff(val bytes: ByteArray, val base: Int, val little: Boolean) {

    fun has(offset: Int, length: Int): Boolean {
        val start = base + offset
        return offset >= 0 && length >= 0 && start >= 0 && start + length <= bytes.size
    }

    fun u8(offset: Int): Int = bytes[base + offset].toInt() and 0xFF

    fun u16(offset: Int): Int =
        if (little) u8(offset) or (u8(offset + 1) shl 8) else (u8(offset) shl 8) or u8(offset + 1)

    fun u32(offset: Int): Long = if (little) {
        u8(offset).toLong() or (u8(offset + 1).toLong() shl 8) or
            (u8(offset + 2).toLong() shl 16) or (u8(offset + 3).toLong() shl 24)
    } else {
        (u8(offset).toLong() shl 24) or (u8(offset + 1).toLong() shl 16) or
            (u8(offset + 2).toLong() shl 8) or u8(offset + 3).toLong()
    }
}

private class Entry(val tag: Int, val type: Int, val count: Long, val valueOffset: Int)

private fun typeSize(type: Int): Int = when (type) {
    1, 2, 6, 7 -> 1
    3, 8 -> 2
    4, 9, 11 -> 4
    5, 10, 12 -> 8
    else -> 0
}

private fun readIfd(t: Tiff, ifdOffset: Int): List<Entry> {
    if (!t.has(ifdOffset, 2)) return emptyList()
    val count = t.u16(ifdOffset)
    if (count <= 0 || count > 512) return emptyList()
    if (!t.has(ifdOffset + 2, count * 12)) return emptyList()

    val out = ArrayList<Entry>(count)
    for (i in 0 until count) {
        val e = ifdOffset + 2 + i * 12
        val type = t.u16(e + 2)
        val n = t.u32(e + 4)
        val size = typeSize(type)
        if (size == 0 || n <= 0 || n > 65_536) continue
        val byteCount = size * n
        val valueOffset = if (byteCount <= 4) e + 8 else t.u32(e + 8).toInt()
        if (!t.has(valueOffset, byteCount.toInt())) continue
        out.add(Entry(t.u16(e), type, n, valueOffset))
    }
    return out
}

private fun ascii(t: Tiff, e: Entry): String? {
    if (e.type != 2) return null
    val sb = StringBuilder()
    for (i in 0 until e.count.toInt()) {
        val c = t.u8(e.valueOffset + i)
        if (c == 0) break
        sb.append(c.toChar())
    }
    return sb.toString().ifEmpty { null }
}

private fun rationals(t: Tiff, e: Entry, expected: Int): DoubleArray? {
    if (e.type != 5 && e.type != 10) return null
    if (e.count.toInt() != expected) return null
    val out = DoubleArray(expected)
    for (i in 0 until expected) {
        val o = e.valueOffset + i * 8
        val num = t.u32(o)
        val den = t.u32(o + 4)
        if (den == 0L) return null
        out[i] = num.toDouble() / den.toDouble()
    }
    return out
}

private fun degrees(dms: DoubleArray, ref: String?): Double? {
    if (ref == null) return null
    val value = dms[0] + dms[1] / 60.0 + dms[2] / 3600.0
    return when (ref.trim().uppercase()) {
        "N", "E" -> value
        "S", "W" -> -value
        else -> null
    }
}

/** Never throws. Malformed or absent metadata yields an empty [ExifData]. */
fun parseJpegExif(bytes: ByteArray): ExifData {
    if (bytes.size < 12) return ExifData()
    if ((bytes[0].toInt() and 0xFF) != 0xFF || (bytes[1].toInt() and 0xFF) != 0xD8) return ExifData()

    var i = 2
    var app1 = -1
    while (i + 4 <= bytes.size) {
        if ((bytes[i].toInt() and 0xFF) != 0xFF) break
        val marker = bytes[i + 1].toInt() and 0xFF
        if (marker == 0xD8 || (marker in 0xD0..0xD7) || marker == 0x01) {
            i += 2
            continue
        }
        if (marker == 0xDA || marker == 0xD9) break // start of scan: no more metadata
        val length = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
        if (length < 2 || i + 2 + length > bytes.size) break
        if (marker == 0xE1 && length >= 8) {
            val p = i + 4
            if (bytes[p].toInt() == 'E'.code && bytes[p + 1].toInt() == 'x'.code &&
                bytes[p + 2].toInt() == 'i'.code && bytes[p + 3].toInt() == 'f'.code &&
                bytes[p + 4].toInt() == 0 && bytes[p + 5].toInt() == 0
            ) {
                app1 = p + 6
                break
            }
        }
        i += 2 + length
    }
    if (app1 < 0 || app1 + 8 > bytes.size) return ExifData()

    val b0 = bytes[app1].toInt() and 0xFF
    val b1 = bytes[app1 + 1].toInt() and 0xFF
    val little = when {
        b0 == 0x49 && b1 == 0x49 -> true
        b0 == 0x4D && b1 == 0x4D -> false
        else -> return ExifData()
    }
    val t = Tiff(bytes, app1, little)
    if (t.u16(2) != 42) return ExifData()
    val ifd0 = t.u32(4).toInt()

    var gps: LatLng? = null
    var utc: Long? = null
    var cameraText: String? = null

    for (entry in readIfd(t, ifd0)) {
        when (entry.tag) {
            TAG_GPS_IFD -> {
                val gpsEntries = readIfd(t, t.u32(entry.valueOffset).toInt())
                var latRef: String? = null
                var lngRef: String? = null
                var lat: DoubleArray? = null
                var lng: DoubleArray? = null
                var time: DoubleArray? = null
                var date: String? = null
                for (g in gpsEntries) {
                    when (g.tag) {
                        GPS_LAT_REF -> latRef = ascii(t, g)
                        GPS_LNG_REF -> lngRef = ascii(t, g)
                        GPS_LAT -> lat = rationals(t, g, 3)
                        GPS_LNG -> lng = rationals(t, g, 3)
                        GPS_TIMESTAMP -> time = rationals(t, g, 3)
                        GPS_DATESTAMP -> date = ascii(t, g)
                    }
                }
                if (lat != null && lng != null) {
                    val la = degrees(lat, latRef)
                    val lo = degrees(lng, lngRef)
                    if (la != null && lo != null && la in -90.0..90.0 && lo in -180.0..180.0) {
                        gps = LatLng(la, lo)
                    }
                }
                if (date != null && time != null) {
                    val parts = date.split(":", "-")
                    if (parts.size == 3) {
                        val y = parts[0].toIntOrNull()
                        val mo = parts[1].toIntOrNull()
                        val d = parts[2].trim().toIntOrNull()
                        if (y != null && mo != null && d != null && mo in 1..12 && d in 1..31) {
                            utc = epochSecondsFromCivil(
                                y, mo, d,
                                time[0].toInt(), time[1].toInt(), time[2].toInt(),
                            )
                        }
                    }
                }
            }

            TAG_EXIF_IFD -> {
                for (x in readIfd(t, t.u32(entry.valueOffset).toInt())) {
                    if (x.tag == TAG_DATETIME_ORIGINAL) cameraText = ascii(t, x)
                }
            }
        }
    }

    return ExifData(gps = gps, utcEpochSeconds = utc, cameraTimestampText = cameraText)
}
