package com.cocode.claudeemailapp.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Remember a click-wrapper that fires a [HapticFeedbackType.LongPress] tick
 * and then invokes [onClick]. Used for primary-action buttons (send, steering
 * arm/fire, swipe commits) so the user gets a physical confirm.
 */
@Composable
fun rememberHapticClick(onClick: () -> Unit): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(onClick, haptic) {
        { haptic.tick(); onClick() }
    }
}

private fun HapticFeedback.tick() = performHapticFeedback(HapticFeedbackType.LongPress)
