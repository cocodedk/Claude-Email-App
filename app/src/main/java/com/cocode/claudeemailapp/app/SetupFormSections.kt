package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.R

internal fun LazyListScope.accountSection(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit
) {
    item { SetupSectionLabel(stringResource(R.string.setup_section_account)) }
    item {
        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            label = { Text(stringResource(R.string.setup_display_name)) },
            modifier = Modifier.fillMaxWidth().testTag("setup_display_name"),
            singleLine = true
        )
    }
    item {
        OutlinedTextField(
            value = email,
            onValueChange = { onEmailChange(it.trim()) },
            label = { Text(stringResource(R.string.setup_email_address)) },
            modifier = Modifier.fillMaxWidth().testTag("setup_email"),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
    }
    item {
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.setup_password)) },
            modifier = Modifier.fillMaxWidth().testTag("setup_password"),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
    }
}

internal fun LazyListScope.imapSection(
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit
) {
    item { SetupSectionLabel(stringResource(R.string.setup_section_imap)) }
    item {
        HostPortRow(
            host, onHostChange, "setup_imap_host", port, onPortChange, "setup_imap_port",
            stringResource(R.string.setup_imap_host)
        )
    }
}

internal fun LazyListScope.smtpSection(
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    startTls: Boolean,
    onStartTlsChange: (Boolean) -> Unit
) {
    item { SetupSectionLabel(stringResource(R.string.setup_section_smtp)) }
    item {
        HostPortRow(
            host, onHostChange, "setup_smtp_host", port, onPortChange, "setup_smtp_port",
            stringResource(R.string.setup_smtp_host)
        )
    }
    item { SmtpStartTlsToggle(startTls, onStartTlsChange) }
}

internal fun LazyListScope.serviceSection(
    address: String,
    onAddressChange: (String) -> Unit,
    sharedSecret: String,
    onSharedSecretChange: (String) -> Unit
) {
    item { SetupSectionLabel(stringResource(R.string.setup_section_service)) }
    item {
        OutlinedTextField(
            value = address,
            onValueChange = { onAddressChange(it.trim()) },
            label = { Text(stringResource(R.string.setup_service_address)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("setup_service_address"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
    }
    item {
        OutlinedTextField(
            value = sharedSecret,
            onValueChange = onSharedSecretChange,
            label = { Text(stringResource(R.string.setup_shared_secret)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().testTag("setup_shared_secret")
        )
    }
}

@Composable
private fun HostPortRow(
    host: String,
    onHostChange: (String) -> Unit,
    hostTestTag: String,
    port: String,
    onPortChange: (String) -> Unit,
    portTestTag: String,
    hostLabel: String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = host,
            onValueChange = { onHostChange(it.trim()) },
            label = { Text(hostLabel) },
            singleLine = true,
            modifier = Modifier.weight(2f).testTag(hostTestTag)
        )
        OutlinedTextField(
            value = port,
            onValueChange = { onPortChange(it.filter(Char::isDigit)) },
            label = { Text(stringResource(R.string.setup_port)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f).testTag(portTestTag)
        )
    }
}

@Composable
private fun SmtpStartTlsToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag("setup_smtp_starttls")
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(if (checked) R.string.setup_tls_starttls else R.string.setup_tls_implicit),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.setup_tls_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
