package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.R
import com.cocode.claudeemailapp.data.ProjectSummary
import com.cocode.claudeemailapp.protocol.AgentStatusValues
import com.cocode.claudeemailapp.protocol.TaskStateValues

@Composable
internal fun ProjectStatePills(project: ProjectSummary) {
    val colors = MaterialTheme.colorScheme
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
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
    return when (p.taskState) {
        TaskStateValues.WORKING -> withTaskRef(stringResource(R.string.projects_task_working), p) to c.primary
        TaskStateValues.WAITING -> withTaskRef(stringResource(R.string.projects_task_waiting), p) to c.secondary
        TaskStateValues.COMPLETED -> withTaskRef(stringResource(R.string.projects_task_completed), p) to c.tertiary
        TaskStateValues.ERROR -> withTaskRef(stringResource(R.string.projects_task_error), p) to c.error
        null -> v1FallbackPill(p, c)
        else -> withTaskRef(stringResource(R.string.projects_task_other, p.taskState), p) to c.outline
    }
}

/** Adds the running task's number to a pill label when the project reports one. */
@Composable
private fun withTaskRef(label: String, p: ProjectSummary): String =
    p.runningTaskId?.let { stringResource(R.string.projects_task_with_ref, label, it) } ?: label

@Composable
private fun v1FallbackPill(p: ProjectSummary, c: ColorScheme): Pair<String, Color>? = when {
    p.runningTaskId != null -> stringResource(R.string.projects_task_running, p.runningTaskId) to c.primary
    p.queueDepth > 0 -> stringResource(R.string.projects_task_queued, p.queueDepth) to c.secondary
    else -> stringResource(R.string.projects_task_idle) to c.outline
}
