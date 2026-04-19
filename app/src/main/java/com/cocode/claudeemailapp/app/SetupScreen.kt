package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.data.MailCredentials
import com.cocode.claudeemailapp.mail.ProbeResult

@Composable
fun SetupScreen(
    viewModel: AppViewModel,
    initial: MailCredentials? = null
) {
    val probe by viewModel.probe.collectAsState()

    var displayName by rememberSaveable { mutableStateOf(initial?.displayName.orEmpty()) }
    var email by rememberSaveable { mutableStateOf(initial?.emailAddress.orEmpty()) }
    var password by rememberSaveable { mutableStateOf(initial?.password.orEmpty()) }
    var imapHost by rememberSaveable { mutableStateOf(initial?.imapHost.orEmpty()) }
    var imapPort by rememberSaveable { mutableStateOf(initial?.imapPort?.toString() ?: "993") }
    var smtpHost by rememberSaveable { mutableStateOf(initial?.smtpHost.orEmpty()) }
    var smtpPort by rememberSaveable { mutableStateOf(initial?.smtpPort?.toString() ?: "465") }
    var smtpStartTls by rememberSaveable { mutableStateOf(initial?.smtpUseStartTls ?: false) }
    var serviceAddress by rememberSaveable { mutableStateOf(initial?.serviceAddress.orEmpty()) }
    var sharedSecret by rememberSaveable { mutableStateOf(initial?.sharedSecret.orEmpty()) }

    val canSubmit = email.isNotBlank() && password.isNotBlank() &&
        imapHost.isNotBlank() && smtpHost.isNotBlank() &&
        imapPort.toIntOrNull() != null && smtpPort.toIntOrNull() != null &&
        !probe.running

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .testTag("setup_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HeaderCard() }
        item { SectionLabel("Account") }
        item {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display name") },
                modifier = Modifier.fillMaxWidth().testTag("setup_display_name"),
                singleLine = true
            )
        }
        item {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim() },
                label = { Text("Email address") },
                modifier = Modifier.fillMaxWidth().testTag("setup_email"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
        }
        item {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password or app password") },
                modifier = Modifier.fillMaxWidth().testTag("setup_password"),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
        }
        item { SectionLabel("IMAP (receive)") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = imapHost,
                    onValueChange = { imapHost = it.trim() },
                    label = { Text("IMAP host") },
                    singleLine = true,
                    modifier = Modifier.weight(2f).testTag("setup_imap_host")
                )
                OutlinedTextField(
                    value = imapPort,
                    onValueChange = { imapPort = it.filter(Char::isDigit) },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).testTag("setup_imap_port")
                )
            }
        }
        item { SectionLabel("SMTP (send)") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = smtpHost,
                    onValueChange = { smtpHost = it.trim() },
                    label = { Text("SMTP host") },
                    singleLine = true,
                    modifier = Modifier.weight(2f).testTag("setup_smtp_host")
                )
                OutlinedTextField(
                    value = smtpPort,
                    onValueChange = { smtpPort = it.filter(Char::isDigit) },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).testTag("setup_smtp_port")
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(
                    checked = smtpStartTls,
                    onCheckedChange = { smtpStartTls = it },
                    modifier = Modifier.testTag("setup_smtp_starttls")
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (smtpStartTls) "STARTTLS (typical port 587)" else "Implicit TLS (typical port 465)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Toggle if your SMTP server uses STARTTLS instead of implicit TLS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item { SectionLabel("claude-email service") }
        item {
            OutlinedTextField(
                value = serviceAddress,
                onValueChange = { serviceAddress = it.trim() },
                label = { Text("Service address (email)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("setup_service_address"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
        }
        item {
            OutlinedTextField(
                value = sharedSecret,
                onValueChange = { sharedSecret = it },
                label = { Text("Shared secret") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("setup_shared_secret")
            )
        }
        item { ProbeStatusView(probe.result) }
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    viewModel.probeAndSave(
                        MailCredentials(
                            displayName = displayName,
                            emailAddress = email,
                            password = password,
                            imapHost = imapHost,
                            imapPort = imapPort.toInt(),
                            smtpHost = smtpHost,
                            smtpPort = smtpPort.toInt(),
                            smtpUseStartTls = smtpStartTls,
                            serviceAddress = serviceAddress,
                            sharedSecret = sharedSecret
                        )
                    )
                },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_submit")
            ) {
                if (probe.running) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Testing connection…")
                } else {
                    Text("Test and save")
                }
            }
        }
    }
}

@Composable
private fun HeaderCard() {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Single mailbox", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "This app connects to one mailbox only. Receive over IMAP, send over SMTP. " +
                    "Credentials stay on this device, encrypted with the Android Keystore.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun ProbeStatusView(result: ProbeResult?) {
    if (result == null) return
    when (result) {
        is ProbeResult.Success -> {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "Connected. IMAP and SMTP verified.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
        }
        is ProbeResult.Failure -> {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${result.stage.name} failed",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = result.message.ifBlank { "Unknown error" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
