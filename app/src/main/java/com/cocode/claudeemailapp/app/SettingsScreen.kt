package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.data.MailCredentials

data class SyncOption(val ms: Long, val label: String)

val SyncOptions = listOf(
    SyncOption(0L, "Manual"),
    SyncOption(30_000L, "30 sec"),
    SyncOption(60_000L, "1 min"),
    SyncOption(300_000L, "5 min")
)

@Composable
fun SettingsScreen(
    credentials: MailCredentials,
    syncIntervalMs: Long,
    onSyncIntervalChange: (Long) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onEdit: () -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("settings_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) {
                    Text("Back")
                }
            }
        }
        item { SectionCard("Account") {
            Entry("Display name", credentials.displayName.ifBlank { "(not set)" })
            Entry("Email", credentials.emailAddress)
        } }
        item { SectionCard("IMAP") {
            Entry("Host", credentials.imapHost)
            Entry("Port", credentials.imapPort.toString())
            Entry("Security", "Implicit TLS")
        } }
        item { SectionCard("SMTP") {
            Entry("Host", credentials.smtpHost)
            Entry("Port", credentials.smtpPort.toString())
            Entry("Security", if (credentials.smtpUseStartTls) "STARTTLS" else "Implicit TLS")
        } }
        item { SectionCard("claude-email service") {
            Entry("Address", credentials.serviceAddress.ifBlank { "(not set)" })
            Entry("Shared secret", if (credentials.sharedSecret.isBlank()) "(not set)" else "••••••")
        } }
        item { SectionCard("Sync") {
            SyncIntervalPicker(selectedMs = syncIntervalMs, onSelect = onSyncIntervalChange)
        } }
        item { SectionCard("Notifications") {
            NotificationsToggle(
                enabled = notificationsEnabled,
                onChange = onNotificationsEnabledChange
            )
        } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onEdit, modifier = Modifier.testTag("settings_edit")) {
                    Text("Edit credentials")
                }
                OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.testTag("settings_diagnostics")) {
                    Text("Diagnostics")
                }
                TextButton(onClick = onSignOut, modifier = Modifier.testTag("settings_signout")) {
                    Text("Sign out")
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ColumnScope.Entry(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ColumnScope.SyncIntervalPicker(selectedMs: Long, onSelect: (Long) -> Unit) {
    Text(
        text = "Background refresh",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SyncOptions.forEach { opt ->
            FilterChip(
                selected = opt.ms == selectedMs,
                onClick = { onSelect(opt.ms) },
                label = { Text(opt.label) },
                colors = FilterChipDefaults.filterChipColors(),
                modifier = Modifier.testTag("settings_sync_${opt.ms}")
            )
        }
    }
    Text(
        text = "Foreground refreshes every 15 s. Pull-to-refresh works regardless.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ColumnScope.NotificationsToggle(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.padding(end = 12.dp)) {
            Text(
                text = "Notify on replies",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Get a device notification when an agent reply arrives. Stops when you close the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onChange,
            modifier = Modifier.testTag("settings_notifications_toggle")
        )
    }
}
