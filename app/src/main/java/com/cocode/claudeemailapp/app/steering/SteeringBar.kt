package com.cocode.claudeemailapp.app.steering

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.R
import com.cocode.claudeemailapp.ui.theme.BorderSoft
import com.cocode.claudeemailapp.ui.theme.GraphiteSoft

@Composable
fun SteeringBar(
    state: SteeringBarState,
    controller: SteeringBarController,
    modifier: Modifier = Modifier
) {
    if (state !is SteeringBarState.Idle) return
    val ui by controller.uiState.collectAsState()
    val chipsEnabled = ui.sending == null
    val consoleShape = RoundedCornerShape(18.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .background(GraphiteSoft, consoleShape)
            .border(1.dp, BorderSoft, consoleShape)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag("steering_bar"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val statusSending = ui.sending == SteeringIntent.Status
        SteeringChip(
            label = if (statusSending) {
                stringResource(R.string.steering_chip_sending)
            } else {
                stringResource(R.string.steering_chip_status)
            },
            onClick = { controller.tapStatus() },
            enabled = chipsEnabled,
            icon = if (statusSending) null else "\u25CE",
            variant = if (statusSending) SteeringChipVariant.Sending else SteeringChipVariant.Default,
            modifier = Modifier.testTag("steering_chip_status")
        )
        SteeringChip(
            label = if (ui.armed) {
                stringResource(R.string.steering_chip_cancel_confirm)
            } else {
                stringResource(R.string.steering_chip_cancel)
            },
            onClick = { controller.tapCancel() },
            enabled = chipsEnabled,
            icon = "\u25A0",
            variant = if (ui.armed) SteeringChipVariant.DangerArmed else SteeringChipVariant.Danger,
            modifier = Modifier.testTag("steering_chip_cancel")
        )
        SteeringChip(
            label = stringResource(R.string.steering_chip_more),
            onClick = { /* More sheet deferred to a follow-up plan. */ },
            enabled = chipsEnabled,
            icon = "\u22EF",
            variant = SteeringChipVariant.Ghost,
            modifier = Modifier.testTag("steering_chip_more")
        )
    }
}
