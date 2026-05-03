package com.cocode.claudeemailapp.app

import com.cocode.claudeemailapp.protocol.ProgressInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressDisplayTest {

    // --- progressLabel: text shown alongside / instead of the bar ---

    @Test fun `current and total render as fraction`() =
        assertEquals("3/7", progressLabel(ProgressInfo(current = 3, total = 7)))

    @Test fun `current+total+label render as fraction with suffix`() =
        assertEquals("3/7 tests passed", progressLabel(ProgressInfo(current = 3, total = 7, label = "tests passed")))

    @Test fun `percent only renders rounded`() =
        assertEquals("43%", progressLabel(ProgressInfo(percent = 42.8)))

    @Test fun `percent with label`() =
        assertEquals("43% tests passed", progressLabel(ProgressInfo(percent = 42.8, label = "tests passed")))

    @Test fun `label only renders alone`() =
        assertEquals("starting up", progressLabel(ProgressInfo(label = "starting up")))

    @Test fun `current+total beats percent when both present`() =
        assertEquals("3/7", progressLabel(ProgressInfo(current = 3, total = 7, percent = 99.0)))

    @Test fun `empty progress renders nothing`() =
        assertNull(progressLabel(ProgressInfo()))

    // --- progressFraction: 0..1 for LinearProgressIndicator, null = indeterminate ---

    @Test fun `current over total derives fraction`() =
        assertEquals(0.5f, progressFraction(ProgressInfo(current = 2, total = 4))!!, 0.001f)

    @Test fun `percent derives fraction`() =
        assertEquals(0.428f, progressFraction(ProgressInfo(percent = 42.8))!!, 0.001f)

    @Test fun `current+total beats percent for fraction`() =
        assertEquals(0.5f, progressFraction(ProgressInfo(current = 2, total = 4, percent = 99.0))!!, 0.001f)

    @Test fun `zero total avoids divide-by-zero — returns null`() =
        assertNull(progressFraction(ProgressInfo(current = 0, total = 0)))

    @Test fun `current without total returns null`() =
        assertNull(progressFraction(ProgressInfo(current = 3)))

    @Test fun `label-only returns null fraction`() =
        assertNull(progressFraction(ProgressInfo(label = "starting")))

    @Test fun `percent over 100 clamps to 1`() =
        assertEquals(1.0f, progressFraction(ProgressInfo(percent = 150.0))!!, 0.001f)

    @Test fun `percent below 0 clamps to 0`() =
        assertEquals(0.0f, progressFraction(ProgressInfo(percent = -5.0))!!, 0.001f)
}
