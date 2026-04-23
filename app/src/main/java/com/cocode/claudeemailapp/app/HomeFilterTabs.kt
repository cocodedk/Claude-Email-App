package com.cocode.claudeemailapp.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeFilterTabs(
    selected: AppViewModel.HomeFilter,
    counts: AppViewModel.HomeBuckets,
    onSelect: (AppViewModel.HomeFilter) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().testTag("home_filter_tabs")) {
        FilterButton(0, AppViewModel.HomeFilter.ACTIVE, "Active", counts.active.size, selected, onSelect)
        FilterButton(1, AppViewModel.HomeFilter.WAITING, "Waiting", counts.waiting.size, selected, onSelect)
        FilterButton(2, AppViewModel.HomeFilter.ARCHIVED, "Archived", counts.archived.size, selected, onSelect)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun androidx.compose.material3.SingleChoiceSegmentedButtonRowScope.FilterButton(
    index: Int,
    filter: AppViewModel.HomeFilter,
    label: String,
    count: Int,
    selected: AppViewModel.HomeFilter,
    onSelect: (AppViewModel.HomeFilter) -> Unit
) {
    SegmentedButton(
        selected = selected == filter,
        onClick = { onSelect(filter) },
        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
        modifier = Modifier.testTag("home_filter_${filter.name.lowercase()}")
    ) {
        Text(
            text = if (count > 0) "$label · $count" else label,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
