package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.protocol.ProgressInfo
import kotlin.math.roundToInt

/**
 * Human-readable label for a [ProgressInfo]. Priority:
 * `current/total` > `percent` > `label`. Returns null when nothing
 * meaningful is set so the UI can skip rendering.
 */
fun progressLabel(p: ProgressInfo): String? {
    val core: String? = when {
        p.current != null && p.total != null -> "${p.current}/${p.total}"
        p.percent != null -> "${p.percent.roundToInt()}%"
        else -> null
    }
    return when {
        core != null && p.label != null -> "$core ${p.label}"
        core != null -> core
        p.label != null -> p.label
        else -> null
    }
}

/**
 * 0..1 fraction for `LinearProgressIndicator`. Returns null when the bar
 * should be omitted (no usable numeric signal). Out-of-range percent values
 * are clamped — backends emitting drift shouldn't crash the UI.
 */
fun progressFraction(p: ProgressInfo): Float? {
    if (p.current != null && p.total != null && p.total > 0) {
        return (p.current.toFloat() / p.total.toFloat()).coerceIn(0f, 1f)
    }
    if (p.percent != null) {
        return (p.percent.toFloat() / 100f).coerceIn(0f, 1f)
    }
    return null
}

/**
 * Renders the progress payload of a `kind=progress` envelope. Label-only
 * snapshots (no numeric signal) render just the text — no indeterminate bar,
 * since these are point-in-time updates, not live operations the UI is
 * polling on.
 */
@Composable
internal fun ProgressDisplay(progress: ProgressInfo) {
    val label = progressLabel(progress) ?: return
    val fraction = progressFraction(progress)
    if (fraction == null) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().testTag("thread_message_progress")
        )
        return
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().testTag("thread_message_progress")
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    }
}
