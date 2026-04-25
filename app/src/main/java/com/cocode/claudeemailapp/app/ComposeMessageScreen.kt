package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
    onSend: (to: String, project: String, body: String) -> Unit,
    recentProjects: List<String> = emptyList()
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
        if (recentProjects.isNotEmpty()) {
            item { RecentProjectChips(recentProjects, onPick = { project = it }) }
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
            item { StatusCard(title = "Send failed", message = it) }
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
private fun RecentProjectChips(projects: List<String>, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Recent projects",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.testTag("compose_recent_projects")
        ) {
            projects.forEach { p ->
                AssistChip(
                    onClick = { onPick(p) },
                    label = { Text(p) },
                    colors = AssistChipDefaults.assistChipColors()
                )
            }
        }
    }
}

