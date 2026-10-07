package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cocode.claudeemailapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTopBar(screen: Screen, editingCredentials: Boolean) {
    TopAppBar(
        title = {
            Text(
                text = when (screen) {
                    Screen.Onboarding -> ""
                    Screen.Setup -> stringResource(
                        if (editingCredentials) R.string.title_edit_credentials else R.string.title_setup
                    )
                    Screen.Home -> stringResource(R.string.title_home)
                    Screen.Settings -> stringResource(R.string.title_settings)
                    Screen.Conversation -> stringResource(R.string.title_conversation)
                    Screen.Compose -> stringResource(R.string.title_compose)
                    Screen.Diagnostics -> stringResource(R.string.title_diagnostics)
                    Screen.Projects -> stringResource(R.string.title_projects)
                    Screen.About -> stringResource(R.string.title_about)
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        windowInsets = WindowInsets.statusBars
    )
}
