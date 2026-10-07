package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.R

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
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
        item {
            OutlinedTextField(
                value = to,
                onValueChange = { to = it.trim() },
                label = { Text(stringResource(R.string.compose_service_address)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("compose_to"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
        }
        item {
            OutlinedTextField(
                value = project,
                onValueChange = { project = it.trim() },
                label = { Text(stringResource(R.string.compose_project_path)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("compose_project")
            )
        }
        item {
            TextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringResource(R.string.compose_command)) },
                modifier = Modifier.fillMaxWidth().height(220.dp).testTag("compose_body"),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
        sendError?.let {
            item { StatusCard(title = stringResource(R.string.send_failed_title), message = it) }
        }
        item {
            Button(
                onClick = rememberHapticClick { onSend(to, project, body) },
                enabled = canSend,
                modifier = Modifier.fillMaxWidth().testTag("compose_send")
            ) {
                Text(stringResource(if (sending) R.string.action_sending else R.string.action_send))
            }
        }
    }
}
