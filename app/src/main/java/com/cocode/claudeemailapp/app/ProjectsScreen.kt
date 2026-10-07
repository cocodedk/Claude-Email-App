package com.cocode.claudeemailapp.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.R
import com.cocode.claudeemailapp.data.ProjectSummary
import com.cocode.claudeemailapp.protocol.AgentStatusValues
import com.cocode.claudeemailapp.protocol.TaskStateValues
import java.time.Instant
import java.util.Date

@Composable
fun ProjectsScreen(
    state: AppViewModel.ProjectsState,
    onRefresh: () -> Unit,
    onProjectTap: (ProjectSummary) -> Unit,
    onCompose: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("projects_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { ProjectsHeader(state = state, onRefresh = onRefresh) }
        if (state.error != null) {
            item { StatusCard(title = stringResource(R.string.projects_load_failed), message = state.error) }
        }
        if (state.projects.isEmpty() && !state.loading && state.error == null) {
            item { ProjectsEmpty(onCompose = onCompose) }
        }
        items(state.projects, key = { it.path }) { project ->
            ProjectRow(project = project, onTap = { onProjectTap(project) })
        }
    }
}

@Composable
private fun ProjectsHeader(state: AppViewModel.ProjectsState, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = stringResource(R.string.title_projects),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            val sub = when {
                state.loading -> stringResource(R.string.projects_loading)
                state.projects.isEmpty() -> stringResource(R.string.projects_none)
                else -> pluralStringResource(R.plurals.projects_found, state.projects.size, state.projects.size)
            }
            Text(text = sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onRefresh, modifier = Modifier.testTag("projects_refresh")) { Text(stringResource(R.string.action_refresh)) }
        }
    }
}

@Composable
private fun ProjectsEmpty(onCompose: () -> Unit) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().testTag("projects_empty")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.projects_empty_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.projects_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onCompose, modifier = Modifier.testTag("projects_empty_compose")) {
                Text(stringResource(R.string.home_new_command))
            }
        }
    }
}

@Composable
private fun ProjectRow(project: ProjectSummary, onTap: () -> Unit) {
    ElevatedCard(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .testTag("project_row_${project.name}")
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = project.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                project.lastActivityAt?.let { ts ->
                    val formatted = formatTimestamp(parseIso(ts))
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ProjectStatePills(project)
        }
    }
}

@Composable
private fun ProjectStatePills(project: ProjectSummary) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        agentPill(project.agentStatus, colors)?.let { (label, accent) -> ChipPill(label, accent) }
        taskPill(project, colors)?.let { (label, accent) -> ChipPill(label, accent) }
    }
}

@Composable
private fun agentPill(status: String?, c: ColorScheme): Pair<String, Color>? = when (status) {
    AgentStatusValues.ONLINE, AgentStatusValues.CONNECTED -> stringResource(R.string.projects_agent_online) to c.tertiary
    AgentStatusValues.STALE -> stringResource(R.string.projects_agent_stale) to c.outline
    AgentStatusValues.OFFLINE, AgentStatusValues.DISCONNECTED, AgentStatusValues.ABSENT ->
        stringResource(R.string.projects_agent_offline) to c.outlineVariant
    null -> null
    else -> stringResource(R.string.projects_agent_other, status) to c.outline
}

@Composable
private fun taskPill(p: ProjectSummary, c: ColorScheme): Pair<String, Color>? {
    val taskRef = p.runningTaskId?.let { " · #$it" } ?: ""
    return when (p.taskState) {
        TaskStateValues.WORKING -> stringResource(R.string.projects_task_working) + taskRef to c.primary
        TaskStateValues.WAITING -> stringResource(R.string.projects_task_waiting) + taskRef to c.secondary
        TaskStateValues.COMPLETED -> stringResource(R.string.projects_task_completed) + taskRef to c.tertiary
        TaskStateValues.ERROR -> stringResource(R.string.projects_task_error) + taskRef to c.error
        null -> v1FallbackPill(p, c)
        else -> stringResource(R.string.projects_task_other, p.taskState) + taskRef to c.outline
    }
}

@Composable
private fun v1FallbackPill(p: ProjectSummary, c: ColorScheme): Pair<String, Color>? = when {
    p.runningTaskId != null -> stringResource(R.string.projects_task_running, p.runningTaskId) to c.primary
    p.queueDepth > 0 -> stringResource(R.string.projects_task_queued, p.queueDepth) to c.secondary
    else -> stringResource(R.string.projects_task_idle) to c.outline
}

private fun parseIso(iso: String): Date? = runCatching { Date.from(Instant.parse(iso)) }.getOrNull()
