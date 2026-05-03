package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.app.steering.SteeringChip
import com.cocode.claudeemailapp.app.steering.SteeringChipVariant
import com.cocode.claudeemailapp.data.Conversation

@Composable
internal fun ConversationHeaderCard(
    conversation: Conversation,
    isArchived: Boolean,
    onBack: () -> Unit,
    onArchiveToggle: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).testTag("conversation_subject")
                )
                OutlinedButton(onClick = onBack, modifier = Modifier.testTag("conversation_back")) {
                    Text("Back")
                }
            }
            Text(
                text = "${conversation.agentDisplay} · ${conversation.messageCount} ${if (conversation.messageCount == 1) "message" else "messages"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SteeringChip(
                    label = if (isArchived) "Unarchive" else "Archive",
                    onClick = onArchiveToggle,
                    variant = SteeringChipVariant.Default,
                    modifier = Modifier.testTag("conversation_archive")
                )
            }
        }
    }
}
