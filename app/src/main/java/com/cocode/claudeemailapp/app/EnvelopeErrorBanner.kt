package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.protocol.EnvelopeError

@Composable
fun EnvelopeErrorBanner(
    error: EnvelopeError,
    onRetry: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onEditCommand: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {}
) {
    val ui = describeEnvelopeError(error)
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().testTag("envelope_error_banner")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = ui.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = ui.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            ui.hint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BannerAction(
                    action = ui.action,
                    onRetry = onRetry,
                    onOpenSettings = onOpenSettings,
                    onEditCommand = onEditCommand
                )
                if (ui.showDiagnostics) {
                    TextButton(
                        onClick = onOpenDiagnostics,
                        modifier = Modifier.testTag("envelope_error_diagnostics")
                    ) {
                        Text("Diagnostics")
                    }
                }
            }
            Text(
                text = "code: ${error.code}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun BannerAction(
    action: UiErrorAction,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditCommand: () -> Unit
) {
    when (action) {
        UiErrorAction.Retry -> Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onErrorContainer,
                contentColor = MaterialTheme.colorScheme.errorContainer
            ),
            modifier = Modifier.testTag("envelope_error_retry")
        ) { Text("Retry") }
        UiErrorAction.OpenSettings -> Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onErrorContainer,
                contentColor = MaterialTheme.colorScheme.errorContainer
            ),
            modifier = Modifier.testTag("envelope_error_open_settings")
        ) { Text("Open Settings") }
        UiErrorAction.EditCommand -> Button(
            onClick = onEditCommand,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onErrorContainer,
                contentColor = MaterialTheme.colorScheme.errorContainer
            ),
            modifier = Modifier.testTag("envelope_error_edit")
        ) { Text("Edit command") }
        UiErrorAction.Dismiss -> Unit
    }
}
