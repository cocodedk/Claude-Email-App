package com.cocode.claudeemailapp.app

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.R
import com.cocode.claudeemailapp.data.MailCredentials

data class SyncOption(val ms: Long, @StringRes val labelRes: Int)

val SyncOptions = listOf(
    SyncOption(0L, R.string.settings_sync_manual),
    SyncOption(30_000L, R.string.settings_sync_30s),
    SyncOption(60_000L, R.string.settings_sync_1m),
    SyncOption(300_000L, R.string.settings_sync_5m)
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
    val notSet = stringResource(R.string.value_not_set)
    val implicitTls = stringResource(R.string.settings_tls_implicit)
    val host = stringResource(R.string.settings_host)
    val port = stringResource(R.string.settings_port)
    val security = stringResource(R.string.settings_security)
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("settings_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) {
                    Text(stringResource(R.string.action_back))
                }
            }
        }
        item { SectionCard(stringResource(R.string.settings_section_account)) {
            Entry(stringResource(R.string.settings_display_name), credentials.displayName.ifBlank { notSet })
            Entry(stringResource(R.string.settings_email), credentials.emailAddress)
        } }
        item { SectionCard(stringResource(R.string.setup_section_imap)) {
            Entry(host, credentials.imapHost)
            Entry(port, credentials.imapPort.toString())
            Entry(security, implicitTls)
        } }
        item { SectionCard(stringResource(R.string.setup_section_smtp)) {
            Entry(host, credentials.smtpHost)
            Entry(port, credentials.smtpPort.toString())
            Entry(
                security,
                if (credentials.smtpUseStartTls) stringResource(R.string.settings_tls_starttls) else implicitTls
            )
        } }
        item { SectionCard(stringResource(R.string.settings_section_service)) {
            Entry(stringResource(R.string.settings_address), credentials.serviceAddress.ifBlank { notSet })
            Entry(
                stringResource(R.string.settings_shared_secret),
                if (credentials.sharedSecret.isBlank()) notSet else "••••••"
            )
        } }
        item { SectionCard(stringResource(R.string.settings_section_sync)) {
            SyncIntervalPicker(selectedMs = syncIntervalMs, onSelect = onSyncIntervalChange)
        } }
        item { SectionCard(stringResource(R.string.settings_section_notifications)) {
            NotificationsToggle(
                enabled = notificationsEnabled,
                onChange = onNotificationsEnabledChange
            )
        } }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onEdit, modifier = Modifier.testTag("settings_edit")) {
                    Text(stringResource(R.string.settings_edit_credentials))
                }
                OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.testTag("settings_diagnostics")) {
                    Text(stringResource(R.string.action_diagnostics))
                }
                TextButton(onClick = onSignOut, modifier = Modifier.testTag("settings_signout")) {
                    Text(stringResource(R.string.settings_sign_out))
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
        text = stringResource(R.string.settings_sync_label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SyncOptions.forEach { opt ->
            FilterChip(
                selected = opt.ms == selectedMs,
                onClick = { onSelect(opt.ms) },
                label = { Text(stringResource(opt.labelRes)) },
                colors = FilterChipDefaults.filterChipColors(),
                modifier = Modifier.testTag("settings_sync_${opt.ms}")
            )
        }
    }
    Text(
        text = stringResource(R.string.settings_sync_hint),
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
                text = stringResource(R.string.settings_notify_title),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.settings_notify_body),
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
