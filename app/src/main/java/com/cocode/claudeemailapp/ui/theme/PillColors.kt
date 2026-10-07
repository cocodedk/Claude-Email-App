package com.cocode.claudeemailapp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/** WCAG AA for normal-size text. */
const val MIN_TEXT_CONTRAST = 4.5

/** WCAG 2.x contrast ratio between two opaque colours. */
fun contrastRatio(a: Color, b: Color): Double {
    val l1 = a.luminance().toDouble()
    val l2 = b.luminance().toDouble()
    return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
}

/** How a status pill is painted: the text, the tinted fill behind it and the outline. */
data class PillColors(val text: Color, val fill: Color, val border: Color)

private const val FILL_ALPHA = 0.15f
private const val BORDER_ALPHA = 0.35f
private const val MUTED_FILL_ALPHA = 0.25f

/**
 * Colours for a pill whose accent is [accent], on a card of colour [surface]. A strong accent
 * tints the fill and outline and colours the text. A dim accent (the theme's outline colours) would
 * make the text unreadable, so the pill stays muted through its fill and outline ([mutedLine]) while
 * its text uses [mutedText], which reads at AA contrast on that fill.
 */
fun pillColors(accent: Color, surface: Color, mutedText: Color, mutedLine: Color): PillColors {
    val fill = accent.copy(alpha = FILL_ALPHA)
    if (contrastRatio(accent, fill.compositeOver(surface)) >= MIN_TEXT_CONTRAST) {
        return PillColors(text = accent, fill = fill, border = accent.copy(alpha = BORDER_ALPHA))
    }
    return PillColors(text = mutedText, fill = mutedLine.copy(alpha = MUTED_FILL_ALPHA), border = mutedLine)
}
