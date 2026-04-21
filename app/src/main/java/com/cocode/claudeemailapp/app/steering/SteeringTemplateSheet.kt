package com.cocode.claudeemailapp.app.steering

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.R

/**
 * Template sheet shown above the composer when the pending task is
 * awaiting a user reply. Tapping a template appends its text to the
 * composer; the user still presses Send. Routing the resulting reply
 * back to the right question (meta.ask_id) is the caller's job.
 */
@Composable
fun SteeringTemplateSheet(
    onTemplateTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val continueText = stringResource(R.string.template_continue)
    val abortText = stringResource(R.string.template_abort)
    val explainText = stringResource(R.string.template_explain)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("steering_template_sheet"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.steering_templates_heading),
            style = MaterialTheme.typography.labelMedium
        )
        OutlinedButton(
            onClick = { onTemplateTap(continueText) },
            modifier = Modifier.fillMaxWidth().testTag("steering_template_continue")
        ) { Text(continueText) }
        OutlinedButton(
            onClick = { onTemplateTap(abortText) },
            modifier = Modifier.fillMaxWidth().testTag("steering_template_abort")
        ) { Text(abortText) }
        OutlinedButton(
            onClick = { onTemplateTap(explainText) },
            modifier = Modifier.fillMaxWidth().testTag("steering_template_explain")
        ) { Text(explainText) }
    }
}
