package com.cocode.claudeemailapp.app

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.mail.FetchedMessage

private const val LONG_BODY_THRESHOLD = 1500
private const val LONG_BODY_PREVIEW_CHARS = 1200

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ThreadMessageCard(message: FetchedMessage, isFromSelf: Boolean) {
    val container = if (isFromSelf) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var expanded by remember(message.messageId) { mutableStateOf(false) }
    val body = message.body.ifBlank { "(no text content)" }
    val isLong = body.length > LONG_BODY_THRESHOLD
    val visible = if (isLong && !expanded) body.take(LONG_BODY_PREVIEW_CHARS) else body

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = container),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("thread_message_card")
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = {
                    clipboard.setText(AnnotatedString(message.body))
                    Toast.makeText(context, "Copied message", Toast.LENGTH_SHORT).show()
                }
            )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HeaderRow(message, isFromSelf)
            EnvelopeRow(message)
            Text(text = visible, style = MaterialTheme.typography.bodyLarge)
            if (isLong) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.testTag("thread_message_expand_toggle")
                ) { Text(if (expanded) "Collapse" else "Expand · ${body.length - LONG_BODY_PREVIEW_CHARS} more chars") }
            }
        }
    }
}

@Composable
private fun HeaderRow(message: FetchedMessage, isFromSelf: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(
            text = if (isFromSelf) "You" else message.fromName?.takeIf(String::isNotBlank) ?: message.from,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = if (isFromSelf) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
        )
        Text(
            text = formatTimestamp(message.sentAt ?: message.receivedAt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EnvelopeRow(message: FetchedMessage) {
    val env = message.envelope ?: return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KindChip(kind = env.kind)
        env.taskId?.let { Text(text = "task #$it", style = MaterialTheme.typography.labelMedium) }
    }
}
