package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.app.steering.SteeringChip
import com.cocode.claudeemailapp.app.steering.SteeringChipVariant
import com.cocode.claudeemailapp.mail.FetchedMessage
import com.cocode.claudeemailapp.protocol.Kinds

private const val MAX_REPLIES = 4
private const val MAX_REPLY_LENGTH = 30

/**
 * Defensive client-side validation of `meta.suggested_replies` per the wire
 * spec (≤4 chips, ≤30 chars each). Backend enforces too, but the app trims,
 * drops blanks/overlong/duplicates, and caps to [MAX_REPLIES] — drift in
 * either direction shouldn't crash the UI or render unusable chips.
 */
fun validSuggestedReplies(raw: List<String>?): List<String> {
    if (raw.isNullOrEmpty()) return emptyList()
    return raw.asSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && it.length <= MAX_REPLY_LENGTH }
        .distinct()
        .take(MAX_REPLIES)
        .toList()
}

/** Chips attach to the latest message only — keeps stale questions from showing chips. */
fun pickSuggestedReplies(messages: List<FetchedMessage>): List<String> {
    val env = messages.lastOrNull()?.envelope ?: return emptyList()
    if (env.kind != Kinds.QUESTION) return emptyList()
    return validSuggestedReplies(env.meta.suggestedReplies)
}

@Composable
internal fun SuggestedRepliesRow(
    replies: List<String>,
    enabled: Boolean,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (replies.isEmpty()) return
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("suggested_replies_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        replies.forEach { chip ->
            SteeringChip(
                label = chip,
                onClick = { onTap(chip) },
                variant = SteeringChipVariant.Default,
                enabled = enabled,
                modifier = Modifier.testTag("suggested_reply_chip")
            )
        }
    }
}
