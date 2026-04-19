package com.cocode.claudeemailapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Date

class FormatTimestampTest {

    private val baseNow = 1_800_000_000_000L

    @Test
    fun nullDate_returnsEmpty() {
        assertEquals("", formatTimestamp(null, now = baseNow))
    }

    @Test
    fun withinAMinute_returnsNow() {
        assertEquals("now", formatTimestamp(Date(baseNow - 30_000), now = baseNow))
    }

    @Test
    fun withinAnHour_returnsMinutes() {
        assertEquals("5m", formatTimestamp(Date(baseNow - 5 * 60_000), now = baseNow))
    }

    @Test
    fun withinADay_returnsHours() {
        assertEquals("3h", formatTimestamp(Date(baseNow - 3 * 60 * 60_000), now = baseNow))
    }

    @Test
    fun withinAWeek_returnsDays() {
        assertEquals("4d", formatTimestamp(Date(baseNow - 4L * 24 * 60 * 60_000), now = baseNow))
    }

    @Test
    fun olderThanAWeek_returnsDate() {
        val olderThanWeek = Date(baseNow - 14L * 24 * 60 * 60_000)
        val formatted = formatTimestamp(olderThanWeek, now = baseNow)
        // Dated format varies by locale; just ensure it's not one of the relative tokens
        assertNotEquals("now", formatted)
        assertNotEquals("6d", formatted)
        assert(formatted.isNotBlank())
    }
}
