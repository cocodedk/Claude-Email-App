package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun HeroCard(
    loading: Boolean,
    buckets: AppViewModel.HomeBuckets,
    onCompose: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Inbox", style = MaterialTheme.typography.headlineSmall)
            if (loading) {
                Text(
                    text = "Syncing…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                HeroCounters(buckets = buckets)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(onClick = onCompose, modifier = Modifier.testTag("home_new_message_button")) {
                    Text("New command")
                }
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !loading,
                    modifier = Modifier.testTag("home_refresh_button")
                ) { Text(if (loading) "Refreshing…" else "Refresh") }
                TextButton(onClick = onOpenSettings, modifier = Modifier.testTag("home_settings_button")) {
                    Text("Settings")
                }
            }
        }
    }
}

@Composable
private fun HeroCounters(buckets: AppViewModel.HomeBuckets) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("home_counters"),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        HeroCounter(label = "Active", value = buckets.active.size, accent = MaterialTheme.colorScheme.primary)
        HeroCounter(label = "Waiting", value = buckets.waiting.size, accent = MaterialTheme.colorScheme.secondary)
        HeroCounter(label = "Archived", value = buckets.archived.size, accent = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeroCounter(label: String, value: Int, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = accent
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
