package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.data.MailCredentials

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
        item { SetupHeaderCard() }
        accountSection(
            displayName = displayName, onDisplayNameChange = { displayName = it },
            email = email, onEmailChange = { email = it },
            password = password, onPasswordChange = { password = it }
        )
        imapSection(
            host = imapHost, onHostChange = { imapHost = it },
            port = imapPort, onPortChange = { imapPort = it }
        )
        smtpSection(
            host = smtpHost, onHostChange = { smtpHost = it },
            port = smtpPort, onPortChange = { smtpPort = it },
            startTls = smtpStartTls, onStartTlsChange = { smtpStartTls = it }
        )
        serviceSection(
            address = serviceAddress, onAddressChange = { serviceAddress = it },
            sharedSecret = sharedSecret, onSharedSecretChange = { sharedSecret = it }
        )
        item { ProbeStatusView(probe.result) }
        item {
            SubmitButton(
                running = probe.running,
                enabled = canSubmit,
                onSubmit = {
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
                }
            )
        }
    }
}

@Composable
private fun SubmitButton(running: Boolean, enabled: Boolean, onSubmit: () -> Unit) {
    Spacer(modifier = Modifier.height(4.dp))
    Button(
        onClick = onSubmit,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("setup_submit")
    ) {
        if (running) {
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
