package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ComposeMessageScreen(
    defaultTo: String,
    defaultProject: String,
    sending: Boolean,
    sendError: String?,
    onCancel: () -> Unit,
    onSend: (to: String, project: String, body: String) -> Unit
) {
    var to by rememberSaveable { mutableStateOf(defaultTo) }
    var project by rememberSaveable { mutableStateOf(defaultProject) }
    var body by rememberSaveable { mutableStateOf("") }

    val canSend = to.isNotBlank() && project.isNotBlank() && body.isNotBlank() && !sending

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("compose_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.testTag("compose_cancel")) {
                    Text("Cancel")
                }
            }
        }
        item {
            OutlinedTextField(
                value = to,
                onValueChange = { to = it.trim() },
                label = { Text("claude-email service address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("compose_to"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
        }
        item {
            OutlinedTextField(
                value = project,
                onValueChange = { project = it.trim() },
                label = { Text("Project path") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("compose_project")
            )
        }
        item {
            TextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Command") },
                modifier = Modifier.fillMaxWidth().height(220.dp).testTag("compose_body"),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
        sendError?.let {
            item { ErrorCard(message = it) }
        }
        item {
            Button(
                onClick = rememberHapticClick { onSend(to, project, body) },
                enabled = canSend,
                modifier = Modifier.fillMaxWidth().testTag("compose_send")
            ) {
                Text(if (sending) "Sending…" else "Send")
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Send failed", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}
