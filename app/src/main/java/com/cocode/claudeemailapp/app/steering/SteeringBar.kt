package com.cocode.claudeemailapp.app.steering

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.R

@Composable
fun SteeringBar(
    state: SteeringBarState,
    controller: SteeringBarController,
    modifier: Modifier = Modifier
) {
    if (state !is SteeringBarState.Idle) return
    val ui by controller.uiState.collectAsState()
    val chipsEnabled = ui.sending == null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("steering_bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = { controller.tapStatus() },
            enabled = chipsEnabled,
            label = {
                val label = if (ui.sending == SteeringIntent.Status) {
                    stringResource(R.string.steering_chip_sending)
                } else {
                    stringResource(R.string.steering_chip_status)
                }
                Text(label)
            },
            modifier = Modifier.testTag("steering_chip_status")
        )
        AssistChip(
            onClick = { controller.tapCancel() },
            enabled = chipsEnabled,
            colors = AssistChipDefaults.assistChipColors(
                labelColor = if (ui.armed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            ),
            label = {
                val label = if (ui.armed) {
                    stringResource(R.string.steering_chip_cancel_confirm)
                } else {
                    stringResource(R.string.steering_chip_cancel)
                }
                Text(label)
            },
            modifier = Modifier.testTag("steering_chip_cancel")
        )
        AssistChip(
            onClick = { /* More sheet deferred to a follow-up plan. */ },
            enabled = chipsEnabled,
            label = { Text(stringResource(R.string.steering_chip_more)) },
            modifier = Modifier.testTag("steering_chip_more")
        )
    }
}
