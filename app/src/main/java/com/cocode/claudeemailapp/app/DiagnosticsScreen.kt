package com.cocode.claudeemailapp.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.R
import com.cocode.claudeemailapp.data.MailCredentials
import com.cocode.claudeemailapp.data.PendingCommand
import com.cocode.claudeemailapp.data.PendingStatus
import java.util.Date

@Composable
fun DiagnosticsScreen(
    credentials: MailCredentials?,
    inbox: AppViewModel.InboxState,
    sendError: String?,
    pending: List<PendingCommand>,
    syncIntervalMs: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("diagnostics_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onBack, modifier = Modifier.testTag("diagnostics_back")) {
                    Text(stringResource(R.string.action_back))
                }
            }
        }
        item { DiagSection(stringResource(R.string.diag_section_refresh)) {
            DiagRow(
                stringResource(R.string.diag_last_refresh),
                formatTimestamp(inbox.lastFetchedAt?.let(::Date)).ifBlank { stringResource(R.string.diag_never) }
            )
            DiagRow(
                stringResource(R.string.diag_status),
                stringResource(
                    when {
                        inbox.loading -> R.string.diag_status_refreshing
                        inbox.error != null -> R.string.diag_status_stalled
                        else -> R.string.diag_status_idle
                    }
                )
            )
            DiagRow(
                stringResource(R.string.diag_auto_refresh),
                if (syncIntervalMs <= 0) {
                    stringResource(R.string.diag_auto_refresh_manual)
                } else {
                    val seconds = (syncIntervalMs / 1000).toInt()
                    pluralStringResource(R.plurals.diag_auto_refresh_every, seconds, seconds)
                }
            )
            inbox.error?.let { DiagRow(stringResource(R.string.diag_refresh_error), it) }
            DiagRow(stringResource(R.string.diag_messages_loaded), inbox.messages.size.toString())
        } }
        item { DiagSection(stringResource(R.string.diag_section_send)) {
            DiagRow(stringResource(R.string.diag_last_send_error), sendError ?: stringResource(R.string.diag_none))
            DiagRow(
                stringResource(R.string.diag_pending),
                pending.count { it.status !in setOf(PendingStatus.DONE, PendingStatus.FAILED, PendingStatus.ERROR) }.toString()
            )
            DiagRow(
                stringResource(R.string.diag_failed_pending),
                pending.count { it.status == PendingStatus.FAILED || it.status == PendingStatus.ERROR }.toString()
            )
        } }
        item { DiagSection(stringResource(R.string.diag_section_connection)) {
            credentials?.let { c ->
                DiagRow(stringResource(R.string.diag_imap), "${c.imapHost}:${c.imapPort}")
                DiagRow(
                    stringResource(R.string.diag_smtp),
                    stringResource(
                        if (c.smtpUseStartTls) R.string.diag_smtp_starttls else R.string.diag_smtp_tls,
                        c.smtpHost,
                        c.smtpPort
                    )
                )
                DiagRow(
                    stringResource(R.string.diag_service_address),
                    c.serviceAddress.ifBlank { stringResource(R.string.value_not_set) }
                )
            } ?: DiagRow(stringResource(R.string.diag_status), stringResource(R.string.diag_no_credentials))
        } }
        item { DiagSection(stringResource(R.string.diag_section_app)) {
            DiagRow(stringResource(R.string.diag_version), appVersion(context))
            DiagRow(stringResource(R.string.diag_package), context.packageName)
        } }
    }
}

@Composable
private fun DiagSection(title: String, content: @Composable () -> Unit) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Suppress("DEPRECATION")
private fun appVersion(context: Context): String = try {
    val pi = context.packageManager.getPackageInfo(context.packageName, 0)
    "${pi.versionName ?: "?"} (${pi.versionCode})"
} catch (_: Throwable) {
    "?"
}

