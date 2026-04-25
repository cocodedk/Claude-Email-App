package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun EmptyBucketCard(
    filter: AppViewModel.HomeFilter,
    onCompose: () -> Unit = {}
) {
    val (heading, body) = when (filter) {
        AppViewModel.HomeFilter.ACTIVE -> "Nothing active" to "Send a command to your claude-email service and the reply will land here."
        AppViewModel.HomeFilter.WAITING -> "No conversations need a reply" to "When the agent asks a question, it will show up here."
        AppViewModel.HomeFilter.ARCHIVED -> "Archive is empty" to "Swipe a conversation left to archive it."
    }
    Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(heading, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (filter == AppViewModel.HomeFilter.ACTIVE) {
                Button(
                    onClick = onCompose,
                    modifier = Modifier.testTag("empty_active_send_cta")
                ) { Text("Send first command") }
            }
        }
    }
}
