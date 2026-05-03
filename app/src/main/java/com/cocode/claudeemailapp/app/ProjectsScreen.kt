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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.data.ProjectSummary
import com.cocode.claudeemailapp.protocol.AgentStatusValues
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
            item { StatusCard(title = "Couldn't load projects", message = state.error) }
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
                text = "Projects",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            val sub = when {
                state.loading -> "Loading…"
                state.projects.isEmpty() -> "No projects"
                else -> "${state.projects.size} discoverable"
            }
            Text(text = sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onRefresh, modifier = Modifier.testTag("projects_refresh")) { Text("Refresh") }
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
            Text("No projects discovered yet.", style = MaterialTheme.typography.titleSmall)
            Text(
                "The list comes from the claude-email service. Send a command to get started, or pull Refresh once the backend exposes the project list.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onCompose, modifier = Modifier.testTag("projects_empty_compose")) {
                Text("New command")
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
                    val formatted = remember(ts) { formatTimestamp(parseIso(ts)) }
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ProjectStatePill(project)
        }
    }
}

@Composable
private fun ProjectStatePill(project: ProjectSummary) {
    val (label, accent) = when {
        project.agentStatus == AgentStatusValues.CONNECTED ->
            "agent connected" to MaterialTheme.colorScheme.tertiary
        project.runningTaskId != null ->
            "running task #${project.runningTaskId}" to MaterialTheme.colorScheme.primary
        project.queueDepth > 0 ->
            "queued ${project.queueDepth}" to MaterialTheme.colorScheme.secondary
        else ->
            "idle" to MaterialTheme.colorScheme.outline
    }
    ChipPill(label = label, accent = accent)
}

private fun parseIso(iso: String): Date? = runCatching { Date.from(Instant.parse(iso)) }.getOrNull()
