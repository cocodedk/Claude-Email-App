package com.cocode.claudeemailapp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG 2.x contrast ratio between two opaque colours. */
private fun contrast(a: Color, b: Color): Double {
    val l1 = a.luminance().toDouble()
    val l2 = b.luminance().toDouble()
    return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
}

/** Text that sits straight on the app's black background must stay readable (AA: 4.5:1). */
class ThemeContrastTest {

    private val appBackground = Color.Black // the Box behind every screen in AppRoot

    @Test
    fun headingColour_onBlack_meetsAA() {
        assertTrue(contrast(Snow, appBackground) >= 4.5)
    }

    @Test
    fun headingColour_onPitchBlack_meetsAA() {
        assertTrue(contrast(Snow, PitchBlack) >= 4.5)
    }

    @Test
    fun bodyColour_onBlack_meetsAA() {
        assertTrue(contrast(SnowMuted, appBackground) >= 4.5)
    }

    @Test
    fun bodyColour_onPitchBlack_meetsAA() {
        assertTrue(contrast(SnowMuted, PitchBlack) >= 4.5)
    }
}
