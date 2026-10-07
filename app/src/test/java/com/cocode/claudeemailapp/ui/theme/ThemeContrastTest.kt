package com.cocode.claudeemailapp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Text must stay readable (AA: 4.5:1) on the black background and on status pills. */
class ThemeContrastTest {

    private val appBackground = Color.Black // the Box behind every screen in AppRoot

    // --- Text straight on the background -------------------------------------------------------

    @Test
    fun headingColour_onBlack_meetsAA() {
        assertTrue(contrastRatio(Snow, appBackground) >= MIN_TEXT_CONTRAST)
    }

    @Test
    fun headingColour_onPitchBlack_meetsAA() {
        assertTrue(contrastRatio(Snow, PitchBlack) >= MIN_TEXT_CONTRAST)
    }

    @Test
    fun bodyColour_onBlack_meetsAA() {
        assertTrue(contrastRatio(SnowMuted, appBackground) >= MIN_TEXT_CONTRAST)
    }

    @Test
    fun bodyColour_onPitchBlack_meetsAA() {
        assertTrue(contrastRatio(SnowMuted, PitchBlack) >= MIN_TEXT_CONTRAST)
    }

    // --- Status pills --------------------------------------------------------------------------
    // The muted pills (agent stale/offline, "N messages", idle) pass the theme's outline colours.

    private fun pill(accent: Color, surface: Color) =
        pillColors(accent, surface, mutedText = SnowMuted, mutedLine = BorderStrong)

    private fun textContrast(accent: Color, surface: Color): Double {
        val colors = pill(accent, surface)
        return contrastRatio(colors.text, colors.fill.compositeOver(surface))
    }

    @Test
    fun mutedPill_outline_onCard_meetsAA() {
        assertTrue(textContrast(BorderStrong, Carbon) >= MIN_TEXT_CONTRAST)
    }

    @Test
    fun mutedPill_outline_onRaisedCard_meetsAA() {
        assertTrue(textContrast(BorderStrong, Graphite) >= MIN_TEXT_CONTRAST)
    }

    @Test
    fun mutedPill_outlineVariant_onCard_meetsAA() {
        assertTrue(textContrast(BorderSoft, Carbon) >= MIN_TEXT_CONTRAST)
    }

    @Test
    fun mutedPill_outlineVariant_onRaisedCard_meetsAA() {
        assertTrue(textContrast(BorderSoft, Graphite) >= MIN_TEXT_CONTRAST)
    }

    @Test
    fun mutedPill_isNotDimText() {
        assertEquals(SnowMuted, pill(BorderStrong, Carbon).text)
    }

    @Test
    fun mutedPill_keepsAVisibleOutline() {
        assertEquals(BorderStrong, pill(BorderSoft, Carbon).border)
    }

    @Test
    fun strongPill_keepsItsAccentColour_cyan() {
        assertEquals(SignalCyan, pill(SignalCyan, Carbon).text)
    }

    @Test
    fun strongPill_keepsItsAccentColour_amber() {
        assertEquals(SignalAmber, pill(SignalAmber, Carbon).text)
    }

    @Test
    fun strongPill_keepsItsAccentColour_red() {
        assertEquals(SignalRed, pill(SignalRed, Carbon).text)
    }

    @Test
    fun strongPill_cyan_meetsAA() {
        assertTrue(textContrast(SignalCyan, Carbon) >= MIN_TEXT_CONTRAST)
    }

    @Test
    fun strongPill_amber_meetsAA() {
        assertTrue(textContrast(SignalAmber, Carbon) >= MIN_TEXT_CONTRAST)
    }

    @Test
    fun strongPill_red_meetsAA() {
        assertTrue(textContrast(SignalRed, Carbon) >= MIN_TEXT_CONTRAST)
    }
}
