package com.cocode.claudeemailapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

/** Tests how long ago a message was; the text for each age lives in strings.xml (time_*). */
class FormatTimestampTest {

    private val baseNow = 1_800_000_000_000L

    @Test
    fun nullDate_returnsNull() {
        assertNull(ageOf(null, now = baseNow))
    }

    @Test
    fun withinAMinute_returnsNow() {
        assertEquals(Age.Now, ageOf(Date(baseNow - 30_000), now = baseNow))
    }

    @Test
    fun withinAnHour_returnsMinutes() {
        assertEquals(Age.Minutes(5), ageOf(Date(baseNow - 5 * 60_000), now = baseNow))
    }

    @Test
    fun withinADay_returnsHours() {
        assertEquals(Age.Hours(3), ageOf(Date(baseNow - 3 * 60 * 60_000), now = baseNow))
    }

    @Test
    fun withinAWeek_returnsDays() {
        assertEquals(Age.Days(4), ageOf(Date(baseNow - 4L * 24 * 60 * 60_000), now = baseNow))
    }

    @Test
    fun olderThanAWeek_returnsTheDate() {
        val olderThanWeek = Date(baseNow - 14L * 24 * 60 * 60_000)
        assertEquals(Age.Dated(olderThanWeek), ageOf(olderThanWeek, now = baseNow))
    }
}
