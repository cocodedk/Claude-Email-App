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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.data.MailCredentials
import com.cocode.claudeemailapp.data.PendingCommand
import com.cocode.claudeemailapp.data.PendingStatus
import java.text.DateFormat
import java.util.Date

@Composable
fun DiagnosticsScreen(
    credentials: MailCredentials?,
    inbox: AppViewModel.InboxState,
    sendError: String?,
    pending: List<PendingCommand>,
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
                OutlinedButton(onClick = onBack, modifier = Modifier.testTag("diagnostics_back")) { Text("Back") }
            }
        }
        item { DiagSection("Sync") {
            DiagRow("Last sync", formatTimestamp(inbox.lastFetchedAt?.let(::Date)).ifBlank { "never" })
            DiagRow("Status", if (inbox.loading) "syncing" else if (inbox.error != null) "stalled" else "idle")
            inbox.error?.let { DiagRow("Sync error", it) }
            DiagRow("Messages cached", inbox.messages.size.toString())
        } }
        item { DiagSection("Send") {
            DiagRow("Last send error", sendError ?: "none")
            DiagRow("Pending tasks", pending.count { it.status !in setOf(PendingStatus.DONE, PendingStatus.FAILED, PendingStatus.ERROR) }.toString())
            DiagRow("Failed pending", pending.count { it.status == PendingStatus.FAILED || it.status == PendingStatus.ERROR }.toString())
        } }
        item { DiagSection("Connection") {
            credentials?.let { c ->
                DiagRow("IMAP", "${c.imapHost}:${c.imapPort}")
                DiagRow("SMTP", "${c.smtpHost}:${c.smtpPort} ${if (c.smtpUseStartTls) "(STARTTLS)" else "(TLS)"}")
                DiagRow("Service address", c.serviceAddress.ifBlank { "(not set)" })
            } ?: DiagRow("Status", "no credentials")
        } }
        item { DiagSection("App") {
            DiagRow("Version", appVersion(context))
            DiagRow("Package", context.packageName)
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

@Suppress("unused")
private fun formatAbsolute(date: Date?): String =
    date?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(it) } ?: ""
