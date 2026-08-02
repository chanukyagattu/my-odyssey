package app.odyssey.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class DatesTest {

    @Test
    fun knownEpochsConvertCorrectly() {
        assertEquals(CivilDate(1970, 1, 1), civilFromEpochSeconds(0))
        assertEquals(CivilDate(2000, 3, 1), civilFromEpochSeconds(951_868_800))
        assertEquals(CivilDate(2024, 2, 29), civilFromEpochSeconds(1_709_164_800))
        assertEquals(CivilDate(2026, 8, 2), civilFromEpochSeconds(1_785_628_800))
    }

    @Test
    fun timeOfDayDoesNotShiftTheDay() {
        val midnight = 1_785_628_800L
        assertEquals(CivilDate(2026, 8, 2), civilFromEpochSeconds(midnight))
        assertEquals(CivilDate(2026, 8, 2), civilFromEpochSeconds(midnight + 86_399))
        assertEquals(CivilDate(2026, 8, 3), civilFromEpochSeconds(midnight + 86_400))
    }

    @Test
    fun preEpochDatesFloorCorrectly() {
        assertEquals(CivilDate(1969, 12, 31), civilFromEpochSeconds(-1))
        assertEquals(CivilDate(1969, 12, 31), civilFromEpochSeconds(-86_400))
        assertEquals(CivilDate(1969, 12, 30), civilFromEpochSeconds(-86_401))
    }

    @Test
    fun labelsRenderAsExpected() {
        assertEquals("2 Aug 2026", formatDate(1_785_628_800))
        assertEquals("1 Jan 1970", formatDate(0))
        assertEquals("29 Feb 2024", formatDate(1_709_164_800))
    }

    @Test
    fun everyDayOfADecadeRoundTripsThroughTheMonthTable() {
        // Guards the month index: an off-by-one would throw on month 12.
        var t = 1_600_000_000L
        repeat(3_650) {
            val c = civilFromEpochSeconds(t)
            check(c.month in 1..12 && c.day in 1..31)
            formatDate(t)
            t += 86_400
        }
    }
}
