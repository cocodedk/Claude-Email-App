package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.data.Conversation
import com.cocode.claudeemailapp.data.PendingCommand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: AppViewModel.InboxState,
    buckets: AppViewModel.HomeBuckets,
    pending: List<PendingCommand>,
    onRefresh: () -> Unit,
    onOpenConversation: (Conversation) -> Unit,
    onCompose: () -> Unit,
    onOpenSettings: () -> Unit,
    onArchiveToggle: (Conversation) -> Unit,
    onRetryPending: (PendingCommand) -> Unit = {},
    onCancelPending: (PendingCommand) -> Unit = {}
) {
    var filter by rememberSaveable { mutableStateOf(AppViewModel.HomeFilter.ACTIVE) }
    val visible = buckets[filter]
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("home_screen"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HeroCard(
                    loading = state.loading,
                    buckets = buckets,
                    onCompose = onCompose,
                    onRefresh = onRefresh,
                    onOpenSettings = onOpenSettings
                )
            }
            item { HomeFilterTabs(selected = filter, counts = buckets, onSelect = { filter = it }) }
            state.error?.let { item { HomeErrorCard(message = it) } }
            if (filter == AppViewModel.HomeFilter.ACTIVE && pending.isNotEmpty()) {
                item {
                    PendingSummary(
                        pending = pending,
                        onRetry = onRetryPending,
                        onCancel = onCancelPending
                    )
                }
            }
            if (visible.isEmpty() && state.loading && state.error == null) {
                items(count = 3, key = { "skeleton-$it" }) { SkeletonConversationCard() }
            }
            if (visible.isEmpty() && !state.loading && state.error == null) {
                item { EmptyBucketCard(filter = filter, onCompose = onCompose) }
            }
            items(visible, key = { "${filter.name}-${it.id}" }) { c ->
                SwipeableConversationCard(
                    conversation = c,
                    inArchivedView = filter == AppViewModel.HomeFilter.ARCHIVED,
                    onOpen = { onOpenConversation(c) },
                    onArchiveToggle = { onArchiveToggle(c) }
                )
            }
        }
    }
}
