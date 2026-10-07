package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.R

@Composable
internal fun EmptyBucketCard(
    filter: AppViewModel.HomeFilter,
    onCompose: () -> Unit = {}
) {
    val (heading, body) = when (filter) {
        AppViewModel.HomeFilter.ACTIVE ->
            stringResource(R.string.empty_active_title) to stringResource(R.string.empty_active_body)
        AppViewModel.HomeFilter.WAITING ->
            stringResource(R.string.empty_waiting_title) to stringResource(R.string.empty_waiting_body)
        AppViewModel.HomeFilter.ARCHIVED ->
            stringResource(R.string.empty_archived_title) to stringResource(R.string.empty_archived_body)
    }
    Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(heading, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (filter == AppViewModel.HomeFilter.ACTIVE) {
                Button(
                    onClick = onCompose,
                    modifier = Modifier.testTag("empty_active_send_cta")
                ) { Text(stringResource(R.string.empty_active_cta)) }
            }
        }
    }
}
