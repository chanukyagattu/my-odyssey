package app.odyssey.engine

/**
 * Just enough calendar to label a memory, with no dependency.
 *
 * Howard Hinnant's civil_from_days, which is exact for the proleptic Gregorian
 * calendar over the whole range we care about. Pulling in a date-time library
 * for one label would be a poor trade for a Kotlin/Native binary.
 */
private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

data class CivilDate(val year: Int, val month: Int, val day: Int)

fun civilFromEpochSeconds(epochSeconds: Long): CivilDate {
    var days = epochSeconds / 86_400L
    if (epochSeconds < 0 && epochSeconds % 86_400L != 0L) days -= 1

    val z = days + 719_468L
    val era = (if (z >= 0) z else z - 146_096L) / 146_097L
    val doe = z - era * 146_097L
    val yoe = (doe - doe / 1_460L + doe / 36_524L - doe / 146_096L) / 365L
    val y = yoe + era * 400L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val d = doy - (153L * mp + 2L) / 5L + 1L
    val m = if (mp < 10) mp + 3 else mp - 9
    return CivilDate(
        year = (if (m <= 2) y + 1 else y).toInt(),
        month = m.toInt(),
        day = d.toInt(),
    )
}

/** Inverse of [civilFromEpochSeconds]. Used to turn an EXIF GPS datestamp into an instant. */
fun epochSecondsFromCivil(
    year: Int,
    month: Int,
    day: Int,
    hour: Int = 0,
    minute: Int = 0,
    second: Int = 0,
): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (month + (if (month > 2) -3 else 9)).toLong()
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    val days = era * 146_097L + doe - 719_468L
    return days * 86_400L + hour * 3_600L + minute * 60L + second
}

/** e.g. "14 Mar 2026" */
fun formatDate(epochSeconds: Long): String {
    val c = civilFromEpochSeconds(epochSeconds)
    return "${c.day} ${MONTHS[c.month - 1]} ${c.year}"
}
